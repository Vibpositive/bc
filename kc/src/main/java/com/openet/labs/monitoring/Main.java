package com.openet.labs.monitoring;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        ArgumentParser argumentParser = new ArgumentParser(args);

        Objects.requireNonNull(argumentParser.getArgumentValue("service"));
        Objects.requireNonNull(argumentParser.getArgumentValue("namespace"));
        Objects.requireNonNull(argumentParser.getArgumentValue("executorInterval"));
        Objects.requireNonNull(argumentParser.getArgumentValue("resourceName"));

        String service = argumentParser.getArgumentValue("service");
        String namespace = argumentParser.getArgumentValue("namespace");
        String executorInterval = argumentParser.getArgumentValue("executorInterval");
        String resourceName = argumentParser.getArgumentValue("resourceName");
        int executorIntervalInt = executorInterval != null ?
                Integer.parseInt(executorInterval) : 5;
        String completionsQuantityString = argumentParser.getArgumentValue("completionsQuantity");
        ClientType selectedClientType;
        int completionsQuantity = -1;
        if(completionsQuantityString != null){
           completionsQuantity = Integer.parseInt(completionsQuantityString) ;
        }

        if (ClientType.ROLLING_UPDATE.label.equals(service)) {
            selectedClientType = ClientType.ROLLING_UPDATE;
        } else if (ClientType.PARTIAL_ROLLING_UPDATR.label.equals(service)) {
            selectedClientType = ClientType.PARTIAL_ROLLING_UPDATR;
        }else {
            logger.error("Unrecognized CLIENT_TYPE selection: {}", service);
            selectedClientType = null;
            System.exit(1);
        }

        logger.info("Starting {} Application", selectedClientType);
        Resource wait = new ClientFactory()
                .getClient(selectedClientType)
                .Init(namespace, resourceName, executorIntervalInt, completionsQuantity);
        JobExecutor.schedule(wait.getJob(), executorIntervalInt);
    }
}