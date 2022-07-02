package com.openet.labs.monitoring;

public enum ClientType {
    WAIT("wait"),
    WAIT_PARTIAL_UPGRADE("wait_partial_upgrade"),
    WAIT_FULL_UPGRADE("wait_full_upgrade");
    public final String label;

    private ClientType(String label) {
        this.label = label;
    }
}
