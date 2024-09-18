package io.unicid.registry.res.c;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@RegisterRestClient
@Path("")
public interface WebRTCResource {

	@GET
	@Path("/r")
	public Response createRoom(@QueryParam(value = "notificationUri") String uri);
    
}
