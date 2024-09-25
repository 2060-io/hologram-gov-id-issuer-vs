package io.unicid.registry.svc;

import io.unicid.registry.enums.TokenType;
import io.unicid.registry.model.PeerRegistry;
import io.unicid.registry.model.Identity;
import io.unicid.registry.model.Token;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RegisterService {

	@Inject EntityManager em;

	private static final Logger logger = Logger.getLogger(RegisterService.class);
	
	@ConfigProperty(name = "io.unicid.debug")
	Boolean debug;
	
	@Transactional
	public PeerRegistry getCallByIdentity(Identity identity) {
		TypedQuery<PeerRegistry> q = em.createNamedQuery("CallRegistry.findForIdentity", PeerRegistry.class);
		q.setParameter("identity", identity);
		PeerRegistry registry = q.getResultList().stream().findFirst().orElse(null);
		if (debug){
			logger.info("getCallByIdentity: " + registry.getId());
		}
		return registry;
	}

	@Transactional
	public PeerRegistry getPeerById(String peerId) {
		TypedQuery<PeerRegistry> q = em.createNamedQuery("CallRegistry.findForId", PeerRegistry.class);
		q.setParameter("id", peerId);
		PeerRegistry registry = q.getResultList().stream().findFirst().orElse(null);
		if (debug){
			logger.info("getCallByPeer: " + registry.getId());
		}
		return registry;
	}

	@Transactional
	public Token getTokenByConnection(UUID connectionId) {
		TypedQuery<Token> q = em.createNamedQuery("Token.findForConnection", Token.class);
		q.setParameter("connectionId", connectionId);
		q.setParameter("type", TokenType.WEBRTC_VERIFICATION);
		Token registry = q.getResultList().stream().findFirst().orElse(null);
		if (debug){
			logger.info("getTokenByConnection: " + registry.getId());
		}
		return registry;
	}

}