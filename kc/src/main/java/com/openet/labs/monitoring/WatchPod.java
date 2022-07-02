package com.openet.labs.monitoring;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class WatchPod {
    private static final Logger logger = LoggerFactory.getLogger(WatchPod.class);
    public static void main(String[] args) throws IOException, ApiException {
        ApiClient client = Config.defaultClient();

        AtomicInteger v1PodHashMapSize = new AtomicInteger();

        HashMap<String, V1Pod> v1PodToBeDeletedHashMap = new HashMap<>();
        HashMap<String, V1Pod> v1PodToBeRecreatedHashMap = new HashMap<>();
        HashMap<String, V1Pod> v1PodToBeRestoredHashMap = new HashMap<>();

        // infinite timeout
        OkHttpClient httpClient =
                client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
        client.setHttpClient(httpClient);
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();
        AppsV1Api appsV1Api = new AppsV1Api();
        V1StatefulSetList v1StatefulSetList;
        v1StatefulSetList = appsV1Api.listNamespacedStatefulSet(
                "monitoring",
                null,
                null,
                null,
                null,
                null,
                100,
                null,
                null,
                null,
                null) ;

        String resourceName = "logstash";
        List<V1StatefulSet> filteredList = v1StatefulSetList.getItems()
                .stream()
                .filter(v1StatefulSet -> Objects.equals(
                        Objects.requireNonNull(v1StatefulSet.getMetadata()).getName().toLowerCase(Locale.ROOT),
                        resourceName.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        filteredList.forEach(v1StatefulSet -> {
            logger.info(
                    "StatefulSet.status.availableReplicas: {} StatefulSet.status.currentReplicas: {} StatefulSet.status.readyReplicas: {} StatefulSet.status.replicas {}: ",
                    v1StatefulSet.getStatus().getAvailableReplicas(),
                    v1StatefulSet.getStatus().getCurrentReplicas(),
                    v1StatefulSet.getStatus().getReadyReplicas(),
                    v1StatefulSet.getStatus().getReplicas()
            );
            v1PodHashMapSize.set(v1StatefulSet.getStatus().getAvailableReplicas());
        });

        while (v1PodHashMapSize.get() > 0){

            V1PodList v1PodList = api.listNamespacedPod("monitoring",
                    null,
                    null,
                    null,
                    null,
//                    Todo
                    "app=monitoring-logstash",
                    null,
                    null,
                    null,
                    null,
                    null);

            logger.info("Pod count: {}", v1PodList.getItems().size());
            v1PodList.getItems()
                .forEach(v1Pod -> {
                    logger.info(
                            "Pod: {} startTime: {} Ready: {} Phase: {} ",
                            v1Pod.getMetadata().getName(),
                            v1Pod.getStatus().getStartTime(),
                            v1Pod.getStatus().getContainerStatuses().get(0).getReady(),
                            v1Pod.getStatus().getPhase()
                            );
//                    TODO what if pod status is container creating already
                    v1PodToBeDeletedHashMap.put(v1Pod.getMetadata().getName(), v1Pod);
                    v1PodHashMapSize.decrementAndGet();
                });
        }
        Watch<V1Pod> watch;
        try {
            watch = Watch.createWatch(
                    client,
                    api.listNamespacedPodCall("monitoring", null, null, null, null, null, 100, null, null, null, Boolean.TRUE, null),
                new TypeToken<Watch.Response<V1Pod>>() {}.getType());
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        try {
            for (Watch.Response<V1Pod> item : watch) {
                String currentV1PodName = item.object.getMetadata().getName();
                if(item.type.equals("DELETED") && v1PodToBeDeletedHashMap.size() > 0){
                    v1PodToBeRecreatedHashMap.put(currentV1PodName, v1PodToBeDeletedHashMap.remove(currentV1PodName));
                    logger.info("{} {}", currentV1PodName, item.type);
                }
                if(item.type.equals("ADDED") && v1PodToBeRecreatedHashMap.size() > 0){
                    v1PodToBeRestoredHashMap.put(currentV1PodName, v1PodToBeRecreatedHashMap.remove(currentV1PodName));
                    logger.info("{} {}", currentV1PodName, item.type);
                }
                if(item.type.equals("MODIFIED") && item.object.getStatus().getPhase().equals("Running") &&
                        v1PodToBeRestoredHashMap.size() > 0 && !(v1PodToBeRestoredHashMap.get(currentV1PodName) == null)
                ){
                    v1PodToBeRestoredHashMap.remove(currentV1PodName);
                    logger.info("{} {}", currentV1PodName, "RESTORED");
                }
                if( v1PodToBeDeletedHashMap.size() == 0 && v1PodToBeRecreatedHashMap.size() == 0 &&
                        v1PodToBeRestoredHashMap.size() == 0 ){
                    System.exit(0);
                }
            }
        }catch (Exception e){
            logger.error(String.valueOf(e));
            throw new RuntimeException(e);
        } finally {
            watch.close();
        }
    }
}