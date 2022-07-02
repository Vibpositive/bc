package com.openet.labs.monitoring;

import io.kubernetes.client.openapi.ApiException;

import java.io.IOException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException, ApiException {

        ArgumentParser argumentParser = new ArgumentParser(args);

        String service = Objects.requireNonNull(argumentParser.getArgumentValue("service"));
        String namespace = Objects.requireNonNull(argumentParser.getArgumentValue("namespace"));

        if (ClientType.WAIT.label.equals(service)) {
            logger.info("ReSyncBalanceDetailsUtil::ReSync latestBalanceDetail with checksum synced: {}", "aloha");
            MonitorResource wait = new ClientFactory().getClient(ClientType.WAIT).Init(namespace);
            JobExecutor.schedule(wait.getJob());
        }
    }
}