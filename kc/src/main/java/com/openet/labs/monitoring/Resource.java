package com.openet.labs.monitoring;

public interface Resource {
    Resource Init(String namespace, String resourceName, int executorIntervalInt, int completionsQuantity);

    Job getJob();
}
