package com.openet.labs.monitoring;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.proto.V1;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

public class WatchPod {
    public static void main(String[] args) throws IOException, ApiException {
        ApiClient client = Config.defaultClient();
        Stack<V1Pod> v1PodStack = new Stack<>();


        // infinite timeout
        OkHttpClient httpClient =
                client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
        client.setHttpClient(httpClient);
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();
        AppsV1Api appsV1Api = new AppsV1Api();

//        V1StatefulSetList v1StatefulSetList =
        Watch<V1Pod> watch = Watch.createWatch(
                client,
                api.listNamespacedPodCall(
                        "monitoring",
                        null,
                        null,
                        null,
                        null,
                        null, 100,
                        null,
                        null,
                        null,
                        null,
                        null),
                new TypeToken<Watch.Response<V1Pod>>() {
                }.getType());
        try {
            for (Watch.Response<V1Pod> item : watch) {
                if(item.type.equals("MODIFIED")){
                    System.out.printf("%s : %s%n", item.type, Objects.requireNonNull(item.object.getMetadata()).getName());
                }
            }
        } finally {
            watch.close();
        }
    }
}