package io.unicid.registry.res.c;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.unicid.registry.model.res.JoinCallRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@RegisterRestClient
@Path("")
public interface VisionResource {

	@GET
	@Path("/r")
	@Consumes("application/json")
	@Produces("application/json")
	public Response connectToRoom(String uri);

	@POST
	@Path("/join-call")
	@Consumes("application/json")
	@Produces("application/json")
	public Response joinCall(JoinCallRequest request);
    
}
