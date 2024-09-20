package io.unicid.registry.res.c;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
    
}
