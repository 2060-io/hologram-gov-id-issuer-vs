package io.unicid.registry.res.s;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.twentysixty.sa.client.model.event.ConnectionStateUpdated;
import io.twentysixty.sa.client.res.s.ConnectionEventInterface;
import io.twentysixty.sa.client.util.JsonUtil;
import io.unicid.registry.svc.Service;

@Path("")
public class ConnectionEventResource implements ConnectionEventInterface {

	
	private static Logger logger = Logger.getLogger(ConnectionEventResource.class);

	@Inject Service service;
	@ConfigProperty(name = "io.unicid.debug")
	Boolean debug;

	
	@Override
	@POST
	@Path("/connection-state-updated")
	@Produces("application/json")
	public Response connectionStateUpdated(ConnectionStateUpdated event) {
		if (debug) {
			try {
				logger.info("connectionStateUpdated: " + JsonUtil.serialize(event, false));
			} catch (JsonProcessingException e) {
				logger.error("", e);
			}
		}
		switch (event.getState()) {
			case COMPLETED: {
				try {
					service.newConnection(event);
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			case TERMINATED: {
				try {
					service.deleteConnection(event);
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}
			default:
				break;
			}
		
		return  Response.status(Status.OK).build();
		
	}

}
