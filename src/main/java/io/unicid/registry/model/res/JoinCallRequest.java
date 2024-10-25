package io.unicid.registry.model.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class JoinCallRequest implements Serializable {
  private static final long serialVersionUID = 1L;

  @JsonProperty("ws_url")
  private String wsUrl;

  @JsonProperty("datastore_base_url")
  private String datastoreBaseUrl;

  @JsonProperty("callback_base_url")
  private String callbackBaseUrl;

  @JsonProperty("token")
  private String token;

  @JsonProperty("lang")
  private String lang;
}
