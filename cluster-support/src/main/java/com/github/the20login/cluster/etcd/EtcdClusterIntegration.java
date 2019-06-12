package com.github.the20login.cluster.etcd;

import com.github.the20login.cluster.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchResponse;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import io.reactivex.subjects.UnicastSubject;
import lombok.extern.slf4j.Slf4j;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class EtcdClusterIntegration implements ClusterIntegration, AutoCloseable {
    public static final String SEPARATOR = "/";
    private final UUID currentNodeId = UUID.randomUUID();
    private final NodeType selfType;
    private final Client client;
    private final String rootPath;
    private final ByteSequence rootPathByteSequence;
    private final EtcdClusterIntegrationConfiguration config;
    private final ScheduledExecutorService executor;
    private final ByteSequence selfKey;

    private volatile Registration registration;
    private volatile Watch.Watcher watcher;

    private volatile ClusterState state = new ClusterState(Health.DOWN, HashTreePMap.empty(), null);

    private final BehaviorSubject<ClusterStateUpdate> subject = BehaviorSubject.create();

    public EtcdClusterIntegration(EtcdClusterIntegrationConfiguration config, NodeType selfType, Client client) {
        this.client = client;
        this.rootPath = config.getServicesPath().endsWith("/") ? config.getServicesPath() : config.getServicesPath() + "/";
        rootPathByteSequence = ByteSequence.from(rootPath, UTF_8);
        this.selfType = selfType;
        this.config = config;
        String selfKeyString = rootPath + selfType + "/" + currentNodeId.toString();
        log.info("This node key is {}", selfKeyString);
        selfKey = ByteSequence.from(selfKeyString, UTF_8);

        executor = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat(this.getClass().getSimpleName() + "-scheduler-%d").build());

        watch();

        register();
    }

    private void register() {
        executor.execute(() -> {
            Instant registrationStarted = Instant.now();
            Runnable rescheduleRegistration = () -> {
                Duration delay = config.getReconnectInterval().minus(Duration.between(registrationStarted, Instant.now()));
                if (delay.isNegative()) {
                    delay = Duration.ZERO;
                }
                log.info("Reschedule registration, next attempt in {}", delay);
                executor.schedule(this::register, delay.toMillis(), TimeUnit.MILLISECONDS);
            };
            tryToRegister()
                    .thenAccept(registration -> {
                        this.registration = registration;
                        registration.keepAliveSubject
                                .subscribe(x -> {
                                }, e -> log.error("Registration error", e), () -> {
                                    log.info("Registration cancelled");
                                    this.registration = null;
                                    if (!executor.isShutdown()) {
                                        rescheduleRegistration.run();
                                    }
                                });
                    })
                    .exceptionally(throwable -> {
                        log.error("Unable to register", throwable);
                        if (!executor.isShutdown()) {
                            rescheduleRegistration.run();
                        }
                        return null;
                    });
        });
    }

    private CompletableFuture<Registration> tryToRegister() {
        log.debug("Try to grant lease");
        Lease leaseClient = client.getLeaseClient();
        long ttl = config.getLeaseTtlSeconds();
        return leaseClient.grant(ttl)
                .thenApply(LeaseGrantResponse::getID)
                .thenApply(leaseId -> {
                    log.debug("lease granted, lease id: {}", leaseId);
                    PublishSubject<LeaseKeepAliveResponse> keepAliveSubject = PublishSubject.create();
                    CloseableClient keepAlive = leaseClient.keepAlive(leaseId, ObserverHelper.fromSubject(keepAliveSubject));

                    return new Registration(leaseId, keepAlive, keepAliveSubject);
                })
                .thenCompose(registration -> {
                    ByteSequence value = ByteSequence.from(config.getAdvertisedAddress(), UTF_8);
                    return client.getKVClient().put(selfKey, value, PutOption.newBuilder().withLeaseId(registration.leaseId).build())
                            .thenApply(putResponse -> {
                                log.info("Successfully registered");
                                return registration;
                            });
                });
    }

    private void watch() {
        executor.execute(() -> {
            Instant watchStarted = Instant.now();
            Runnable rescheduleWatch = () -> {
                Duration delay = config.getReconnectInterval().minus(Duration.between(watchStarted, Instant.now()));
                if (delay.isNegative()) {
                    delay = Duration.ZERO;
                }
                log.info("Reschedule watch, next attempt in {}", delay);
                executor.schedule(this::watch, delay.toMillis(), TimeUnit.MILLISECONDS);
            };
            tryToWatch()
                    .thenAccept(watcher -> {
                        this.watcher = watcher;
                        //TODO: handle late reconnect
                    })
                    .exceptionally(throwable -> {
                        log.error("Unable to watch", throwable);
                        if (!executor.isShutdown()) {
                            rescheduleWatch.run();
                        }
                        return null;
                    });
        });
    }

    private CompletableFuture<List<Update>> loadInitialState(long revision, List<Update> partialData) {
        return client.getKVClient().get(rootPathByteSequence, GetOption.newBuilder().withPrefix(rootPathByteSequence).withRevision(revision).build())
                .thenCompose(response -> {
                    partialData.add(parseInitialValues(response));
                    if (response.isMore()) {
                        log.debug("Partial initial data loaded, load rest");
                        return loadInitialState(response.getHeader().getRevision(), partialData);
                    } else {
                        return CompletableFuture.completedFuture(partialData);
                    }
                });
    }

    private CompletableFuture<Watch.Watcher> tryToWatch() {
        return loadInitialState(0, new ArrayList<>())
                .thenApply(this::mergeInitialValues)
                .thenApply(initial -> {
                    Subject<WatchResponse> updates = UnicastSubject.create();
                    Watch.Watcher watcher = client.getWatchClient()
                            .watch(rootPathByteSequence, WatchOption.newBuilder().withPrefix(rootPathByteSequence).withRevision(initial.revision).build(), ObserverHelper.listenerFromSubject(updates));

                    Observable.just(initial)
                            .concatWith(updates.map(this::parseUpdatesValues))
                            .subscribe(this::onChange, e -> log.error("Watch error", e));
                    return watcher;
                });
    }

    private Update mergeInitialValues(List<Update> updates) {
        long revision = updates.stream().map(update -> update.revision).findAny().get();
        Map<NodeType, Map<UUID, NodeInfo>> updatedNodes = updates.stream()
                .flatMap(update -> update.updatedNodes.values().stream())
                .flatMap(nodes -> nodes.values().stream())
                .reduce(new HashMap<>(),
                        (nodesByType, nodeInfo) -> {
                            nodesByType.computeIfAbsent(nodeInfo.getType(), nodeType -> new HashMap<>()).put(nodeInfo.getId(), nodeInfo);
                            return nodesByType;
                        },
                        (nodesByType, nodesByType2) -> {
                            nodesByType2.forEach((nodeType, typeNodes) -> {
                                nodesByType.computeIfAbsent(nodeType, t -> new HashMap<>()).putAll(typeNodes);
                            });
                            return nodesByType;
                        });
        return new Update(revision, updatedNodes, Collections.emptyMap());
    }

    private Update parseInitialValues(GetResponse response) {
        log.info("Initial values loaded, parse it");
        int prefixSize = rootPathByteSequence.getBytes().length;
        Map<NodeType, Map<UUID, NodeInfo>> nodes;
        if (response.getKvs() == null) {
            nodes = Collections.emptyMap();
        } else {
            nodes = response.getKvs().stream()
                    //.filter(keyValue -> !keyValue.getKey().equals(rootKey))
                    .map(keyValue -> parseNodeInfo(keyValue, prefixSize))
                    .reduce(new HashMap<>(),
                            (nodesByType, nodeInfo) -> {
                                nodesByType.computeIfAbsent(nodeInfo.getType(), nodeType -> new HashMap<>()).put(nodeInfo.getId(), nodeInfo);
                                return nodesByType;
                            },
                            (nodesByType, nodesByType2) -> {
                                nodesByType2.forEach((nodeType, typeNodes) -> {
                                    nodesByType.computeIfAbsent(nodeType, t -> new HashMap<>()).putAll(typeNodes);
                                });
                                return nodesByType;
                            });
        }
        log.info("Initial values parsed, {} nodes", nodes.size());
        return new Update(response.getHeader().getRevision(), nodes);
    }

    private Update parseUpdatesValues(WatchResponse response) {
        log.info("Update received, parse it");
        int prefixSize = rootPathByteSequence.getBytes().length;
        Map<NodeType, Map<UUID, NodeInfo>> updated = new HashMap<>();
        Map<NodeType, Set<UUID>> removed = new HashMap<>();
        response.getEvents().stream()
                //.filter(watchEvent -> !watchEvent.getKeyValue().getKey().equals(rootPathByteSequence))
                .forEach(watchEvent -> {
                    switch (watchEvent.getEventType()) {
                        case PUT: {
                            NodeInfo node = parseNodeInfo(watchEvent.getKeyValue(), prefixSize);
                            updated.computeIfAbsent(node.getType(), nodeType -> new HashMap<>())
                                    .put(node.getId(), node);
                            break;
                        }
                        case DELETE: {
                            NodeKey nodeKey = parseNodeId(watchEvent.getKeyValue().getKey(), prefixSize);
                            removed.computeIfAbsent(nodeKey.nodeType, nodeType -> new HashSet<>()).add(nodeKey.nodeId);
                            break;
                        }
                    }
                });

        log.info("Update parsed, {} nodes updates, {} nodes removed", updated.size(), removed.size());
        return new Update(response.getHeader().getRevision(), updated, removed);
    }

    private NodeInfo parseNodeInfo(KeyValue keyValue, int prefixSize) {
        NodeKey parsedKey = parseNodeId(keyValue.getKey(), prefixSize);
        String socketAddress;
        if (keyValue.getValue() != null) {
            socketAddress = new String(keyValue.getValue().getBytes(), UTF_8);
        } else {
            socketAddress = null;
        }
        return new NodeInfo(parsedKey.nodeId, parsedKey.nodeType, socketAddress);
    }

    private NodeKey parseNodeId(ByteSequence key, int prefixSize) {
        byte[] keyBytes = key.getBytes();
        String[] typeAndKey = new String(keyBytes, prefixSize, keyBytes.length - prefixSize, UTF_8).split(SEPARATOR);
        NodeType nodeType = NodeType.valueOf(typeAndKey[0]);
        UUID nodeId = UUID.fromString(typeAndKey[1]);
        return new NodeKey(nodeId, nodeType);
    }

    private void onChange(Update update) {
        ClusterState oldState = state;
        PMap<NodeType, PMap<UUID, NodeInfo>> nodes = oldState.getNodes();
        for (Map.Entry<NodeType, Map<UUID, NodeInfo>> e : update.updatedNodes.entrySet()) {
            NodeType nodeType = e.getKey();
            Map<UUID, NodeInfo> updatedNodes = e.getValue();

            PMap<UUID, NodeInfo> typedNodes = nodes.getOrDefault(nodeType, HashTreePMap.empty());
            for (Map.Entry<UUID, NodeInfo> entry : updatedNodes.entrySet()) {
                UUID uuid = entry.getKey();
                NodeInfo nodeInfo = entry.getValue();
                typedNodes = typedNodes.plus(uuid, nodeInfo);
            }
            nodes = nodes.plus(nodeType, typedNodes);
        }

        for (Map.Entry<NodeType, Set<UUID>> e : update.removedNodes.entrySet()) {
            NodeType nodeType = e.getKey();
            Set<UUID> removedNodes = e.getValue();

            PMap<UUID, NodeInfo> typedNodes = nodes.getOrDefault(nodeType, HashTreePMap.empty());
            for (UUID uuid : removedNodes) {
                typedNodes = typedNodes.minus(uuid);
            }
            if (typedNodes.isEmpty()) {
                nodes = nodes.minus(nodeType);
            } else {
                nodes = nodes.plus(nodeType, typedNodes);
            }
        }
        NodeInfo currentNode = nodes.getOrDefault(selfType, HashTreePMap.empty()).get(currentNodeId);
        log.debug("Update applied, nodes {}", nodes);
        state = new ClusterState(Health.UP, nodes, currentNode);
        subject.onNext(new ClusterStateUpdate(state, update.updatedNodes, update.removedNodes));
    }

    @Override
    public void close() {
        executor.shutdownNow();
        Registration registration = this.registration;
        if (registration != null) {
            registration.keepAlive.close();
        }
        Watch.Watcher watcher = this.watcher;
        if (watcher != null) {
            watcher.close();
        }
        subject.onComplete();
    }

    @Override
    public Observable<ClusterStateUpdate> getSubject() {
        return subject;
    }

    private static class NodeKey {
        private final UUID nodeId;
        private final NodeType nodeType;

        private NodeKey(UUID nodeId, NodeType nodeType) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
        }
    }

    private static class Update {
        final Map<NodeType, Map<UUID, NodeInfo>> updatedNodes;
        final Map<NodeType, Set<UUID>> removedNodes;
        final long revision;

        public Update(long revision, Map<NodeType, Map<UUID, NodeInfo>> updatedNodes) {
            this.revision = revision;
            this.updatedNodes = updatedNodes;
            removedNodes = Collections.emptyMap();
        }

        public Update(long revision, Map<NodeType, Map<UUID, NodeInfo>> updatedNodes, Map<NodeType, Set<UUID>> removedNodes) {
            this.updatedNodes = updatedNodes;
            this.removedNodes = removedNodes;
            this.revision = revision;
        }
    }

    private static class Registration {
        private final long leaseId;
        private final CloseableClient keepAlive;
        private final PublishSubject<LeaseKeepAliveResponse> keepAliveSubject;

        private Registration(long leaseId, CloseableClient keepAlive, PublishSubject<LeaseKeepAliveResponse> keepAliveSubject) {
            this.leaseId = leaseId;
            this.keepAlive = keepAlive;
            this.keepAliveSubject = keepAliveSubject;
        }
    }
}
