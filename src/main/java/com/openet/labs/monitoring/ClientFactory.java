package com.openet.labs.monitoring;


import io.kubernetes.client.openapi.ApiException;

import java.io.IOException;

public class ClientFactory {
    public MonitorResource getClient(ClientType clientType) {
        if(clientType == ClientType.WAIT){
            return new WaitDefault();
        }
        return null;
    }
}