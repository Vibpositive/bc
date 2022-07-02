package com.openet.labs.monitoring;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

public class WaitDefault extends ClientFactory implements MonitorResource{

    CoreV1Api api;
    V1PodList v1PodList;
    String namespace;

    private static final Logger logger = LoggerFactory.getLogger(WaitDefault.class);
    public WaitDefault() {
    }

    public Job job = () -> {
            try {
                v1PodList = api.listNamespacedPod(namespace,null,null, null, null, "app=monitoring-logstash", null, null, null, null, null);
                v1PodList.getItems()
                        .forEach(v1Pod -> {
//                            logger.info("Pod: {} startTime {} lalal {}", Objects.requireNonNull(v1Pod.getMetadata()).getName(), Objects.requireNonNull(v1Pod.getStatus()).getStartTime(), v1Pod.getStatus().getMessage());
                            logger.info("Pod: {} startTime {} lalal {}", Objects.requireNonNull(v1Pod.getMetadata()).getName(), Objects.requireNonNull(v1Pod.getStatus()).getStartTime(), Objects.requireNonNull(v1Pod.getStatus().getContainerStatuses()).get(0).getReady());
                        });
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
    };

    public Job getJob() {
        return job;
    }

    @Override
    public MonitorResource Init(String namespace) {
        logger.info("WaitDefault.init()");

        ApiClient client;
        try {
            client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);

            api = new CoreV1Api();
            this.namespace = namespace;
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
