package io.unicid.registry.svc;

import io.unicid.registry.model.CallRegistry;
import io.unicid.registry.model.Identity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RegisterService {

	@Inject EntityManager em;

	private static final Logger logger = Logger.getLogger(RegisterService.class);
	
	@ConfigProperty(name = "io.unicid.debug")
	Boolean debug;
	
	@Transactional
	public CallRegistry getCallByIdentity(Identity identity) {
		TypedQuery<CallRegistry> q = em.createNamedQuery("CallRegistry.findForIdentity", CallRegistry.class);
		q.setParameter("identity", identity);
		CallRegistry registry = q.getResultList().stream().findFirst().orElse(null);
		if (debug){
			logger.info("getCallByIdentity: " + registry.getId());
		}
		return registry;
	}

	@Transactional
	public CallRegistry getCallByPeer(String peerId) {
		TypedQuery<CallRegistry> q = em.createNamedQuery("CallRegistry.findForPeerId", CallRegistry.class);
		q.setParameter("peerId", peerId);
		CallRegistry registry = q.getResultList().stream().findFirst().orElse(null);
		if (debug){
			logger.info("getCallByPeer: " + registry.getId());
		}
		return registry;
	}

}