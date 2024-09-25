package io.unicid.registry.svc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.unicid.registry.enums.MediaType;
import io.unicid.registry.enums.TokenType;
import io.unicid.registry.ex.MediaAlreadyLinkedException;
import io.unicid.registry.ex.TokenException;
import io.unicid.registry.model.PeerRegistry;
import io.unicid.registry.model.Identity;
import io.unicid.registry.model.Media;
import io.unicid.registry.model.Token;
import io.unicid.registry.model.res.JoinCallRequest;
import io.unicid.registry.model.res.NotificationRequest;
import io.unicid.registry.res.c.WebRTCResource;

@ApplicationScoped
public class VisionService {

	
	@Inject Service service;
	
	@Inject EntityManager em;

	// @Inject @RestClient VisionResource vs;
	@Inject @RestClient WebRTCResource wb;
	@Inject RegisterService registerService;
	
	
	@ConfigProperty(name = "io.unicid.create.token.lifetimeseconds")
	Long createTokenLifetimeSec;
	
	@ConfigProperty(name = "io.unicid.verify.token.lifetimeseconds")
	Long verifyTokenLifetimeSec;


	@ConfigProperty(name = "io.unicid.vision.redirdomain")
	Optional<String> redirDomain;
	
	
	
	public List<UUID> listMedias(UUID tokenId) throws Exception {
		
		Token token = this.getToken(tokenId);
		if (token == null) throw new TokenException();
		
		Identity identity = token.getIdentity();
		
		
		if ((identity.getProtectedTs() != null) 
				&& (identity.getDeletedTs() == null) && (identity.getConnectionId() != null)) {
			
			Query q = this.em.createNamedQuery("Media.find");
			q.setParameter("identity", identity);
			
			
			switch (token.getType()) {
				case FACE_VERIFICATION: {
					
					q.setParameter("type", MediaType.FACE);
					List<UUID> medias = q.getResultList();
					
					return medias;
				}
				case FINGERPRINT_VERIFICATION: {
					q.setParameter("type", MediaType.FINGERPRINT);
					List<UUID> medias = q.getResultList();
					
					return medias;
				}
				default: {
					throw new TokenException();
				}
			}
			
			
		}
		
		throw new TokenException();
		
	}

	@Transactional
	public void linkMedia(UUID tokenId, UUID mediaId) throws Exception {
		
		
		Token token = this.getToken(tokenId);
		if (token == null) throw new TokenException();
		
		Identity identity = token.getIdentity();
		Media media = em.find(Media.class, mediaId);
		if (media != null) {
			throw new MediaAlreadyLinkedException();
		}
		
		if ((identity.getDeletedTs() == null) && (identity.getConnectionId() != null)) {
			switch (token.getType()) {
			
			case FACE_VERIFICATION:
			case FACE_CAPTURE: {
				
				media = new Media();
				media.setId(mediaId);
				media.setIdentity(identity);
				media.setTs(Instant.now());
				media.setType(MediaType.FACE);
				em.persist(media);
				break;
			}
			
			case FINGERPRINT_VERIFICATION:
			case FINGERPRINT_CAPTURE: {
				
				media = new Media();
				media.setId(mediaId);
				media.setIdentity(identity);
				media.setTs(Instant.now());
				media.setType(MediaType.FINGERPRINT);
				em.persist(media);
				break;
			}
			
			
			}
		} else {
			throw new TokenException();
		}
		
	}

	
	private Token getToken(UUID uuid) throws TokenException {
		Token t = em.find(Token.class, uuid);
		if (t == null) throw new TokenException();
		if (t.getExpireTs() == null) throw new TokenException();
		if (t.getExpireTs().isBefore(Instant.now())) throw new TokenException();
		return t;
	}

	public void success(UUID tokenId) throws Exception {
		
		Token token = this.getToken(tokenId);
		if (token == null) throw new TokenException();
		
		service.notifySuccess(token);
		
		
	}

	
	public void failure(UUID tokenId) throws Exception {
		Token token = this.getToken(tokenId);
		if (token == null) throw new TokenException();
		
		service.notifyFailure(token);
		
	
		
	}
	
	
	public Token createToken(TokenType tokenType, Identity identity, UUID connectionId, UUID threadId) {
		Token token = new Token();
		token.setId(UUID.randomUUID());
		token.setConnectionId(connectionId);
		token.setThreadId(threadId);
		token.setIdentity(identity);
		token.setType(tokenType);
		
		switch (tokenType) {
		case FACE_CAPTURE:
		case FINGERPRINT_CAPTURE:
		{
			token.setExpireTs(Instant.now().plus(Duration.ofSeconds(createTokenLifetimeSec)));
			break;
		}
		case FACE_VERIFICATION:
		case FINGERPRINT_VERIFICATION:
		case WEBRTC_VERIFICATION: {
			token.setExpireTs(Instant.now().plus(Duration.ofSeconds(verifyTokenLifetimeSec)));
			break;
		}
		}
		
		em.persist(token);
		
		return token;
	}
	
	
	public void connectToRoom(NotificationRequest notificationRequest) {
		
		// return vs.connectToRoom(wsUrl);
		PeerRegistry cr = updatePeerRegistry(notificationRequest, true);
		Token t = registerService.getTokenByConnection(cr.getIdentity().getConnectionId());
		
		JoinCallRequest jc = new JoinCallRequest();
		jc.setWsUrl(cr.getWsUrl());
		jc.setSuccessUrl(redirDomain+"/success/"+t.getId());
		jc.setFailureUrl(redirDomain+"/failure/"+t.getId());
		
		wb.joinCall(jc); // TODO: create peer registry with room and add in the wsUrl
	}

	public void leftToRoom(NotificationRequest notificationRequest) {
		updatePeerRegistry(notificationRequest, false);
	}

	private PeerRegistry updatePeerRegistry(NotificationRequest notificationRequest, Boolean isActive){
		PeerRegistry cr = registerService.getPeerById(notificationRequest.peerId);
		if(cr == null) {
			throw new IllegalArgumentException("No call found for peerId: " + notificationRequest.getPeerId());
		}
		cr.setEvent(notificationRequest.event);
		cr.setIsActive(isActive);
		em.merge(cr);
		return cr;
	}
}
