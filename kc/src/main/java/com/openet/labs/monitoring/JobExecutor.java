package com.openet.labs.monitoring;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JobExecutor {
    public static void schedule(Job consumer, int timeout) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try{
                consumer.run();
            }catch (Exception e){
                e.printStackTrace();
                System.exit(1);
            }
        }, 0, timeout, TimeUnit.MILLISECONDS);
    }
}
