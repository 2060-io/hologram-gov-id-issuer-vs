package io.unicid.registry.model.objects;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Getter;
import lombok.Setter;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class NotificationRequest implements Serializable {

	private static final long serialVersionUID = 1L;

    public boolean peerJoined;
    public String roomId;
    public String peerId;

}
