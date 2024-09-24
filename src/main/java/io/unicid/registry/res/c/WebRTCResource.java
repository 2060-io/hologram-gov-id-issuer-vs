package io.unicid.registry.res.c;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.unicid.registry.model.res.CreateRoomRequest;
import io.unicid.registry.model.res.DataWsUrl;
import io.unicid.registry.model.res.JoinCallRequest;

import java.util.UUID;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@RegisterRestClient
@Path("")
public interface WebRTCResource {

	@POST
	@Path("/rooms/{roomId}")
	@Consumes("application/json")
	@Produces("application/json")
	public DataWsUrl createRoom(@PathParam(value = "roomId") UUID uuid,CreateRoomRequest request);

	@POST
	@Path("/join-call")
	@Consumes("application/json")
	@Produces("application/json")
	public Response joinCall(JoinCallRequest request);
    
}
