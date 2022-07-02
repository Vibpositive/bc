package com.openet.labs.monitoring;


public class ClientFactory {

    public Resource getClient(ClientType clientType) {
        if(clientType == ClientType.ROLLING_UPDATE){
            return new WaitRollingUpdate();
        } else if (clientType == ClientType.PARTIAL_ROLLING_UPDATR) {
           return new WaitPartialRollingUpdate();
        }
        return null;
    }
}