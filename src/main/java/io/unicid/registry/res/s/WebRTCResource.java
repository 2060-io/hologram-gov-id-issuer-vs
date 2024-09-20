package io.unicid.registry.res.s;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.logging.Logger;

import io.unicid.registry.model.objects.NotificationRequest;
import io.unicid.registry.svc.VisionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("")
public class WebRTCResource {

	private static Logger logger = Logger.getLogger(VisionResource.class);

    @ConfigProperty(name = "io.unicid.debug")
	Boolean debug;

	@Inject VisionService service;
	
	@POST
    @Path("/notificationUri")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update notification state",
        description = "Update the state for a peer joining a room")
    @APIResponses({
        @APIResponse(responseCode = "400", description = "Invalid input data"),
        @APIResponse(responseCode = "500", description = "Server error, please retry."),
        @APIResponse(responseCode = "200", description = "State successfully updated.") }
    )
    public Response notificationUri(NotificationRequest notificationRequest) {
        
        if (notificationRequest.roomId == null || notificationRequest.peerId == null) {
            return Response.status(Status.BAD_REQUEST).entity("roomId or peerId is missing").build();
        }

        try {
            service.connectToRoom("wss://?roomId="+notificationRequest.roomId);
            
            return Response.status(Status.OK).entity("State successfully updated").build();
        } catch (Exception e) {
            logger.error("Error updating state", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("An error occurred while updating the state").build();
        }
    }

    
}
