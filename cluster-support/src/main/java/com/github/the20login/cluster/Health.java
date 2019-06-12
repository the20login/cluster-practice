package com.github.the20login.cluster;

public enum Health {
    UP,
    DOWN;

    public boolean isUp() {
        return this.equals(UP);
    }
}
