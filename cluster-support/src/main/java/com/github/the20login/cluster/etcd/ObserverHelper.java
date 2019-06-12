package com.github.the20login.cluster.etcd;

import io.etcd.jetcd.Watch;
import io.etcd.jetcd.watch.WatchResponse;
import io.grpc.stub.StreamObserver;
import io.reactivex.subjects.Subject;

public abstract class ObserverHelper {
    private ObserverHelper() {
    }

    public static <V> StreamObserver<V> fromSubject(Subject<V> subject) {
        return new StreamObserver<V>() {
            @Override
            public void onNext(V value) {
                subject.onNext(value);
            }

            @Override
            public void onError(Throwable t) {
                subject.onError(t);
            }

            @Override
            public void onCompleted() {
                subject.onComplete();
            }
        };
    }

    public static Watch.Listener listenerFromSubject(Subject<WatchResponse> subject) {
        return new Watch.Listener() {
            @Override
            public void onNext(WatchResponse value) {
                subject.onNext(value);
            }

            @Override
            public void onError(Throwable t) {
                subject.onError(t);
            }

            @Override
            public void onCompleted() {
                subject.onComplete();
            }
        };
    }
}
