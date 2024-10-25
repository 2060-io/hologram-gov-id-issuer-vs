package io.unicid.registry.svc;

import io.unicid.registry.enums.MediaType;
import io.unicid.registry.enums.PeerType;
import io.unicid.registry.enums.TokenType;
import io.unicid.registry.ex.MediaAlreadyLinkedException;
import io.unicid.registry.ex.TokenException;
import io.unicid.registry.model.Identity;
import io.unicid.registry.model.Media;
import io.unicid.registry.model.PeerRegistry;
import io.unicid.registry.model.Token;
import io.unicid.registry.model.res.JoinCallRequest;
import io.unicid.registry.model.res.NotificationRequest;
import io.unicid.registry.res.c.VisionResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class VisionService {

  private static final Logger logger = Logger.getLogger(VisionService.class);

  @Inject Service service;

  @Inject EntityManager em;

  @Inject @RestClient VisionResource vs;

  @Inject RegisterService registerService;

  @ConfigProperty(name = "io.unicid.create.token.lifetimeseconds")
  Long createTokenLifetimeSec;

  @ConfigProperty(name = "io.unicid.verify.token.lifetimeseconds")
  Long verifyTokenLifetimeSec;

  @ConfigProperty(name = "io.unicid.vision.redirdomain")
  Optional<String> redirDomain;

  @ConfigProperty(name = "io.unicid.vision.redirdomain.d")
  Optional<String> dRedirDomain;

  public List<UUID> listMedias(UUID tokenId) throws Exception {

    Token token = this.getToken(tokenId);
    if (token == null) throw new TokenException();

    Identity identity = token.getIdentity();

    if ((identity.getProtectedTs() != null)
        && (identity.getDeletedTs() == null)
        && (identity.getConnectionId() != null)) {

      Query q = this.em.createNamedQuery("Media.find");
      q.setParameter("identity", identity);

      logger.info("listMedias: token: " + tokenId);

      switch (token.getType()) {
        case FACE_VERIFICATION:
          {
            q.setParameter("type", MediaType.FACE);
            List<UUID> medias = q.getResultList();

            return medias;
          }
        case FINGERPRINT_VERIFICATION:
          {
            q.setParameter("type", MediaType.FINGERPRINT);
            List<UUID> medias = q.getResultList();

            return medias;
          }
        case WEBRTC_VERIFICATION:
          {
            q.setParameter("type", MediaType.WEBRTC);
            List<UUID> medias = q.getResultList();

            return medias;
          }
        default:
          {
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
        case FACE_CAPTURE:
          {
            media = new Media();
            media.setId(mediaId);
            media.setIdentity(identity);
            media.setTs(Instant.now());
            media.setType(MediaType.FACE);
            em.persist(media);
            break;
          }

        case FINGERPRINT_VERIFICATION:
        case FINGERPRINT_CAPTURE:
          {
            media = new Media();
            media.setId(mediaId);
            media.setIdentity(identity);
            media.setTs(Instant.now());
            media.setType(MediaType.FINGERPRINT);
            em.persist(media);
            break;
          }

        case WEBRTC_VERIFICATION:
        case WEBRTC_CAPTURE:
          {
            media = new Media();
            media.setId(mediaId);
            media.setIdentity(identity);
            media.setTs(Instant.now());
            media.setType(MediaType.WEBRTC);
            em.persist(media);
            break;
          }
      }
      logger.info("linkMedia: token: " + media);
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

  public Token createToken(
      TokenType tokenType, Identity identity, UUID connectionId, UUID threadId) {
    Token token = new Token();
    token.setId(UUID.randomUUID());
    token.setConnectionId(connectionId);
    token.setThreadId(threadId);
    token.setIdentity(identity);
    token.setType(tokenType);

    switch (tokenType) {
      case FACE_CAPTURE:
      case FINGERPRINT_CAPTURE:
      case WEBRTC_CAPTURE:
        {
          token.setExpireTs(Instant.now().plus(Duration.ofSeconds(createTokenLifetimeSec)));
          break;
        }
      case FACE_VERIFICATION:
      case FINGERPRINT_VERIFICATION:
      case WEBRTC_VERIFICATION:
        {
          token.setExpireTs(Instant.now().plus(Duration.ofSeconds(verifyTokenLifetimeSec)));
          break;
        }
    }

    em.persist(token);

    return token;
  }

  @Transactional
  public void joinCall(NotificationRequest notificationRequest) {

    PeerRegistry cr = updatePeerRegistry(notificationRequest);

    if (cr.getType().equals(PeerType.PEER_USER)) {
      Token t =
          registerService.getTokenByConnection(cr.getConnectionId(), TokenType.WEBRTC_VERIFICATION);
      String lang = service.getConnection(t.getConnectionId()).getLanguage();

      // Create registry vision
      PeerRegistry crv = new PeerRegistry();
      UUID peerId = UUID.randomUUID();
      crv.setId(peerId);
      crv.setConnectionId(null);
      crv.setRoomId(cr.getRoomId());
      crv.setWsUrl(cr.getWsUrl());
      crv.setType(PeerType.PEER_VISION);
      em.persist(crv);

      JoinCallRequest jc = new JoinCallRequest();
      jc.setWsUrl(cr.getWsUrl() + "/?roomId=" + cr.getRoomId() + "&peerId=" + peerId);
      jc.setCallbackBaseUrl(redirDomain.get());
      jc.setDatastoreBaseUrl(dRedirDomain.get());
      jc.setToken(t.getId().toString());
      jc.setLang(lang);

      vs.joinCall(jc);
    }
  }

  public void leaveCall(NotificationRequest notificationRequest) {
    updatePeerRegistry(notificationRequest);
  }

  @Transactional
  public PeerRegistry updatePeerRegistry(NotificationRequest notificationRequest) {
    PeerRegistry cr = registerService.getPeerById(UUID.fromString(notificationRequest.peerId));
    if (cr == null) {
      throw new IllegalArgumentException(
          "No call found for peerId: " + notificationRequest.getPeerId());
    }
    cr.setEvent(notificationRequest.event);
    em.merge(cr);
    return cr;
  }
}
