package com.openet.labs.monitoring;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JobExecutor {
    public static void schedule(Job consumer) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(consumer::run, 0, 500, TimeUnit.MILLISECONDS);
    }
}
