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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class WaitPartialRollingUpdate extends ClientFactory implements Resource{

    CoreV1Api api;
    AppsV1Api appsV1Api;

    V1StatefulSetList v1StatefulSetList = new V1StatefulSetList();
    String namespace;
    String resourceName;
    AtomicInteger v1PodStackSize = new AtomicInteger();
    HashMap<String, V1Pod> v1DeletedPods = new HashMap<>();
    HashMap<String, V1Pod> v1PodsToBeRecreated = new HashMap<>();
    HashMap<String, V1Pod> v1PodsToBeRestored = new HashMap<>();
    Watch<V1Pod> watch;
    ApiClient client;
    OkHttpClient httpClient;

    int sleepTimeout;

    private static final Logger logger = LoggerFactory.getLogger(WaitPartialRollingUpdate.class);
    private int completionsQuantity;
    private int currentCompletions;

    public Job job = () -> {
        try {
            getStatefulSetInfo();
            logPodList();
            run();
        } catch (ApiException | InterruptedException | IOException e){
            throw new RuntimeException(e);
        }
        logger.info("Sleeping for {}ms", sleepTimeout);
    };

    private void logPodList() throws ApiException {
        while (v1PodStackSize.get() > 0){

            V1PodList v1PodList = api.listNamespacedPod(namespace,
                    null,
                    null,
                    null,
                    null,
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
//                        v1DeletedPod.put(v1Pod.getMetadata().getName(), v1Pod);
//                        v1PodsToBeRestored.put(v1Pod.getMetadata().getName(), v1Pod);
//                        v1PodsToBeRecreated.put(v1Pod.getMetadata().getName(), v1Pod);
                        v1PodStackSize.decrementAndGet();
                    });
        }
    }

    private void run() throws IOException, ApiException {
        String resourceVersion = null;
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

                logger.info("xxxx {} yyyy {}", currentV1PodName, item.object.getStatus().getPhase());

                if(item.type.equals("DELETED") && !v1DeletedPods.containsKey(currentV1PodName)){
                    v1DeletedPods.put(currentV1PodName, item.object);
                    logger.info("{} {}", currentV1PodName, item.type);
                }
                if( item.type.equals("ADDED") && !v1PodsToBeRecreated.containsKey(currentV1PodName)&&
                    v1DeletedPods.containsKey(currentV1PodName)){

                    v1PodsToBeRecreated.put(currentV1PodName, item.object);
                    logger.info("{} {}", currentV1PodName, item.type);
                }
                if(
                    item.type.equals("MODIFIED") && item.object.getStatus().getPhase().equals("Running") &&
                    !v1PodsToBeRestored.containsKey(currentV1PodName) &&
                    v1DeletedPods.containsKey(currentV1PodName)
                ){

                    v1PodsToBeRestored.put(currentV1PodName, item.object);
                    logger.info("{} {}", currentV1PodName, "RESTORED");
                }
                if (v1DeletedPods.size() == v1PodsToBeRecreated.size() && v1DeletedPods.size() == v1PodsToBeRestored.size() &&
                        v1DeletedPods.size() > 0 &&
                        v1DeletedPods.containsKey(currentV1PodName) &&
                        v1PodsToBeRecreated.containsKey(currentV1PodName) &&
                        v1PodsToBeRestored.containsKey(currentV1PodName)
                ){
                    currentCompletions++;
                    logger.debug("CurrentCompletions {}", currentCompletions);
                }

                if(currentCompletions == completionsQuantity){
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

    private void getStatefulSetInfo() throws ApiException, InterruptedException {
        logger.debug("getStatefulSetInfo");
        while (v1StatefulSetList.getItems().size() == 0) {
            v1StatefulSetList = appsV1Api.listNamespacedStatefulSet(
                    namespace,
                    "true",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

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
                v1PodStackSize.set(v1StatefulSet.getStatus().getAvailableReplicas());
            });
            Thread.sleep(sleepTimeout);
        }
    }

    public Job getJob() {
        return job;
    }

    @Override
    public Resource Init(String namespace, String resourceName, int sleepTimeout, int completionsQuantity) {
        logger.info("WaitPartialRollingUpdate.init()");
        try {
            setupClient();
            setupApis();

            this.namespace = namespace;
            this.resourceName = resourceName;
            this.sleepTimeout = sleepTimeout;
            this.completionsQuantity = completionsQuantity;
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupApis() {
        api = new CoreV1Api();
        appsV1Api = new AppsV1Api(client);
    }

    private void setupClient() throws IOException {
        client = Config.defaultClient();
        httpClient = client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
        client.setHttpClient(httpClient);
        Configuration.setDefaultApiClient(client);
    }

}
