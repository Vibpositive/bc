package com.openet.labs.monitoring;

public interface MonitorResource {
    Job getJob();
    MonitorResource Init(String namespace);
}
