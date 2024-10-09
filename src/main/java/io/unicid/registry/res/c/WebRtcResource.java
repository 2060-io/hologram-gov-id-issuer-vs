package io.unicid.registry.res.c;

import io.unicid.registry.model.res.CreateRoomRequest;
import io.unicid.registry.model.res.webRtc.WebRtcCallData;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient
@Path("")
public interface WebRtcResource {

  @POST
  @Path("/rooms/{roomId}")
  @Consumes("application/json")
  @Produces("application/json")
  public WebRtcCallData createRoom(
      @PathParam(value = "roomId") UUID uuid, CreateRoomRequest request);
}
