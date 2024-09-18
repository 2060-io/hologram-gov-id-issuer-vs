package io.unicid.registry.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationRequest {
    public boolean peerJoined;
    public String roomId;
    public String peerId;

}
