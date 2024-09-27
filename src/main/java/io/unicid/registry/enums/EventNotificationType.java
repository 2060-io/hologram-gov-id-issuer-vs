package io.unicid.registry.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum EventNotificationType {
        @JsonProperty("peer-joined")
        PEER_JOINED,
        @JsonProperty("peer-left")
        PEER_LEFT,	
        ;
    }
