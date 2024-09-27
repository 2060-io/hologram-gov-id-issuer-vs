package io.unicid.registry.res.c;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.unicid.registry.model.res.CreateRoomRequest;
import io.unicid.registry.model.res.DataWsUrl;
import java.util.UUID;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

@RegisterRestClient
@Path("")
public interface WebRTCResource {

	@POST
	@Path("/rooms/{roomId}")
	@Consumes("application/json")
	@Produces("application/json")
	public DataWsUrl createRoom(@PathParam(value = "roomId") UUID uuid,CreateRoomRequest request);
    
}
