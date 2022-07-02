package com.openet.labs.monitoring;

public enum ClientType {
    ROLLING_UPDATE("rollingUpdate"),
    PARTIAL_ROLLING_UPDATR("partialRollingUpdate"),
    WAIT_FULL_UPGRADE("wait_full_upgrade");
    public final String label;

    private ClientType(String label) {
        this.label = label;
    }
}
