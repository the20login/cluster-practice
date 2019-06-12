package com.github.the20login.cluster;

import io.reactivex.Observable;

public interface ClusterIntegration {
    Observable<ClusterStateUpdate> getSubject();
}
