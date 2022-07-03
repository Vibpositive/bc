package com.openet.labs.monitoring;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class WaitRollingUpdate extends ClientFactory implements Resource{

    CoreV1Api api;
    AppsV1Api appsV1Api;

    String namespace;
    String resourceName;
    ApiClient client;
    OkHttpClient httpClient;


    private Integer completionsQuantity = 0;
    private int currentCompletions;
    Long generation;

    int sleepTimeout;

    private static final Logger logger = LoggerFactory.getLogger(WaitRollingUpdate.class);

    public Job job = () -> {
        try {
            if(!isCompletionQuantity()){
                setCompletionsQuantity();
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

    private void run() throws Throwable {
        logger.debug("run");

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

                logger.debug("Pod [{}] State change [{}]",
                        ignored.object.getMetadata().getName(), ignored.type);


                Optional<V1StatefulSet> first = getStatefulset()
                        .getItems()
                        .stream().filter(v1StatefulSet -> {
                            V1ObjectMeta meta = v1StatefulSet.getMetadata();
                            Objects.requireNonNull(meta);
                            Long generation = meta.getGeneration();
                            Objects.requireNonNull(generation);

                            return generation > 1;
                        })
                        .findFirst();
                if(first.isPresent() && ignored.type.equals("ADDED")){
                    extracted(first.orElseThrow((Supplier<Throwable>) () -> new RuntimeException("TODO:")));
                }
            }
        }catch (Exception e){
            logger.error("{}", (Object) e.getStackTrace());
            e.printStackTrace();
//            TODO when this happend, app is not exiting
            throw new RuntimeException(e);
        }
    }

    private void extracted(V1StatefulSet v1StatefulSet) {
        Objects.requireNonNull(v1StatefulSet.getMetadata());
        Objects.requireNonNull(v1StatefulSet.getStatus());

//        Integer updatedReplicas
//                = v1StatefulSet.getStatus().getUpdatedReplicas();

        generation = v1StatefulSet.getMetadata().getGeneration();
        Objects.requireNonNull(generation);

        currentCompletions++;
        Objects.requireNonNull(completionsQuantity);

        logger.debug("Statefulset [{}] CurrentCompletions [{}] completionsQuantity [{}] CurrentGeneration [{}]",
                v1StatefulSet.getMetadata().getName(), currentCompletions, completionsQuantity, generation);

//        TODO generation needs to be set via arguments
        if(currentCompletions == completionsQuantity && generation == 2L){
            System.exit(0);
        }
    }


    private void setCompletionsQuantity() {
        logger.debug("getStatefulSetInfo");

//        TODO:
//        CompletableFuture<Integer> future
//                = CompletableFuture.supplyAsync(() -> -1);
        while(!isCompletionQuantity()){
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
                    logger.debug(Thread.currentThread().getName());
                    logger.debug("shutting down executor");
                }
                //noinspection BusyWait
                Thread.sleep(500);
            } catch (ApiException | InterruptedException e) {
//                TODO test this
                throw new RuntimeException(e);
            }
        }
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
