package com.openet.labs.monitoring;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1StatefulSetList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WaitRollingUpdate extends ClientFactory implements Resource{

    CoreV1Api api;
    AppsV1Api appsV1Api;

    String namespace;
    String resourceName;
    ApiClient client;
    OkHttpClient httpClient;


    private Integer completionsQuantity = -1;
    private int currentCompletions;

    int sleepTimeout;

    private static final Logger logger = LoggerFactory.getLogger(WaitRollingUpdate.class);

    public Job job = () -> {
        try {
            if(!isCompletionQuantity()){
                statefulSetInfoExecutor();
            }else{
                run();
            }
        } catch (Throwable e){
            throw new RuntimeException(e);
        }
        logger.info("Sleeping for {}ms", sleepTimeout);
    };

    private boolean isCompletionQuantity() {
        return completionsQuantity > 0;
    }

    private void run() {
        logger.debug("run");
//        TODO need to implement a watch for stateful set, as soon as stateful set changes, we start watching for
//        TODO pods with Watch<V1Pod>
        try (Watch<V1Pod> watch = Watch.createWatch(
                client,
                api.listNamespacedPodCall(
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
                        Boolean.TRUE,
                        null),
                new TypeToken<Watch.Response<V1Pod>>() {}.getType())
        ){
            for (Watch.Response<V1Pod> ignored : watch) {

                Objects.requireNonNull(ignored.object.getMetadata(), "V1Pod metadata must be available");

                logger.info("Pod [{}] State change [{}]",
                        ignored.object.getMetadata().getName(), ignored.type);

                getStatefulset()
                    .getItems()
                    .forEach(v1StatefulSet -> {
                        Objects.requireNonNull(v1StatefulSet.getMetadata());
                        Objects.requireNonNull(v1StatefulSet.getStatus());

                        Integer updatedReplicas
                                = v1StatefulSet.getStatus().getUpdatedReplicas();

                        Long generation = v1StatefulSet.getMetadata().getGeneration();
                        Objects.requireNonNull(generation);

                        currentCompletions = updatedReplicas == null ? 0 : updatedReplicas;
                        Objects.requireNonNull(completionsQuantity);

                        logger.debug("Statefulset [{}] CurrentCompletions [{}] completionsQuantity [{}] CurrentGeneration [{}]",
                                v1StatefulSet.getMetadata().getName(), currentCompletions, completionsQuantity, generation);

                        if(currentCompletions == completionsQuantity && generation == 1L){
                            currentCompletions = 0;
                        }
                        if(currentCompletions == completionsQuantity && generation == 2L){
                            logger.info("Would exit");
//                            System.exit(0);
                        }
                    });
            }
        }catch (Exception e){
            logger.error("{}", (Object) e.getStackTrace());
            e.printStackTrace();
//            TODO when this happend, app is not exiting
            throw new RuntimeException(e);
        }
    }


    private void statefulSetInfoExecutor() {
        logger.debug("getStatefulSetInfo");
        if(isCompletionQuantity()){
            return;
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                if(getStatefulset().getItems().size() > 0){
                    completionsQuantity = getStatefulset()
                            .getItems()
                            .stream().findFirst()
                            .map(v1StatefulSet -> {
                                Objects.requireNonNull(v1StatefulSet.getSpec());
                                Objects.requireNonNull(v1StatefulSet.getSpec().getReplicas());

                                return v1StatefulSet.getSpec().getReplicas();
                            }).get();
                    logger.debug("setting completionsQuantity to {}", completionsQuantity);
                    executor.shutdown();
                    logger.debug("shutting down executor");
                }
            } catch (ApiException e) {
//                TODO test this
                throw new RuntimeException(e);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private V1StatefulSetList getStatefulset() throws ApiException {

        return appsV1Api.listNamespacedStatefulSet(
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
    }

    public Job getJob() {
        return job;
    }

    @Override
    public Resource Init(String namespace, String resourceName, int sleepTimeout, int completionQuantity) {
        return Init(namespace, resourceName, sleepTimeout);
    }
    public Resource Init(String namespace, String resourceName, int sleepTimeout) {
        logger.info("WaitRollingUpdate.init()");


        try {
            client = Config.defaultClient();
            httpClient =
                    client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
            client.setHttpClient(httpClient);
            Configuration.setDefaultApiClient(client);

            api = new CoreV1Api();
            appsV1Api = new AppsV1Api(client);
            this.namespace = namespace;
            this.resourceName = resourceName;
            this.sleepTimeout = sleepTimeout;
            return this;
        } catch (IOException e) {
//                TODO test this
            throw new RuntimeException(e);
        }
    }

}
