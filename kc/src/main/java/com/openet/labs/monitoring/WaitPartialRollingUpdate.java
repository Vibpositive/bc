package com.openet.labs.monitoring;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class WaitPartialRollingUpdate extends ClientFactory implements Resource{

    CoreV1Api api;
    AppsV1Api appsV1Api;

    V1StatefulSetList v1StatefulSetList = new V1StatefulSetList();
    String namespace;
    String resourceName;
    AtomicInteger v1PodListSize = new AtomicInteger();
    Watch<V1Pod> watch;
    ApiClient client;
    OkHttpClient httpClient;

    int sleepTimeout;

    private static final Logger logger = LoggerFactory.getLogger(WaitPartialRollingUpdate.class);
    private int completionsQuantity;
    private int currentCompletions;

    public Job job = () -> {
        try {
            setStatefulSetInfo();
//            logPodList();
            run();
        } catch (ApiException | InterruptedException | IOException e){
            throw new RuntimeException(e);
        }
        logger.info("Sleeping for {}ms", sleepTimeout);
    };

//    private void logPodList() throws ApiException {
//        while (v1PodListSize.get() > 0){
//
//            V1PodList v1PodList = api.listNamespacedPod(namespace,
//                    null,
//                    null,
//                    null,
//                    null,
//                    "app=monitoring-logstash",
//                    null,
//                    null,
//                    null,
//                    null,
//                    null);
//
//            logger.info("Pod count: {}", v1PodList.getItems().size());
//            v1PodList.getItems()
//                    .forEach(v1Pod -> {
//                        Objects.requireNonNull(v1Pod.getMetadata());
//                        Objects.requireNonNull(v1Pod.getStatus());
//                        logger.debug(
//                                "Pod: {} startTime: {} Phase: {} ",
//                                v1Pod.getMetadata().getName(),
//                                v1Pod.getStatus().getStartTime(),
//                                v1Pod.getStatus().getPhase()
//                        );
//                        v1PodListSize.decrementAndGet();
//                    });
//        }
//    }
//
    private void run() throws IOException, ApiException {
//        while (true) {
            try (Watch<V1Pod> watch = Watch.createWatch(
                    client,
                    api.listNamespacedPodCall(
                            namespace,
                            null,
                            null,
                            null,
                            null,
                            null,
                            100,
                            null,
                            null,
                            null,
                            Boolean.TRUE,
                            null),
                    new TypeToken<Watch.Response<V1Pod>>() {}.getType())
            ){
                for (Watch.Response<V1Pod> ignored : watch) {

                    Objects.requireNonNull(ignored.object.getMetadata(), "V1Pod metadata must be available");

//                    logger.info("Pod [{}] State change [{}] Status [{}] aaa [{}]",
//                            ignored.object.getMetadata().getName(), ignored.type, ignored.object.getStatus(), ignored.object.getMetadata());

                    appsV1Api.listNamespacedStatefulSet(
                                    namespace,
                                    "true",
                                    null,
                                    null,
                                    null,
                                    "app=monitoring-logstash",
                                    null,
                                    null,
                                    null,
                                    null,
                                    null)
                            .getItems()
                            .forEach(v1StatefulSet -> {
                                Objects.requireNonNull(v1StatefulSet.getMetadata());
                                Objects.requireNonNull(v1StatefulSet.getStatus());

                                Integer updatedReplicas
                                        = v1StatefulSet.getStatus().getUpdatedReplicas();

                                Long generation = v1StatefulSet.getMetadata().getGeneration();

                                Objects.requireNonNull(generation);

                                currentCompletions = updatedReplicas == null ? 0 : updatedReplicas;
                                logger.debug("Statefulset [{}] CurrentCompletions [{}] CurrentGeneration [{}]",
                                        v1StatefulSet.getMetadata().getName(), currentCompletions, generation);
                                logger.info("statefulset {}", v1StatefulSet.getSpec().getReplicas());

                                if(currentCompletions == completionsQuantity && generation == 2L){
                                    System.exit(0);
                                }
                            });
                }
            }catch (Exception e){
                logger.error(String.valueOf(e));
                throw new RuntimeException(e);
            } finally {
                watch.close();
            }
//        }
    }

    private void setStatefulSetInfo() throws ApiException, InterruptedException {
        logger.debug("getStatefulSetInfo");

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                execute();
                if(v1StatefulSetList.getItems().size() > 0){
                    executor.shutdown();
                }
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void execute() throws ApiException {

        v1StatefulSetList = appsV1Api.listNamespacedStatefulSet(
                namespace,
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

        String resourceName = "logstash";
        List<V1StatefulSet> filteredList = v1StatefulSetList.getItems()
                .stream()
                .filter(v1StatefulSet -> {
                    Objects.requireNonNull(v1StatefulSet.getMetadata());
                    Objects.requireNonNull(v1StatefulSet.getMetadata().getName());
                    return Objects.equals(
                            Objects.requireNonNull(v1StatefulSet.getMetadata()).getName().toLowerCase(Locale.ROOT),
                            resourceName.toLowerCase(Locale.ROOT));
                })
                .collect(Collectors.toList());

        filteredList.forEach(v1StatefulSet -> {
            Objects.requireNonNull(v1StatefulSet.getStatus());
            logger.info(
                    "StatefulSet.status.availableReplicas: {} StatefulSet.status.currentReplicas: {} StatefulSet.status.readyReplicas: {} StatefulSet.status.replicas {}: ",
                    v1StatefulSet.getStatus().getAvailableReplicas(),
                    v1StatefulSet.getStatus().getCurrentReplicas(),
                    v1StatefulSet.getStatus().getReadyReplicas(),
                    v1StatefulSet.getStatus().getReplicas()
            );
            v1PodListSize.set(v1StatefulSet.getStatus().getAvailableReplicas());
        });
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
