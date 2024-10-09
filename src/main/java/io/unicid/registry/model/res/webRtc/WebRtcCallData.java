package io.unicid.registry.model.res.webRtc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "protocol")
@JsonSubTypes({
  @Type(value = WebRtcCallDataV1.class, name = "2060-mediasoup-v1"),
})
@Getter
@Setter
public abstract class WebRtcCallData implements Serializable {
  private static final long serialVersionUID = 1L;

  private String protocol;
  private String roomId;
  private String wsUrl;
}
