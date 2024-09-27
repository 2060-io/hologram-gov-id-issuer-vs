package io.unicid.registry.model.res;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty("success_url")
    private String successUrl;
    @JsonProperty("failure_url")
    private String failureUrl;
    
}