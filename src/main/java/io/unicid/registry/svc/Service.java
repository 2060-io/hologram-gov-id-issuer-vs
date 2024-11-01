package io.unicid.registry.svc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.twentysixty.sa.client.model.credential.CredentialType;
import io.twentysixty.sa.client.model.event.ConnectionStateUpdated;
import io.twentysixty.sa.client.model.message.BaseMessage;
import io.twentysixty.sa.client.model.message.Ciphering;
import io.twentysixty.sa.client.model.message.Claim;
import io.twentysixty.sa.client.model.message.ContextualMenuItem;
import io.twentysixty.sa.client.model.message.ContextualMenuSelect;
import io.twentysixty.sa.client.model.message.ContextualMenuUpdate;
import io.twentysixty.sa.client.model.message.CredentialIssuanceMessage;
import io.twentysixty.sa.client.model.message.CredentialReceptionMessage;
import io.twentysixty.sa.client.model.message.MediaItem;
import io.twentysixty.sa.client.model.message.MediaMessage;
import io.twentysixty.sa.client.model.message.MenuDisplayMessage;
import io.twentysixty.sa.client.model.message.MenuItem;
import io.twentysixty.sa.client.model.message.MenuSelectMessage;
import io.twentysixty.sa.client.model.message.Parameters;
import io.twentysixty.sa.client.model.message.ProfileMessage;
import io.twentysixty.sa.client.model.message.TextMessage;
import io.twentysixty.sa.client.model.message.calls.CallOfferRequestMessage;
import io.twentysixty.sa.client.model.message.mrtd.EMrtdDataRequestMessage;
import io.twentysixty.sa.client.model.message.mrtd.EMrtdDataSubmitMessage;
import io.twentysixty.sa.client.model.message.mrtd.MrzDataRequestMessage;
import io.twentysixty.sa.client.model.message.mrtd.MrzDataSubmitMessage;
import io.twentysixty.sa.client.util.Aes256cbc;
import io.twentysixty.sa.client.util.JsonUtil;
import io.twentysixty.sa.res.c.CredentialTypeResource;
import io.twentysixty.sa.res.c.MessageResource;
import io.unicid.registry.enums.CreateStep;
import io.unicid.registry.enums.IdentityClaim;
import io.unicid.registry.enums.IssueStep;
import io.unicid.registry.enums.MediaType;
import io.unicid.registry.enums.PeerType;
import io.unicid.registry.enums.Protection;
import io.unicid.registry.enums.RestoreStep;
import io.unicid.registry.enums.SessionType;
import io.unicid.registry.enums.TokenType;
import io.unicid.registry.ex.NoMediaException;
import io.unicid.registry.ex.TokenException;
import io.unicid.registry.model.Identity;
import io.unicid.registry.model.Media;
import io.unicid.registry.model.PeerRegistry;
import io.unicid.registry.model.Session;
import io.unicid.registry.model.Token;
import io.unicid.registry.model.dts.Connection;
import io.unicid.registry.model.res.CreateRoomRequest;
import io.unicid.registry.model.res.webRtc.WebRtcCallData;
import io.unicid.registry.res.c.MediaResource;
import io.unicid.registry.res.c.Resource;
import io.unicid.registry.res.c.WebRtcResource;
import io.unicid.registry.utils.I18n;
import io.unicid.registry.utils.ServiceLabel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jgroups.util.Base64;

@ApplicationScoped
public class Service {

  private static Logger logger = Logger.getLogger(Service.class);

  @Inject EntityManager em;

  @Inject I18n i18n;

  @RestClient @Inject MediaResource mediaResource;

  @RestClient @Inject WebRtcResource webRtcResource;

  @RestClient @Inject MessageResource messageResource;

  @RestClient @Inject CredentialTypeResource credentialTypeResource;

  @Inject RegisterService registerService;

  @Inject VisionService visionService;

  @ConfigProperty(name = "io.unicid.debug")
  Boolean debug;

  @ConfigProperty(name = "io.unicid.create.token.lifetimeseconds")
  Long createTokenLifetimeSec;

  @ConfigProperty(name = "io.unicid.verify.token.lifetimeseconds")
  Long verifyTokenLifetimeSec;

  @ConfigProperty(name = "io.unicid.protection")
  Protection protection;

  @ConfigProperty(name = "io.unicid.vision.face.capture.url")
  String faceCaptureUrl;

  @ConfigProperty(name = "io.unicid.vision.face.verification.url")
  String faceVerificationUrl;

  @ConfigProperty(name = "io.unicid.vision.fingerprints.capture.url")
  String fingerprintsCaptureUrl;

  @ConfigProperty(name = "io.unicid.vision.fingerprints.verification.url")
  String fingerprintsVerificationUrl;

  @ConfigProperty(name = "io.unicid.identity.recoverable.seconds")
  Long identityRecoverableSeconds;

  @ConfigProperty(name = "io.unicid.auth.valid.for.minutes")
  Integer authenticationValidForMinutes;

  // how will be named the credential
  @ConfigProperty(name = "io.unicid.identity.def.name")
  String defName;

  @ConfigProperty(name = "io.unicid.identity.def.claim.avatarPic.maxdimension")
  Integer avatarMaxDim;

  @ConfigProperty(name = "io.unicid.vision.redirdomain")
  Optional<String> redirDomain;

  @ConfigProperty(name = "io.unicid.vision.redirdomain.q")
  Optional<String> qRedirDomain;

  @ConfigProperty(name = "io.unicid.vision.redirdomain.d")
  Optional<String> dRedirDomain;

  @ConfigProperty(name = "io.unicid.messages.welcome")
  String WELCOME;

  @ConfigProperty(name = "io.unicid.messages.welcome2")
  Optional<String> WELCOME2;

  @ConfigProperty(name = "io.unicid.messages.welcome3")
  Optional<String> WELCOME3;

  // private static HashMap<UUID, SessionData> sessions = new HashMap<UUID, SessionData>();
  private static CredentialType type = null;
  private static Object lockObj = new Object();
  private static DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  ObjectMapper objectMapper = new ObjectMapper();

  private String getMessage(String messageName, UUID connection) {
    Connection session = this.getConnection(connection);
    try {
      return i18n.getMessage(messageName, connection, session.getLanguage());
    } catch (Exception e) {
      logger.error("getMessage: error: " + messageName);
      return messageName;
    }
  }

  public String getConfigValue(String key) {
    return ConfigProvider.getConfig().getValue(key, String.class);
  }

  public BaseMessage getRootMenu(UUID connectionId, Session session, Identity identity) {

    ContextualMenuUpdate menu = new ContextualMenuUpdate();
    menu.setTitle(getMessage("ROOT_MENU_TITLE", connectionId));
    List<ContextualMenuItem> options = new ArrayList<ContextualMenuItem>();

    if ((session == null) || (session.getType() == null)) {
      // main menu

      menu.setDescription(getMessage("ROOT_MENU_NO_SELECTED_ID_DESCRIPTION", connectionId));
      int i = 0;

      if (i < 5) {
        // max 5 identities
        options.add(
            ContextualMenuItem.build(
                ServiceLabel.CMD_CREATE, getMessage("CMD_CREATE_LABEL", connectionId), null));
      }

    } else
      switch (session.getType()) {
        case EDIT:
        case ISSUE:
        case CREATE:
          {
            /* create menu */

            // abort and return to main menu
            String legacy = "off";
            if (session != null && session.getLegacy()) legacy = "on";
            if (session != null
                && session.getCreateStep() != null
                && !session.getCreateStep().equals(CreateStep.MRZ))
              options.add(
                  ContextualMenuItem.build(
                      ServiceLabel.CMD_CREATE_OLD,
                      getMessage("CMD_CREATE_OLD_LABEL", connectionId).replace("VALUE", legacy),
                      null));
            options.add(
                ContextualMenuItem.build(
                    ServiceLabel.CMD_CREATE_ABORT,
                    getMessage("CMD_CREATE_ABORT_LABEL", connectionId),
                    null));
            break;
          }
        default:
          {
            break;
          }
      }

    menu.setOptions(options);

    if (debug) {
      try {
        logger.info("getRootMenu: " + JsonUtil.serialize(menu, false));
      } catch (JsonProcessingException e) {
      }
    }
    menu.setConnectionId(connectionId);
    menu.setId(UUID.randomUUID());
    menu.setTimestamp(Instant.now());

    return menu;
  }

  private String getIdentityLabel(Identity identity) {
    StringBuffer idLabel = new StringBuffer(64);

    if (identity.getDeletedTs() != null) {
      idLabel.append("🧨");
    } else if (identity.getRevokedTs() != null) {
      idLabel.append("❌");
    } else if (identity.getIssuedTs() != null) {
      if (identity.getProtectedTs() == null) {
        idLabel.append("⚠️ (unprotected)");
      } else {
        idLabel.append("✅");
      }

    } else if (identity.getProtectedTs() != null) {
      idLabel.append("🔐");
    } else {
      idLabel.append("✏️️");
    }

    boolean name = false;
    if (identity.getFirstName() != null) {
      idLabel.append(" ").append(identity.getFirstName());
      name = true;
    }
    if (identity.getLastName() != null) {
      idLabel.append(" ").append(identity.getLastName());
      name = true;
    }
    if (identity.getAvatarName() != null) {
      idLabel.append(" ").append(identity.getAvatarName());
      name = true;
    }
    if (!name) {

      if (identity.getCitizenId() != null) {
        idLabel.append(" ").append(identity.getCitizenId());
      } else {
        idLabel.append(" ").append(" <unset name>");
      }
    }

    return idLabel.toString();
  }

  @Transactional
  public void userInput(BaseMessage message) throws Exception {

    String content = null;

    MediaMessage mm = null;

    if (message instanceof TextMessage) {

      TextMessage textMessage = (TextMessage) message;
      content = textMessage.getContent();

    } else if ((message instanceof ContextualMenuSelect)) {

      ContextualMenuSelect menuSelect = (ContextualMenuSelect) message;
      content = menuSelect.getSelectionId();

    } else if ((message instanceof MenuSelectMessage)) {

      MenuSelectMessage menuSelect = (MenuSelectMessage) message;
      content = menuSelect.getMenuItems().iterator().next().getId();
    } else if ((message instanceof MediaMessage)) {
      mm = (MediaMessage) message;
      content = "media";
    } else if ((message instanceof ProfileMessage)) {
      ProfileMessage profile = (ProfileMessage) message;
      this.updatePreferLanguage(profile);
      content = ServiceLabel.CMD_CREATE;
    } else if ((message instanceof MrzDataSubmitMessage)) {
      MrzDataSubmitMessage mrz = (MrzDataSubmitMessage) message;
      messageResource.sendMessage(
          TextMessage.build(
              message.getConnectionId(),
              null,
              getMessage("MRZ_SUCCESSFULL", message.getConnectionId())));
      content = JsonUtil.serialize(mrz, false);
    } else if ((message instanceof EMrtdDataSubmitMessage)) {
      EMrtdDataSubmitMessage emrtd = (EMrtdDataSubmitMessage) message;
      content = JsonUtil.serialize(emrtd, false);
    } else if ((message instanceof CredentialReceptionMessage)) {
      CredentialReceptionMessage crp = (CredentialReceptionMessage) message;
      switch (crp.getState()) {
        case DONE:
          messageResource.sendMessage(
              TextMessage.build(
                  message.getConnectionId(),
                  null,
                  getMessage("CREDENTIAL_ACCEPTED", message.getConnectionId())));
          content = ServiceLabel.CMD_DELETE;
          break;
        case DECLINED:
          messageResource.sendMessage(
              TextMessage.build(
                  message.getConnectionId(),
                  null,
                  getMessage("CREDENTIAL_REJECTED", message.getConnectionId())));
          content = ServiceLabel.CMD_EDIT_ABORT;
          break;
        default:
          break;
      }
    }

    if (content != null) {
      content = content.strip();
      if (content.length() == 0) content = null;
    }

    if (content == null) return;

    Session session = this.getSession(message.getConnectionId());
    Identity identity = null;

    if (session != null) {
      identity = session.getIdentity();

      try {
        logger.info("userInput: session: " + JsonUtil.serialize(session, false));
      } catch (JsonProcessingException e) {

      }
    }

    if (identity != null) {
      try {
        logger.info("userInput: identity: " + JsonUtil.serialize(identity, false));

      } catch (JsonProcessingException e) {

      }
    }

    if (content.equals(ServiceLabel.CMD_CREATE)) {
      logger.info("userInput: CMD_CREATE : session before: " + session);

      session = createSession(session, message.getConnectionId());
      session.setType(SessionType.CREATE);
      session = em.merge(session);

      this.createEntryPoint(message.getConnectionId(), message.getThreadId(), session, null, null);
    } else if (content.equals(ServiceLabel.CMD_CREATE_OLD)) {
      logger.info("userInput: CMD_CREATE_OLD : session before: " + session);

      if (session == null) session = createSession(session, message.getConnectionId());
      session.setType(SessionType.CREATE);
      session.setCreateStep(CreateStep.CAPTURE);
      session.setLegacy(!session.getLegacy());
      session = em.merge(session);

      this.createEntryPoint(message.getConnectionId(), message.getThreadId(), session, null, null);

    } else if (content.equals(ServiceLabel.CMD_CONTINUE_SETUP)) {
      logger.info("userInput: CMD_CONTINUE_SETUP : session before: " + session);

      if (session != null) {
        session.setType(SessionType.CREATE);

        session.setCreateStep(CreateStep.CAPTURE);
        session = em.merge(session);

        this.createEntryPoint(
            message.getConnectionId(), message.getThreadId(), session, null, null);
      }

      logger.info("userInput: CMD_CONTINUE_SETUP : session after: " + session);
    } else if (content.equals(ServiceLabel.CMD_CREATE_ABORT)) {
      logger.info("userInput: CMD_CREATE_ABORT : session before: " + session);

      this.deleteData(message.getConnectionId(), message.getThreadId(), session);
      logger.info("userInput: CMD_CREATE_ABORT : session after: " + session);
      messageResource.sendMessage(
          this.getConfirmData(message.getConnectionId(), message.getThreadId()));

    } else if (content.equals(ServiceLabel.CMD_VIEW_ID)) {
      logger.info("userInput: CMD_VIEW_ID : session before: " + session);
      if (this.isEditSession(session, identity)) {

        String idstr = this.getIdentityDataString(identity);
        messageResource.sendMessage(
            TextMessage.build(message.getConnectionId(), message.getThreadId(), idstr));
      } else {
        messageResource.sendMessage(
            TextMessage.build(
                message.getConnectionId(),
                message.getThreadId(),
                getMessage("ERROR_SELECT_IDENTITY_FIRST", message.getConnectionId())));
      }

    } else if (content.equals(ServiceLabel.CMD_UNDELETE)) {
      logger.info("userInput: CMD_UNDELETE : session before: " + session);
      if (this.isEditSession(session, identity)) {
        if (identity.getDeletedTs().plusSeconds(86400).compareTo(Instant.now()) > 0) {
          identity.setDeletedTs(null);
          identity = em.merge(identity);
          messageResource.sendMessage(
              TextMessage.build(
                  message.getConnectionId(),
                  message.getThreadId(),
                  getMessage("IDENTITY_UNDELETED", message.getConnectionId())
                      .replace("IDENTITY", this.getIdentityLabel(identity))));

        } else {
          messageResource.sendMessage(
              TextMessage.build(
                  message.getConnectionId(),
                  message.getThreadId(),
                  getMessage("ERROR_IDENTITY_UNDELETE", message.getConnectionId())
                      .replace("IDENTITY", this.getIdentityLabel(identity))));
        }
      } else {
        messageResource.sendMessage(
            TextMessage.build(
                message.getConnectionId(),
                message.getThreadId(),
                getMessage("ERROR_SELECT_IDENTITY_FIRST", message.getConnectionId())));
      }

    } else if (content.equals(ServiceLabel.CMD_EDIT_ABORT)) {
      logger.info("userInput: CMD_EDIT_ABORT : session before: " + session);
      if (session != null) {
        em.remove(session);
        session = null;
      }
      messageResource.sendMessage(
          TextMessage.build(
              message.getConnectionId(),
              message.getThreadId(),
              getMessage("IDENTITY_EDIT_ABORTED", message.getConnectionId())));

    } else if (content.equals(ServiceLabel.CMD_ISSUE)) {
      logger.info("userInput: CMD_ISSUE : session before: " + session);
      if (this.isEditSession(session, identity)) {

        session.setType(SessionType.ISSUE);
        session.setIdentity(identity);
        session = em.merge(session);
        this.issueEntryPoint(message.getConnectionId(), message.getThreadId(), session, null);

      } else {
        messageResource.sendMessage(
            TextMessage.build(
                message.getConnectionId(),
                message.getThreadId(),
                getMessage("ERROR_SELECT_IDENTITY_FIRST", message.getConnectionId())));
      }

    } else if (content.equals(ServiceLabel.CMD_ISSUE_ABORT)) {
      logger.info("userInput: CMD_ISSUE_ABORT : session before: " + session);
      if (session != null) {
        session.setType(SessionType.EDIT);
        session = em.merge(session);
      }
      messageResource.sendMessage(
          TextMessage.build(
              message.getConnectionId(),
              message.getThreadId(),
              getMessage("IDENTITY_ISSUANCE_ABORTED", message.getConnectionId())));
    } else if (content.equals(ServiceLabel.CMD_DEBUG)) {
      this.getToken(message.getConnectionId(), TokenType.WEBRTC_VERIFICATION, null, null);
      this.sendWebRTCCapture(message.getConnectionId(), message.getThreadId());
    } else if (content.equals(ServiceLabel.CMD_DELETE)) {
      logger.info("userInput: CMD_DELETE : session before: " + session);

      identity = this.deleteData(message.getConnectionId(), message.getThreadId(), session);
      session = null;

    } else if (content.equals(ServiceLabel.CMD_REVOKE)) {
      logger.info("userInput: CMD_REVOKE : session before: " + session);
      if (this.isEditSession(session, identity)) {
        if (identity.getIssuedTs() != null) {
          identity.setRevokedTs(Instant.now());
          identity = em.merge(identity);
          messageResource.sendMessage(
              TextMessage.build(
                  message.getConnectionId(),
                  message.getThreadId(),
                  getMessage("IDENTITY_REVOKED", message.getConnectionId())
                      .replace("IDENTITY", this.getIdentityLabel(identity))));
        }
      } else {
        messageResource.sendMessage(
            TextMessage.build(
                message.getConnectionId(),
                message.getThreadId(),
                getMessage("ERROR_SELECT_IDENTITY_FIRST", message.getConnectionId())));
      }

    } else if (content.equals(ServiceLabel.COMPLETE_IDENTITY_CONFIRM_YES_VALUE)) {
      this.userInput(
          TextMessage.build(
              message.getConnectionId(), message.getThreadId(), ServiceLabel.CMD_CREATE));
    } else if (content.equals(ServiceLabel.COMPLETE_IDENTITY_CONFIRM_NO_VALUE)) {
    } else if (this.isSessionType(session, SessionType.CREATE)) {
      logger.info("userInput: CREATE entryPoint session before: " + session);

      this.createEntryPoint(message.getConnectionId(), message.getThreadId(), session, content, mm);
    } else if (this.isSessionType(session, SessionType.RESTORE)) {
      logger.info("userInput: RESTORE entryPoint session before: " + session);

      this.restoreEntryPoint(message.getConnectionId(), message.getThreadId(), session, content);
    } else if (this.isSessionType(session, SessionType.ISSUE)) {
      logger.info("userInput: ISSUE entryPoint session before: " + session);

      this.issueEntryPoint(message.getConnectionId(), message.getThreadId(), session, content);
    } else {
      messageResource.sendMessage(
          TextMessage.build(
              message.getConnectionId(),
              message.getThreadId(),
              getMessage("HELP", message.getConnectionId())));
    }

    logger.info(
        "userInput: sending menu content: "
            + content
            + " session: "
            + session
            + " identity: "
            + ((session != null) ? session.getIdentity() : identity));

    messageResource.sendMessage(
        this.getRootMenu(
            message.getConnectionId(),
            session,
            ((session != null) ? session.getIdentity() : identity)));
  }

  private Identity deleteData(UUID connectionId, UUID threadId, Session session) {
    Identity identity = null;
    if (session != null) {
      if (session.getIdentity() != null) {
        try {
          Token token =
              this.getToken(connectionId, TokenType.WEBRTC_CAPTURE, session.getIdentity(), null);
          mediaResource.delete(session.getIdentity().getAvatarPic(), token.getId().toString());
          logger.info("deleteData: picture: " + session.getIdentity().getAvatarPic());
        } catch (Exception e) {
          logger.info("deleteData: No picture deleted");
        }
        session.getIdentity().clearFields();
        identity = em.merge(session.getIdentity());
      }
      em.remove(session);
      session = null;
    }
    messageResource.sendMessage(
        TextMessage.build(
            connectionId, threadId, getMessage("IDENTITY_CREATE_ABORTED", connectionId)));
    return identity;
  }

  private boolean isSessionType(Session session, SessionType issue) {
    return (session != null) && (session.getType() != null) && (session.getType().equals(issue));
  }

  private boolean isEditSession(Session session, Identity identity) {
    return (session != null)
        && (session.getType() != null)
        && (session.getType().equals(SessionType.EDIT))
        && (identity != null);
  }

  private void updatePreferLanguage(ProfileMessage profile) {
    Connection session = this.getConnection(profile.getConnectionId());
    session.setLanguage(profile.getPreferredLanguage());
    em.merge(session);

    // Send welcome message after
    // this.sendWelcomeMessages(profile.getConnectionId(), profile.getThreadId());
    messageResource.sendMessage(this.getRootMenu(profile.getConnectionId(), null, null));
  }

  private void sendWelcomeMessages(UUID connectionId, UUID threadId) {
    messageResource.sendMessage(
        TextMessage.build(connectionId, threadId, getMessage("WELCOME", connectionId)));
    if (WELCOME2.isPresent()) {
      messageResource.sendMessage(
          TextMessage.build(connectionId, threadId, getMessage("WELCOME2", connectionId)));
    }
    if (WELCOME3.isPresent()) {
      messageResource.sendMessage(
          TextMessage.build(connectionId, threadId, getMessage("WELCOME3", connectionId)));
    }
  }

  // ?token=TOKEN&d=D_DOMAIN&q=Q_DOMAIN
  private String buildVisionUrl(String url, UUID connection) {
    Connection session = this.getConnection(connection);

    if (redirDomain.isPresent()) {
      url = url + "&rd=" + redirDomain.get().replace("https://", "");
    }
    if (qRedirDomain.isPresent()) {
      url = url + "&q=" + qRedirDomain.get().replace("https://", "");
    }
    if (dRedirDomain.isPresent()) {
      url = url + "&d=" + dRedirDomain.get().replace("https://", "");
    }
    if (session.getLanguage() != null && !session.getLanguage().isEmpty()) {
      url = url + "&lang=" + session.getLanguage();
    }

    return url;
  }

  private BaseMessage generateFaceVerificationMediaMessage(
      UUID connectionId, UUID threadId, Token token) {
    String url = faceVerificationUrl.replaceFirst("TOKEN", token.getId().toString());
    url = this.buildVisionUrl(url, connectionId);

    MediaItem mi = new MediaItem();
    mi.setMimeType("text/html");
    mi.setUri(url);
    mi.setTitle(getMessage("FACE_VERIFICATION_HEADER", connectionId));
    mi.setDescription(getMessage("FACE_VERIFICATION_DESC", connectionId));
    mi.setOpeningMode("normal");
    List<MediaItem> mis = new ArrayList<MediaItem>();
    mis.add(mi);
    MediaMessage mm = new MediaMessage();
    mm.setConnectionId(connectionId);
    mm.setThreadId(threadId);
    mm.setDescription(getMessage("FACE_VERIFICATION_DESC", connectionId));
    mm.setItems(mis);
    return mm;
  }

  private BaseMessage generateFaceCaptureMediaMessage(
      UUID connectionId, UUID threadId, Token token) {
    String url = faceCaptureUrl.replaceFirst("TOKEN", token.getId().toString());
    url = this.buildVisionUrl(url, connectionId);
    MediaItem mi = new MediaItem();
    mi.setMimeType("text/html");
    mi.setUri(url);
    mi.setTitle(getMessage("FACE_CAPTURE_HEADER", connectionId));
    mi.setDescription(getMessage("FACE_CAPTURE_DESC", connectionId));
    mi.setOpeningMode("normal");
    List<MediaItem> mis = new ArrayList<MediaItem>();
    mis.add(mi);
    MediaMessage mm = new MediaMessage();
    mm.setConnectionId(connectionId);
    mm.setThreadId(threadId);
    mm.setDescription(getMessage("FACE_CAPTURE_DESC", connectionId));
    mm.setItems(mis);
    return mm;
  }

  private void restoreEntryPoint(UUID connectionId, UUID threadId, Session session, String content)
      throws Exception {

    if (session.getRestoreStep() == null) {
      session.setRestoreStep(getNextRestoreStep(null));
      session = em.merge(session);
      this.restoreSendMessage(connectionId, threadId, session);
    } else
      switch (session.getRestoreStep()) {
        case CITIZEN_ID:
          {
            if (content != null) {

              try {
                // parse long
                Long citizenId = Long.valueOf(content);
                if (citizenId < 0) citizenId = -citizenId;
                session.setRestoreStep(getNextRestoreStep(session.getRestoreStep()));
                session.setCitizenId(citizenId.toString());
                session = em.merge(session);
              } catch (Exception e) {
                logger.error("", e);
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId, threadId, getMessage("ID_ERROR", connectionId)));
              }
            }
            this.restoreSendMessage(connectionId, threadId, session);
            break;
          }

        case FIRST_NAME:
          {
            if (content != null) {
              session.setRestoreStep(getNextRestoreStep(session.getRestoreStep()));
              session.setFirstName(content);
              session = em.merge(session);
            }
            this.restoreSendMessage(connectionId, threadId, session);
            break;
          }
        case LAST_NAME:
          {
            if (content != null) {
              session.setRestoreStep(getNextRestoreStep(session.getRestoreStep()));
              session.setLastName(content);
              session = em.merge(session);
            }
            this.restoreSendMessage(connectionId, threadId, session);
            break;
          }

        case AVATAR_NAME:
          {
            if (content != null) {
              session.setRestoreStep(getNextRestoreStep(session.getRestoreStep()));
              session.setAvatarName(content);
              session = em.merge(session);
            }
            this.restoreSendMessage(connectionId, threadId, session);
            break;
          }

        case BIRTH_DATE:
          {
            if (content != null) {

              LocalDate birthDate = null;

              try {
                // parse date
                birthDate = LocalDate.from(df.parse(content));
                session.setBirthDate(birthDate);
                session.setRestoreStep(getNextRestoreStep(session.getRestoreStep()));
                session = em.merge(session);

                this.restoreSendMessage(connectionId, threadId, session);

              } catch (Exception e) {
                logger.error("", e);
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId, threadId, getMessage("BIRTH_DATE_ERROR", connectionId)));
                this.restoreSendMessage(connectionId, threadId, session);
              }

            } else {
              this.restoreSendMessage(connectionId, threadId, session);
            }

            break;
          }

        case PLACE_OF_BIRTH:
          {
            if (content != null) {
              session.setRestoreStep(getNextRestoreStep(session.getRestoreStep()));
              session.setPlaceOfBirth(content);
              session = em.merge(session);
            }

            this.restoreSendMessage(connectionId, threadId, session);

            break;
          }

        case MRZ:
          {
            if (content != null) {
              session.setRestoreStep(getNextRestoreStep(session.getRestoreStep()));
              session = em.merge(session);
            }
            this.restoreSendMessage(connectionId, threadId, session);
            break;
          }

        case FACE_VERIFICATION:
          {
            Token token =
                this.getToken(
                    connectionId, TokenType.FACE_VERIFICATION, session.getIdentity(), null);
            messageResource.sendMessage(
                generateFaceVerificationMediaMessage(connectionId, threadId, token));
            break;
          }

        case FINGERPRINT_VERIFICATION:
          {
            this.getToken(
                connectionId, TokenType.FINGERPRINT_VERIFICATION, session.getIdentity(), null);

            break;
          }
        case WEBRTC_VERIFICATION:
          {
            Token token =
                this.getToken(
                    connectionId, TokenType.WEBRTC_VERIFICATION, session.getIdentity(), null);
            if (session.getIdentity() != null && session.getIdentity().getLegacy()) {
              messageResource.sendMessage(
                  generateFaceVerificationMediaMessage(connectionId, threadId, token));
            } else {
              this.sendWebRTCCapture(connectionId, threadId);
            }

            break;
          }
        case PASSWORD:
          {
            if (content != null) {
              logger.info("restoreEntryPoint: password: " + content);
              String password = DigestUtils.sha256Hex(content);

              Identity identity = session.getIdentity();

              boolean restored = false;

              if (identity.getPassword() != null) {
                if (identity.getPassword().equals(password)) {

                  identity.setConnectionId(connectionId);
                  identity.setAuthenticatedTs(Instant.now());
                  identity = em.merge(identity);
                  messageResource.sendMessage(
                      TextMessage.build(
                          session.getConnectionId(),
                          null,
                          getMessage("AUTHENTICATION_SUCCESSFULL", connectionId)));
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId,
                          threadId,
                          getMessage("IDENTITY_RESTORED", connectionId)
                              .replaceFirst("IDENTITY", this.getIdentityLabel(identity))));
                  session = this.issueCredentialAndSetEditMenu(session, identity);

                  restored = true;
                }
              }

              if (debug) {
                logger.info(
                    "entryPointRestore: finding Identity with firstName: "
                        + session.getFirstName()
                        + " lastName: "
                        + session.getLastName()
                        + " birthDate: "
                        + session.getBirthDate()
                        + " password: "
                        + password);
              }

              if (!restored) {
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId, threadId, getMessage("IDENTITY_NOT_FOUND", connectionId)));
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId, threadId, getMessage("RESTORE_PASSWORD", connectionId)));
              }

            } else {
              this.restoreSendMessage(connectionId, threadId, session);
            }
            break;
          }
      }

    if ((session.getRestoreStep() != null) && (session.getRestoreStep().equals(RestoreStep.DONE))) {

      Query q = em.createQuery(this.queryDuplicatePredicate(session));

      Identity res = (Identity) q.getResultList().stream().findFirst().orElse(null);

      if (res != null) {
        if (res.getConnectionId() != null) {
          if (res.getConnectionId().equals(connectionId)) {
            res = null;
          }
        }
      }
      if (res == null) {
        messageResource.sendMessage(
            TextMessage.build(
                connectionId, threadId, getMessage("IDENTITY_NOT_FOUND", connectionId)));
        this.purgeSession(session);
        session.setType(SessionType.RESTORE);
        session.setRestoreStep(getNextRestoreStep(null));
        session = em.merge(session);
        this.restoreSendMessage(connectionId, threadId, session);
      } else {
        res.setConnectionId(connectionId);
        session.setConnectionId(connectionId);
        session.setIdentity(res);
        em.merge(res);
        switch (res.getProtection()) {
          case PASSWORD:
            {
              logger.info(
                  "restoreEntryPoint: found password method for identity "
                      + this.getIdentityDataString(res));
              session.setRestoreStep(RestoreStep.PASSWORD);
              this.restoreSendMessage(connectionId, threadId, session);
              break;
            }

          case FACE:
            {
              logger.info(
                  "restoreEntryPoint: found face verification method for identity "
                      + this.getIdentityDataString(res));

              session.setRestoreStep(RestoreStep.FACE_VERIFICATION);

              Token token = this.getToken(connectionId, TokenType.FACE_VERIFICATION, res, null);

              messageResource.sendMessage(
                  generateFaceVerificationMediaMessage(connectionId, threadId, token));

              logger.info("restoreEntryPoint: session: " + JsonUtil.serialize(session, false));

              break;
            }

          case FINGERPRINTS:
            {
              logger.info(
                  "restoreEntryPoint: found fingerprint verification method for identity "
                      + this.getIdentityDataString(res));

              session.setRestoreStep(RestoreStep.FINGERPRINT_VERIFICATION);

              this.getToken(connectionId, TokenType.FINGERPRINT_VERIFICATION, res, null);

              break;
            }
          case WEBRTC:
            {
              logger.info(
                  "restoreEntryPoint: found webrtc verification method for identity "
                      + this.getIdentityDataString(res));

              session.setRestoreStep(RestoreStep.WEBRTC_VERIFICATION);

              Token token = this.getToken(connectionId, TokenType.WEBRTC_VERIFICATION, res, null);
              if (res.getLegacy()) {
                messageResource.sendMessage(
                    generateFaceVerificationMediaMessage(connectionId, threadId, token));
              } else {
                this.sendWebRTCCapture(connectionId, threadId);
              }

              break;
            }
        }
      }
      session = em.merge(session);
    }
  }

  private void restoreSendMessage(UUID connectionId, UUID threadId, Session session)
      throws Exception {

    if (session.getRestoreStep() == null) {

      messageResource.sendMessage(
          TextMessage.build(
              connectionId, threadId, getMessage("RESTORE_FIRST_NAME", connectionId)));
    } else
      switch (session.getRestoreStep()) {
        case CITIZEN_ID:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("RESTORE_CITIZEN_ID", connectionId)));
            break;
          }

        case FIRST_NAME:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("RESTORE_FIRST_NAME", connectionId)));
            break;
          }
        case LAST_NAME:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("RESTORE_LAST_NAME", connectionId)));
            break;
          }
        case AVATAR_NAME:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("RESTORE_AVATAR_NAME", connectionId)));
            break;
          }
        case BIRTH_DATE:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("RESTORE_BIRTH_DATE", connectionId)));
            break;
          }
        case PLACE_OF_BIRTH:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("RESTORE_PLACE_OF_BIRTH", connectionId)));
            break;
          }
        case MRZ:
          {
            messageResource.sendMessage(
                TextMessage.build(connectionId, threadId, getMessage("MRZ_REQUEST", connectionId)));
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("WEBRTC_REQUIRED", connectionId)));
            messageResource.sendMessage(MrzDataRequestMessage.build(connectionId, threadId));
            break;
          }

        case PASSWORD:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("RESTORE_PASSWORD", connectionId)));
            break;
          }

        case FACE_VERIFICATION:
        case FINGERPRINT_VERIFICATION:
        case WEBRTC_VERIFICATION:
        default:
          {
            break;
          }
      }
  }

  private boolean identityAlreadyExists(Session session) {
    Query q = em.createQuery(this.queryDuplicatePredicate(session));

    List<Identity> founds = q.getResultList();
    loggerInfoSerializeObject("identityAlreadyExists: found: ", founds);
    return (founds.size() > 0);
  }

  private CriteriaQuery<Identity> queryDuplicatePredicate(Session session) {

    CriteriaBuilder builder = em.getCriteriaBuilder();
    CriteriaQuery<Identity> query = builder.createQuery(Identity.class);
    Root<Identity> root = query.from(Identity.class);

    List<Predicate> allPredicates = new ArrayList<Predicate>();
    Instant deletedTs = Instant.now().minusSeconds(identityRecoverableSeconds);
    Predicate isDeletedNull = builder.isNull(root.get("deletedTs"));
    Predicate isDeletedGreaterThan = builder.greaterThan(root.get("deletedTs"), deletedTs);
    Predicate deletedPredicate = builder.or(isDeletedNull, isDeletedGreaterThan);

    Predicate predicate = builder.equal(root.get("mrz"), session.getMrz());
    allPredicates.add(predicate);
    loggerInfoSerializeObject("identityAlreadyExists: mrz: ", session.getMrz());

    allPredicates.add(deletedPredicate);
    query.where(builder.and(allPredicates.toArray(new Predicate[allPredicates.size()])));

    query.orderBy(builder.desc(root.get("id")));
    return query;
  }

  private CreateStep getNextCreateStep(CreateStep current) throws Exception {

    if (current == null) return CreateStep.MRZ;
    throw new Exception("no claim has been enabled");
  }

  private RestoreStep getNextRestoreStep(RestoreStep current) throws Exception {

    if (current == null) return RestoreStep.MRZ;
    else {
      switch (current) {
        case MRZ:
        default:
          {
            return RestoreStep.DONE;
          }
      }
    }
  }

  private void createEntryPoint(
      UUID connectionId, UUID threadId, Session session, String content, MediaMessage mm)
      throws Exception {

    if (session.getCreateStep() == null) {
      session.setCreateStep(getNextCreateStep(null));
      session = em.merge(session);
      this.createSendMessage(connectionId, threadId, session);

    } else
      switch (session.getCreateStep()) {
        case CITIZEN_ID:
          {
            if (content != null) {

              try {
                // parse long
                Long citizenId = Long.valueOf(content);
                if (citizenId < 0) citizenId = -citizenId;
                session.setCreateStep(getNextCreateStep(session.getCreateStep()));
                session.setCitizenId(citizenId.toString());

              } catch (Exception e) {
                logger.error("", e);
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId, threadId, getMessage("ID_ERROR", connectionId)));
              }
              if (session.getCreateStep().equals(CreateStep.PENDING_CONFIRM)) {

                if (this.identityAlreadyExists(session)) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId,
                          threadId,
                          getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                  if (session.getAvatarPic() == null) {
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, this.getSessionDataString(session)));
                  } else {
                    MediaMessage mms =
                        this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                    messageResource.sendMessage(mms);
                  }

                  session.setCreateStep(CreateStep.NEED_TO_CHANGE);
                }
              }
              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }
        case FIRST_NAME:
          {
            if (content != null) {
              session.setCreateStep(getNextCreateStep(session.getCreateStep()));
              session.setFirstName(content);
              if (session.getCreateStep().equals(CreateStep.PENDING_CONFIRM)) {

                if (this.identityAlreadyExists(session)) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId,
                          threadId,
                          getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                  if (session.getAvatarPic() == null) {
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, this.getSessionDataString(session)));
                  } else {
                    MediaMessage mms =
                        this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                    messageResource.sendMessage(mms);
                  }
                  session.setCreateStep(CreateStep.NEED_TO_CHANGE);
                }
              }
              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }
        case LAST_NAME:
          {
            if (content != null) {
              session.setCreateStep(getNextCreateStep(session.getCreateStep()));
              session.setLastName(content);
              if (session.getCreateStep().equals(CreateStep.PENDING_CONFIRM)) {

                if (this.identityAlreadyExists(session)) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId,
                          threadId,
                          getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                  if (session.getAvatarPic() == null) {
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, this.getSessionDataString(session)));
                  } else {
                    MediaMessage mms =
                        this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                    messageResource.sendMessage(mms);
                  }
                  session.setCreateStep(CreateStep.NEED_TO_CHANGE);
                }
              }
              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }
        case AVATAR_NAME:
          {
            if (content != null) {
              session.setCreateStep(getNextCreateStep(session.getCreateStep()));
              session.setAvatarName(content);

              if (session.getCreateStep().equals(CreateStep.PENDING_CONFIRM)) {

                if (this.identityAlreadyExists(session)) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId,
                          threadId,
                          getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                  if (session.getAvatarPic() == null) {
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, this.getSessionDataString(session)));
                  } else {
                    MediaMessage mms =
                        this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                    messageResource.sendMessage(mms);
                  }
                  session.setCreateStep(CreateStep.NEED_TO_CHANGE);
                }
              }
              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }
        case AVATAR_PIC:
          {
            if (mm != null) {

              this.saveAvatarPicture(mm, session);

              if (session.getAvatarPic() != null) {
                session.setCreateStep(getNextCreateStep(session.getCreateStep()));

                if (session.getCreateStep().equals(CreateStep.PENDING_CONFIRM)) {

                  if (this.identityAlreadyExists(session)) {
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId,
                            threadId,
                            getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                    if (session.getAvatarPic() == null) {
                      messageResource.sendMessage(
                          TextMessage.build(
                              connectionId, threadId, this.getSessionDataString(session)));
                    } else {
                      MediaMessage mms =
                          this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                      messageResource.sendMessage(mms);
                    }
                    session.setCreateStep(CreateStep.NEED_TO_CHANGE);
                  }
                }
                session = em.merge(session);
              }
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }
        case BIRTH_DATE:
          {
            if (content != null) {

              LocalDate birthDate = null;

              try {
                // parse date
                birthDate = LocalDate.from(df.parse(content));
                session.setBirthDate(birthDate);
                session.setCreateStep(getNextCreateStep(session.getCreateStep()));
                session = em.merge(session);
              } catch (Exception e) {
                logger.error("", e);
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId, threadId, getMessage("BIRTH_DATE_ERROR", connectionId)));
              }

              if (session.getCreateStep().equals(CreateStep.PENDING_CONFIRM)) {

                if (this.identityAlreadyExists(session)) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId,
                          threadId,
                          getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                  if (session.getAvatarPic() == null) {
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, this.getSessionDataString(session)));
                  } else {
                    MediaMessage mms =
                        this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                    messageResource.sendMessage(mms);
                  }
                  session.setCreateStep(CreateStep.NEED_TO_CHANGE);
                }
              }
              session = em.merge(session);
            }

            this.createSendMessage(connectionId, threadId, session);
            break;
          }
        case PLACE_OF_BIRTH:
          {
            if (content != null) {
              session.setPlaceOfBirth(content);
              session.setCreateStep(getNextCreateStep(session.getCreateStep()));

              if (session.getCreateStep().equals(CreateStep.PENDING_CONFIRM)) {

                if (this.identityAlreadyExists(session)) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId,
                          threadId,
                          getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                  if (session.getAvatarPic() == null) {
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, this.getSessionDataString(session)));
                  } else {
                    MediaMessage mms =
                        this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                    messageResource.sendMessage(mms);
                  }
                  session.setCreateStep(CreateStep.NEED_TO_CHANGE);
                }
              }

              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }
        case MRZ:
          {
            if (content != null) {
              MrzDataSubmitMessage mrz =
                  objectMapper.readValue(content, MrzDataSubmitMessage.class);
              this.getToken(
                  connectionId, TokenType.WEBRTC_CAPTURE, session.getIdentity(), mrz.getThreadId());

              Identity identity = this.setAvatarPictureData(null, session);
              em.persist(identity);
              session.setCreateStep(CreateStep.CAPTURE);
              this.createEntryPoint(connectionId, threadId, session, null, null);
              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }

        case PENDING_CONFIRM:
          {
            Identity identity = null;
            if (content != null) {
              if (content.equals(ServiceLabel.COMPLETE_IDENTITY_CONFIRM_YES_VALUE)) {

                if (!this.identityAlreadyExists(session)) {
                  session.setConnectionId(connectionId);
                  identity = this.setAvatarPictureData(identity, session);
                  em.persist(identity);
                  session = em.merge(session);
                }

                session.setCreateStep(CreateStep.CAPTURE);
                session = em.merge(session);

              } else if (content.equals(ServiceLabel.COMPLETE_IDENTITY_CONFIRM_NO_VALUE)) {
                session.setCreateStep(CreateStep.WANT_TO_CHANGE);
                session = em.merge(session);
                if (session.getAvatarPic() == null) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId, threadId, this.getSessionDataString(session)));
                } else {
                  MediaMessage mms =
                      this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                  messageResource.sendMessage(mms);
                }
                this.createSendMessage(connectionId, threadId, session);
                break;
              } else {
                this.createSendMessage(connectionId, threadId, session);
                break;
              }
            } else {
              this.createSendMessage(connectionId, threadId, session);
              break;
            }
          }
        case CAPTURE:
          {
            Identity identity = session.getIdentity();
            if ((identity != null) && (identity.getProtectedTs() == null)) {

              switch (protection) {
                case PASSWORD:
                  {
                    session.setCreateStep(CreateStep.PASSWORD);
                    session = em.merge(session);

                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, getMessage("PASSWORD_REQUEST", connectionId)));

                    break;
                  }
                case FACE:
                  {
                    session.setCreateStep(CreateStep.FACE_CAPTURE);
                    session = em.merge(session);

                    Token token =
                        this.getToken(
                            connectionId, TokenType.FACE_CAPTURE, session.getIdentity(), null);
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId,
                            threadId,
                            getMessage("FACE_CAPTURE_REQUIRED", connectionId)));
                    messageResource.sendMessage(
                        generateFaceCaptureMediaMessage(connectionId, threadId, token));
                    break;
                  }
                case FINGERPRINTS:
                  {
                    session.setCreateStep(CreateStep.FINGERPRINT_CAPTURE);
                    session = em.merge(session);

                    this.getToken(
                        connectionId, TokenType.FINGERPRINT_CAPTURE, session.getIdentity(), null);
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId,
                            threadId,
                            getMessage("FINGERPRINT_CAPTURE_REQUIRED", connectionId)));

                    break;
                  }
                case WEBRTC:
                  {
                    session.setCreateStep(CreateStep.WEBRTC_CAPTURE);
                    session = em.merge(session);

                    Token mrzToken =
                        this.getToken(
                            connectionId, TokenType.WEBRTC_CAPTURE, session.getIdentity(), null);
                    messageResource.sendMessage(
                        EMrtdDataRequestMessage.build(connectionId, mrzToken.getThreadId()));
                    break;
                  }
              }

            } else {
              this.purgeSession(session);
            }

            break;
          }
        case WANT_TO_CHANGE:
        case NEED_TO_CHANGE:
          {
            if (content != null) {
              if (content.equals(IdentityClaim.CITIZEN_ID.toString())) {
                session.setCreateStep(CreateStep.CHANGE_CITIZEN_ID);
              } else if (content.equals(IdentityClaim.FIRST_NAME.toString())) {
                session.setCreateStep(CreateStep.CHANGE_FIRST_NAME);
              } else if (content.equals(IdentityClaim.LAST_NAME.toString())) {
                session.setCreateStep(CreateStep.CHANGE_LAST_NAME);
              } else if (content.equals(IdentityClaim.AVATAR_NAME.toString())) {
                session.setCreateStep(CreateStep.CHANGE_AVATAR_NAME);
              } else if (content.equals(IdentityClaim.AVATAR_PIC.toString())) {
                session.setCreateStep(CreateStep.CHANGE_AVATAR_PIC);
              } else if (content.equals(IdentityClaim.BIRTH_DATE.toString())) {
                session.setCreateStep(CreateStep.CHANGE_BIRTH_DATE);
              } else if (content.equals(IdentityClaim.PLACE_OF_BIRTH.toString())) {
                session.setCreateStep(CreateStep.CHANGE_PLACE_OF_BIRTH);
              } else if (content.equals(IdentityClaim.MRZ.toString())) {
                session.setCreateStep(CreateStep.CHANGE_MRZ);
              }
              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);

            break;
          }

        case CHANGE_CITIZEN_ID:
          {
            if (content != null) {

              if (content != null) {

                try {
                  // parse long
                  Long citizenId = Long.valueOf(content);
                  if (citizenId < 0) citizenId = -citizenId;
                  session.setCitizenId(citizenId.toString());
                } catch (Exception e) {
                  logger.error("", e);
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId, threadId, getMessage("ID_ERROR", connectionId)));
                }
              }

              if (this.identityAlreadyExists(session)) {
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId,
                        threadId,
                        getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                if (session.getAvatarPic() == null) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId, threadId, this.getSessionDataString(session)));
                } else {
                  MediaMessage mms =
                      this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                  messageResource.sendMessage(mms);
                }
                session.setCreateStep(CreateStep.NEED_TO_CHANGE);
              } else {
                session.setCreateStep(CreateStep.PENDING_CONFIRM);
              }

              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }

        case CHANGE_FIRST_NAME:
          {
            if (content != null) {
              session.setCreateStep(CreateStep.PENDING_CONFIRM);
              session.setFirstName(content);

              if (this.identityAlreadyExists(session)) {
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId,
                        threadId,
                        getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                if (session.getAvatarPic() == null) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId, threadId, this.getSessionDataString(session)));
                } else {
                  MediaMessage mms =
                      this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                  messageResource.sendMessage(mms);
                }
                session.setCreateStep(CreateStep.NEED_TO_CHANGE);
              } else {
                session.setCreateStep(CreateStep.PENDING_CONFIRM);
              }

              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }

        case CHANGE_LAST_NAME:
          {
            if (content != null) {
              session.setCreateStep(CreateStep.PENDING_CONFIRM);
              session.setLastName(content);

              if (this.identityAlreadyExists(session)) {
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId,
                        threadId,
                        getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                if (session.getAvatarPic() == null) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId, threadId, this.getSessionDataString(session)));
                } else {
                  MediaMessage mms =
                      this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                  messageResource.sendMessage(mms);
                }
                session.setCreateStep(CreateStep.NEED_TO_CHANGE);
              } else {
                session.setCreateStep(CreateStep.PENDING_CONFIRM);
              }

              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }
        case CHANGE_AVATAR_NAME:
          {
            if (content != null) {
              session.setCreateStep(CreateStep.PENDING_CONFIRM);
              session.setAvatarName(content);

              if (this.identityAlreadyExists(session)) {
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId,
                        threadId,
                        getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                if (session.getAvatarPic() == null) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId, threadId, this.getSessionDataString(session)));
                } else {
                  MediaMessage mms =
                      this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                  messageResource.sendMessage(mms);
                }
                session.setCreateStep(CreateStep.NEED_TO_CHANGE);
              } else {
                session.setCreateStep(CreateStep.PENDING_CONFIRM);
              }

              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }

        case CHANGE_AVATAR_PIC:
          {
            if (mm != null) {

              this.saveAvatarPicture(mm, session);

              if (session.getAvatarPic() != null) {
                session.setCreateStep(CreateStep.PENDING_CONFIRM);

                if (this.identityAlreadyExists(session)) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId,
                          threadId,
                          getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                  if (session.getAvatarPic() == null) {
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, this.getSessionDataString(session)));
                  } else {
                    MediaMessage mms =
                        this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                    messageResource.sendMessage(mms);
                  }

                  session.setCreateStep(CreateStep.NEED_TO_CHANGE);
                } else {
                  session.setCreateStep(CreateStep.PENDING_CONFIRM);
                }
                session = em.merge(session);
              }
            }

            this.createSendMessage(connectionId, threadId, session);
            break;
          }

        case CHANGE_BIRTH_DATE:
          {
            if (content != null) {

              LocalDate birthDate = null;

              try {
                // parse date
                birthDate = LocalDate.from(df.parse(content));
                session.setBirthDate(birthDate);

                if (this.identityAlreadyExists(session)) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId,
                          threadId,
                          getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                  if (session.getAvatarPic() == null) {
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, this.getSessionDataString(session)));
                  } else {
                    MediaMessage mms =
                        this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                    messageResource.sendMessage(mms);
                  }
                  session.setCreateStep(CreateStep.NEED_TO_CHANGE);
                } else {
                  session.setCreateStep(CreateStep.PENDING_CONFIRM);
                }

                session = em.merge(session);

              } catch (Exception e) {
                logger.error("", e);
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId, threadId, getMessage("BIRTH_DATE_ERROR", connectionId)));
              }
            }

            this.createSendMessage(connectionId, threadId, session);

            break;
          }
        case CHANGE_PLACE_OF_BIRTH:
          {
            if (content != null) {

              session.setPlaceOfBirth(content);

              if (this.identityAlreadyExists(session)) {
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId,
                        threadId,
                        getMessage("IDENTITY_CREATE_ERROR_DUPLICATE_IDENTITY", connectionId)));
                if (session.getAvatarPic() == null) {
                  messageResource.sendMessage(
                      TextMessage.build(
                          connectionId, threadId, this.getSessionDataString(session)));
                } else {
                  MediaMessage mms =
                      this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
                  messageResource.sendMessage(mms);
                }
                session.setCreateStep(CreateStep.NEED_TO_CHANGE);
              } else {
                session.setCreateStep(CreateStep.PENDING_CONFIRM);
              }
              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);

            break;
          }
        case CHANGE_MRZ:
          {
            if (content != null) {
              MrzDataSubmitMessage mrz =
                  objectMapper.readValue(content, MrzDataSubmitMessage.class);
              this.getToken(
                  connectionId, TokenType.WEBRTC_CAPTURE, session.getIdentity(), mrz.getThreadId());

              Identity identity = this.setAvatarPictureData(null, session);
              em.persist(identity);
              session.setCreateStep(CreateStep.CAPTURE);
              this.createEntryPoint(connectionId, threadId, session, null, null);
              session = em.merge(session);
            }
            this.createSendMessage(connectionId, threadId, session);
            break;
          }

        case PASSWORD:
          {
            if (content != null) {

              logger.info("createEntryPoint: password: " + content);

              Identity identity = session.getIdentity();
              identity.setPassword(DigestUtils.sha256Hex(content));
              identity.setProtectedTs(Instant.now());
              identity.setProtection(Protection.PASSWORD);
              em.persist(identity);

              messageResource.sendMessage(
                  TextMessage.build(
                      connectionId, threadId, getMessage("PASSWORD_CONFIRM", connectionId)));

              this.purgeSession(session);
              session.setType(SessionType.ISSUE);
              session.setIdentity(identity);
              session = em.merge(session);
              this.issueEntryPoint(connectionId, threadId, session, content);

            } else {
              messageResource.sendMessage(
                  TextMessage.build(
                      connectionId, threadId, getMessage("PASSWORD_REQUEST", connectionId)));
            }
            break;
          }
        case FACE_CAPTURE:
          {
            Token token =
                this.getToken(connectionId, TokenType.FACE_CAPTURE, session.getIdentity(), null);

            messageResource.sendMessage(
                generateFaceCaptureMediaMessage(connectionId, threadId, token));

            break;
          }
        case FINGERPRINT_CAPTURE:
          {
            this.getToken(connectionId, TokenType.FINGERPRINT_CAPTURE, session.getIdentity(), null);

            break;
          }
        case WEBRTC_CAPTURE:
          {
            Token token =
                this.getToken(connectionId, TokenType.WEBRTC_CAPTURE, session.getIdentity(), null);
            EMrtdDataSubmitMessage emrtd =
                objectMapper.readValue(content, EMrtdDataSubmitMessage.class);
            session.updateSessionWithData(emrtd.getDataGroups(), session);
            this.saveJp2Picture(
                emrtd.getDataGroups().getProcessed().getFaceImages().get(0), session, token);
            if (session != null && session.getAvatarPic() != null) {
              em.merge(this.setAvatarPictureData(session.getIdentity(), session));
              this.notifySuccess(token);
            }

            break;
          }

        default:
          break;
      }
  }

  private Identity setAvatarPictureData(Identity identity, Session session) {
    if (identity == null) {
      identity = new Identity();
      identity.setId(UUID.randomUUID());
      identity.setCitizenSinceTs(Instant.now());
    }
    identity.setCitizenId(session.getCitizenId());
    identity.setAvatarName(session.getAvatarName());
    identity.setMrz(session.getMrz());
    identity.setConnectionId(session.getConnectionId());
    identity.setProtection(protection);

    identity.setFirstName(session.getFirstName());
    identity.setLastName(session.getLastName());
    identity.setBirthDate(session.getBirthDate());
    identity.setPlaceOfBirth(session.getPlaceOfBirth());
    identity.setDocumentType(session.getDocumentType());
    identity.setDocumentNumber(session.getDocumentNumber());
    identity.setLegacy(session.getLegacy());

    identity.setAvatarPic(session.getAvatarPic());
    identity.setIsAvatarPicCiphered(session.getIsAvatarPicCiphered());
    identity.setAvatarPicCiphAlg(session.getAvatarPicCiphAlg());
    identity.setAvatarPicCiphIv(session.getAvatarPicCiphIv());
    identity.setAvatarPicCiphKey(session.getAvatarPicCiphKey());
    identity.setAvatarMimeType(session.getAvatarMimeType());

    session.setIdentity(identity);
    return identity;
  }

  private CallOfferRequestMessage generateOfferMessage(
      UUID connectionId, UUID threadId, Map<String, Object> wsUrlMap) {
    CallOfferRequestMessage co = new CallOfferRequestMessage();
    co.setConnectionId(connectionId);
    co.setId(UUID.randomUUID());
    co.setThreadId(threadId);
    co.setParameters(wsUrlMap);
    try {
      logger.info("generateOfferMessage: " + JsonUtil.serialize(co, false));
    } catch (JsonProcessingException e) {

    }
    return co;
  }

  private MediaMessage buildSessionIdentityMediaMessage(
      UUID connectionId, UUID threadId, Session session) {
    MediaMessage mms = new MediaMessage();
    mms.setConnectionId(connectionId);
    mms.setThreadId(threadId);
    mms.setTimestamp(Instant.now());
    mms.setDescription(this.getSessionDataString(session));
    List<MediaItem> items = new ArrayList<MediaItem>();
    MediaItem item = new MediaItem();
    item.setMimeType(session.getAvatarMimeType());
    item.setUri(session.getAvatarURI());
    Ciphering c = new Ciphering();
    c.setAlgorithm(session.getAvatarPicCiphAlg());
    Parameters p = new Parameters();
    p.setIv(session.getAvatarPicCiphIv());
    p.setKey(session.getAvatarPicCiphKey());
    c.setParameters(p);
    item.setCiphering(c);
    item.setDescription(mms.getDescription());
    items.add(item);
    mms.setItems(items);
    return mms;
  }

  private Token getToken(UUID connectionId, TokenType type, Identity identity, UUID threadId) {

    Query q = em.createNamedQuery("Token.findForConnection");
    q.setParameter("connectionId", connectionId);
    q.setParameter("type", type);
    Token token = (Token) q.getResultList().stream().findFirst().orElse(null);

    if (token == null) {
      token = new Token();
      token.setConnectionId(connectionId);
      token.setId(UUID.randomUUID());
      token.setType(type);
      token.setIdentity(identity);
      token.setThreadId(threadId);
      switch (type) {
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
    } else {
      token.setConnectionId(connectionId);
      token.setType(type);
      token.setIdentity(identity);
      if (threadId != null) token.setThreadId(threadId);
      switch (type) {
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
      token = em.merge(token);
    }

    return token;
  }

  private void sendWebRTCCapture(UUID connectionId, UUID threadId) {

    CreateRoomRequest request = new CreateRoomRequest(qRedirDomain.get() + "/call-event", 50);
    WebRtcCallData wsUrl = webRtcResource.createRoom(UUID.randomUUID(), request);
    UUID peerId = UUID.randomUUID();
    Map<String, Object> wsUrlMap = objectMapper.convertValue(wsUrl, Map.class);
    wsUrlMap.put("peerId", peerId);

    try {
      logger.info("webRtcResource: createRoom: " + JsonUtil.serialize(wsUrlMap, false));
    } catch (JsonProcessingException e) {

    }

    // Create registry
    PeerRegistry cr = em.find(PeerRegistry.class, peerId);
    if (cr == null) {
      cr = new PeerRegistry();
      cr.setId(peerId);
      cr.setConnectionId(connectionId);
      cr.setRoomId(wsUrl.getRoomId());
      cr.setWsUrl(wsUrl.getWsUrl());
      cr.setType(PeerType.PEER_USER);
      em.persist(cr);
    }

    messageResource.sendMessage(
        TextMessage.build(connectionId, null, getMessage("MRZ_FACE_VERIFICATION", connectionId)));
    messageResource.sendMessage(this.generateOfferMessage(connectionId, threadId, wsUrlMap));
  }

  private BaseMessage getWhichToChangeUserRequested(UUID connectionId, UUID threadId) {
    List<MenuItem> menuItems = new ArrayList<MenuItem>();

    MenuDisplayMessage confirm = new MenuDisplayMessage();
    confirm.setPrompt(getMessage("CHANGE_CLAIM_TITLE", connectionId));

    this.setChangeMenuItem(IdentityClaim.MRZ, menuItems);

    confirm.setConnectionId(connectionId);
    confirm.setThreadId(threadId);
    confirm.setMenuItems(menuItems);
    return confirm;
  }

  private BaseMessage getWhichToChangeNeeded(UUID connectionId, UUID threadId) {
    List<MenuItem> menuItems = new ArrayList<MenuItem>();

    MenuDisplayMessage confirm = new MenuDisplayMessage();
    confirm.setPrompt(getMessage("CONFLICTIVE_CLAIM_TITLE", connectionId));

    this.setChangeMenuItem(IdentityClaim.MRZ, menuItems);

    confirm.setConnectionId(connectionId);
    confirm.setThreadId(threadId);
    confirm.setMenuItems(menuItems);
    return confirm;
  }

  private void setChangeMenuItem(IdentityClaim claim, List<MenuItem> menuItems) {
    MenuItem menu = new MenuItem();
    menu.setId(claim.toString());
    menu.setText(claim.getClaimLabel());
    menuItems.add(menu);
  }

  private BaseMessage getConfirmData(UUID connectionId, UUID threadId) {

    MenuDisplayMessage confirm = new MenuDisplayMessage();
    confirm.setPrompt(getMessage("COMPLETE_IDENTITY_CONFIRM_TITLE", connectionId));

    MenuItem yes = new MenuItem();
    yes.setId(ServiceLabel.COMPLETE_IDENTITY_CONFIRM_YES_VALUE);
    yes.setText(getMessage("COMPLETE_IDENTITY_CONFIRM_YES", connectionId));

    MenuItem no = new MenuItem();
    no.setId(ServiceLabel.COMPLETE_IDENTITY_CONFIRM_NO_VALUE);
    no.setText(getMessage("COMPLETE_IDENTITY_CONFIRM_NO", connectionId));

    List<MenuItem> menuItems = new ArrayList<MenuItem>();
    menuItems.add(yes);
    menuItems.add(no);

    confirm.setMenuItems(menuItems);

    confirm.setConnectionId(connectionId);
    confirm.setThreadId(threadId);

    return confirm;
  }

  private void createSendMessage(UUID connectionId, UUID threadId, Session session)
      throws Exception {
    CreateStep step = session.getCreateStep();
    if (session.getCreateStep() == null) {
      this.sendWelcomeMessages(connectionId, threadId);

      // should not occur never
      if (step == null) {
        step = this.getNextCreateStep(null);
      }
    } else
      switch (step) {
        case CITIZEN_ID:
        case CHANGE_CITIZEN_ID:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("CITIZEN_ID_REQUEST", connectionId)));
            break;
          }

        case FIRST_NAME:
        case CHANGE_FIRST_NAME:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("FIRST_NAME_REQUEST", connectionId)));
            break;
          }
        case LAST_NAME:
        case CHANGE_LAST_NAME:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("LAST_NAME_REQUEST", connectionId)));
            break;
          }
        case AVATAR_NAME:
        case CHANGE_AVATAR_NAME:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("AVATAR_NAME_REQUEST", connectionId)));
            break;
          }
        case AVATAR_PIC:
        case CHANGE_AVATAR_PIC:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("AVATAR_PIC_REQUEST", connectionId)));
            break;
          }
        case BIRTH_DATE:
        case CHANGE_BIRTH_DATE:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("BIRTH_DATE_REQUEST", connectionId)));
            break;
          }
        case PLACE_OF_BIRTH:
        case CHANGE_PLACE_OF_BIRTH:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("PLACE_OF_BIRTH_REQUEST", connectionId)));
            break;
          }
        case MRZ:
        case CHANGE_MRZ:
          {
            messageResource.sendMessage(
                TextMessage.build(connectionId, threadId, getMessage("MRZ_REQUEST", connectionId)));
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("WEBRTC_REQUIRED", connectionId)));
            messageResource.sendMessage(MrzDataRequestMessage.build(connectionId, threadId));
            break;
          }

        case PENDING_CONFIRM:
          {
            if (session.getAvatarPic() == null) {
              messageResource.sendMessage(
                  TextMessage.build(connectionId, threadId, this.getSessionDataString(session)));
            } else {
              MediaMessage mms =
                  this.buildSessionIdentityMediaMessage(connectionId, threadId, session);
              messageResource.sendMessage(mms);
            }
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("CONFIRM_DATA_IMMUTABLE", connectionId)));
            messageResource.sendMessage(this.getConfirmData(connectionId, threadId));
            break;
          }
        case PASSWORD:
          {
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId, threadId, getMessage("PASSWORD_REQUEST", connectionId)));
            break;
          }

        case FACE_CAPTURE:
          {
            break;
          }

        case WANT_TO_CHANGE:
          {
            messageResource.sendMessage(this.getWhichToChangeUserRequested(connectionId, threadId));
            break;
          }

        case NEED_TO_CHANGE:
          {
            messageResource.sendMessage(this.getWhichToChangeNeeded(connectionId, threadId));
            break;
          }
        default:
          break;
      }
  }

  private Session issueCredentialAndSetEditMenu(Session session, Identity identity)
      throws Exception {

    if (identity
            .getAuthenticatedTs()
            .plus(Duration.ofMinutes(authenticationValidForMinutes))
            .plus(Duration.ofSeconds(15))
            .compareTo(Instant.now())
        >= 0) {
      logger.info("issueCredentialAndSetEditMenu: " + session.getFirstName());
      identity.setIssuedTs(Instant.now());
      identity.setRevokedTs(null);
      identity = em.merge(identity);
      this.sendCredential(session.getConnectionId(), identity);
      this.purgeSession(session);
      session.setIdentity(identity);
      session.setType(SessionType.EDIT);
      return em.merge(session);
    } else {
      identity.setAuthenticatedTs(null);
      identity = em.merge(identity);
    }
    return session;
  }

  public void issueEntryPoint(UUID connectionId, UUID threadId, Session session, String content)
      throws Exception {

    Identity identity = session.getIdentity();

    if ((identity.getAuthenticatedTs() != null)
        && (identity
                .getAuthenticatedTs()
                .plus(Duration.ofMinutes(authenticationValidForMinutes))
                .plus(Duration.ofSeconds(15))
                .compareTo(Instant.now())
            >= 0)) {
      session = this.issueCredentialAndSetEditMenu(session, identity);
      session = em.merge(session);
      return;
    }

    if ((session.getIssueStep() == null)) {

      switch (identity.getProtection()) {
        case PASSWORD:
          {
            session.setIssueStep(IssueStep.PASSWORD_AUTH);
            session = em.merge(session);
            messageResource.sendMessage(
                TextMessage.build(
                    connectionId,
                    threadId,
                    getMessage("PASSWORD_VERIFICATION_REQUEST", connectionId)));

            break;
          }
        case FACE:
          {
            session.setIssueStep(IssueStep.FACE_AUTH);
            session = em.merge(session);

            Token token =
                this.getToken(
                    connectionId, TokenType.FACE_VERIFICATION, session.getIdentity(), null);
            messageResource.sendMessage(
                generateFaceVerificationMediaMessage(connectionId, threadId, token));

            break;
          }
        case FINGERPRINTS:
          {
            session.setIssueStep(IssueStep.FINGERPRINT_AUTH);
            session = em.merge(session);

            this.getToken(
                connectionId, TokenType.FINGERPRINT_VERIFICATION, session.getIdentity(), null);

            break;
          }
        case WEBRTC:
          {
            session.setIssueStep(IssueStep.WEBRTC_AUTH);
            session = em.merge(session);

            Token token =
                this.getToken(
                    connectionId, TokenType.WEBRTC_VERIFICATION, session.getIdentity(), null);
            if (session.getIdentity() != null && session.getIdentity().getLegacy()) {
              messageResource.sendMessage(
                  generateFaceVerificationMediaMessage(connectionId, threadId, token));
            } else {
              this.sendWebRTCCapture(connectionId, threadId);
            }

            break;
          }
      }
    } else
      switch (session.getIssueStep()) {
        case PASSWORD_AUTH:
          {
            if (content != null) {
              logger.info("issueEntryPoint: password: " + content);
              String password = DigestUtils.sha256Hex(content);

              if ((identity.getPassword() != null)
                  && (identity.getPassword().equals(password)
                      && (identity.getConnectionId().equals(connectionId)))) {

                identity.setAuthenticatedTs(Instant.now());
                identity = em.merge(identity);
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId,
                        threadId,
                        getMessage("AUTHENTICATION_SUCCESSFULL", connectionId)));

                session = this.issueCredentialAndSetEditMenu(session, identity);
                session = em.merge(session);

              } else {

                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId, threadId, getMessage("INVALID_PASSWORD", connectionId)));
                messageResource.sendMessage(
                    TextMessage.build(
                        connectionId,
                        threadId,
                        getMessage("PASSWORD_VERIFICATION_REQUEST", connectionId)));
              }
            } else {
              messageResource.sendMessage(
                  TextMessage.build(
                      connectionId,
                      threadId,
                      getMessage("PASSWORD_VERIFICATION_REQUEST", connectionId)));
            }
            break;
          }
        case FACE_AUTH:
          {
            Token token =
                this.getToken(
                    connectionId, TokenType.FACE_VERIFICATION, session.getIdentity(), null);
            messageResource.sendMessage(
                generateFaceVerificationMediaMessage(connectionId, threadId, token));

            break;
          }

        case FINGERPRINT_AUTH:
          {
            this.getToken(
                connectionId, TokenType.FINGERPRINT_VERIFICATION, session.getIdentity(), null);

            break;
          }

        case WEBRTC_AUTH:
          {
            Token token =
                this.getToken(
                    connectionId, TokenType.WEBRTC_VERIFICATION, session.getIdentity(), null);
            if (session.getIdentity() != null && session.getIdentity().getLegacy()) {
              messageResource.sendMessage(
                  generateFaceVerificationMediaMessage(connectionId, threadId, token));
            } else {
              this.sendWebRTCCapture(connectionId, threadId);
            }

            break;
          }
      }
  }

  private String getIdentityDataString(Identity identity) {
    StringBuffer data = new StringBuffer(1024);
    data.append(getMessage("IDENTITY_DATA_STR_HEADER", identity.getConnectionId())).append("\n");

    data.append(IdentityClaim.MRZ.getClaimLabel()).append(": ");
    if (identity.getMrz() != null) {
      data.append(identity.getMrz()).append("\n");
    } else {
      data.append("<unset mrz>").append("\n");
    }

    return data.toString();
  }

  private String getSessionDataString(Session session) {
    StringBuffer data = new StringBuffer(1024);
    data.append(getMessage("IDENTITY_DATA_STR_HEADER", session.getConnectionId())).append("\n");

    data.append(IdentityClaim.MRZ.getClaimLabel()).append(": ");
    if (session.getMrz() != null) {
      data.append(session.getMrz()).append("\n");
    } else {
      data.append("<unset mrz>").append("\n");
    }

    return data.toString();
  }

  private CredentialType getCredentialType() {
    synchronized (lockObj) {
      if (type == null) {
        List<CredentialType> types = credentialTypeResource.getAllCredentialTypes();

        if ((types == null) || (types.size() == 0)) {

          CredentialType newType = new CredentialType();
          newType.setName(defName);
          newType.setVersion("1.0");

          List<String> attributes = new ArrayList<String>();
          attributes.add("id");
          attributes.add("documentType");
          attributes.add("country");
          attributes.add("firstName");
          attributes.add("lastName");
          attributes.add("birthDate");
          attributes.add("documentNumber");
          attributes.add("photo");

          attributes.add("citizenSince");
          attributes.add("issued");

          newType.setAttributes(attributes);
          try {
            logger.info("getCredentialType: create: " + JsonUtil.serialize(newType, false));
          } catch (JsonProcessingException e) {

          }

          credentialTypeResource.createCredentialType(newType);

          types = credentialTypeResource.getAllCredentialTypes();
        }
        type = types.iterator().next();
      }
    }
    return type;
  }

  private void sendCredential(UUID connectionId, Identity id) throws Exception {

    CredentialIssuanceMessage cred = new CredentialIssuanceMessage();
    cred.setConnectionId(connectionId);
    cred.setCredentialDefinitionId(getCredentialType().getId());

    if (id == null) return;

    List<Claim> claims = new ArrayList<Claim>();

    this.addClaim(
        claims,
        "id",
        Optional.ofNullable(id.getId().toString()).map(Object::toString).orElse("null"));

    this.addClaim(
        claims,
        "documentType",
        Optional.ofNullable(id.getDocumentType()).map(Object::toString).orElse("null"));
    this.addClaim(
        claims,
        "country",
        Optional.ofNullable(id.getPlaceOfBirth()).map(Object::toString).orElse("null"));
    this.addClaim(
        claims,
        "firstName",
        Optional.ofNullable(id.getFirstName()).map(Object::toString).orElse("null"));
    this.addClaim(
        claims,
        "lastName",
        Optional.ofNullable(id.getLastName()).map(Object::toString).orElse("null"));
    this.addClaim(
        claims,
        "birthDate",
        Optional.ofNullable(id.getBirthDate() != null ? id.getBirthDate().toString() : null)
            .map(Object::toString)
            .orElse("null"));
    this.addClaim(
        claims,
        "documentNumber",
        Optional.ofNullable(id.getDocumentNumber()).map(Object::toString).orElse("null"));
    this.addClaim(
        claims,
        "photo",
        Optional.ofNullable(this.getDataStorePic(id)).map(Object::toString).orElse("null"));

    Claim citizenSince = new Claim();
    citizenSince.setName("citizenSince");
    citizenSince.setValue(id.getCitizenSinceTs().toString());

    Claim issued = new Claim();
    issued.setName("issued");
    issued.setValue(id.getIssuedTs().toString());

    claims.add(citizenSince);
    claims.add(issued);
    cred.setClaims(claims);
    try {
      logger.info("sendCredential: " + JsonUtil.serialize(cred, false));
    } catch (JsonProcessingException e) {
    }
    messageResource.sendMessage(cred);
  }

  private String getDataStorePic(Identity id) throws Exception {
    UUID mediaId = id.getAvatarPic();
    String mimeType = id.getAvatarMimeType();

    if (mediaId == null) {
      logger.error("getDataStorePic: no media defined for id " + id.getId());
      throw new NoMediaException();
    }

    byte[] imageBytes = mediaResource.render(mediaId);

    if (imageBytes == null) {
      logger.error(
          "getDataStorePic: datastore returned null value for mediaId "
              + mediaId
              + " id "
              + id.getId());
      throw new NoMediaException();
    }
    if (mimeType == null) {
      mimeType = "image/jpeg";
    }

    logger.info(
        "getDataStorePic: imageBytes: "
            + imageBytes.length
            + " "
            + id.getAvatarPicCiphIv()
            + " "
            + id.getAvatarPicCiphKey());

    byte[] decrypted = null;
    if (id.getIsAvatarPicCiphered()) {
      decrypted = Aes256cbc.decrypt(id.getAvatarPicCiphKey(), id.getAvatarPicCiphIv(), imageBytes);
    } else {
      decrypted = imageBytes;
    }
    logger.info("sendCredential: decrypted: " + decrypted.length);
    return "data:" + mimeType + ";base64," + Base64.encodeBytes(decrypted);
  }

  private void addClaim(List<Claim> claims, String name, String value) {
    Claim claim = new Claim();
    claim.setName(name);
    claim.setValue(value);
    claims.add(claim);
  }

  private String getEncryptedPhoto(Identity id, MediaType face) throws Exception {
    Query q = this.em.createNamedQuery("Media.find");
    q.setParameter("identity", id);
    q.setParameter("type", face);
    List<UUID> faceMedias = q.getResultList();

    if (faceMedias.size() < 1) {
      logger.error(
          "getEncryptedPhoto: faceMedias.size() " + faceMedias.size() + " id " + id.getId());
      throw new NoMediaException();
    }
    UUID mediaId = faceMedias.iterator().next();
    byte[] imageBytes = mediaResource.render(mediaId);

    if (imageBytes == null) {
      logger.error(
          "getEncryptedPhoto: datastore returned null value for mediaId "
              + mediaId
              + " id "
              + id.getId());
      throw new NoMediaException();
    }

    String mimeType = em.find(Media.class, mediaId).getMimeType();
    if (mimeType == null) {
      mimeType = "image/jpeg";
    }

    return "data:" + mimeType + ";base64," + Base64.encodeBytes(imageBytes);
  }

  @Transactional
  public void newConnection(ConnectionStateUpdated csu) throws Exception {
    this.getConnection(csu.getConnectionId());

    // entryPointCreate(connectionId, null, null);
  }

  @Transactional
  public void deleteConnection(ConnectionStateUpdated csu) {
    Connection session = this.getConnection(csu.getConnectionId());
    if (session != null) {
      session.setDeletedTs(Instant.now());
      loggerInfoSerializeObject("deleteConnection: ", session);
    }
  }

  public Connection getConnection(UUID connectionId) {
    Connection session = em.find(Connection.class, connectionId);
    if (session == null) {
      session = new Connection();
      session.setId(connectionId);
      Instant now = Instant.now();
      session.setNextBcTs(now.plusSeconds(60l));
      session.setCreatedTs(now);
      em.persist(session);
    }
    loggerInfoSerializeObject("getConnection: ", session);
    return session;
  }

  private void purgeSession(Session session) {
    if (session != null) {
      session.setBirthDate(null);
      session.setCreateStep(null);
      session.setFirstName(null);
      session.setIdentity(null);
      session.setLastName(null);
      session.setPlaceOfBirth(null);
      session.setMrz(null);
      session.setDocumentType(null);
      session.setDocumentNumber(null);
      session.setRestoreStep(null);
      session.setType(null);
    }
  }

  private Session getSession(UUID connectionId) {
    return em.find(Session.class, connectionId);
  }

  private Session createSession(Session session, UUID connectionId) {
    if (session == null) {
      session = new Session();
      session.setConnectionId(connectionId);
      em.persist(session);
    } else {
      session.setBirthDate(null);
      session.setCreateStep(null);
      session.setFirstName(null);
      session.setIdentity(null);
      session.setLastName(null);
      session.setPlaceOfBirth(null);
      session.setMrz(null);
      session.setDocumentType(null);
      session.setDocumentNumber(null);
      session.setRestoreStep(null);
      session.setType(null);
    }
    return session;
  }

  @Transactional
  public void notifySuccess(Token token) throws Exception {

    Identity identity = token.getIdentity();
    Session session = getSession(token.getConnectionId());

    loggerInfoSerializeObject("notifySuccess: session: ", session);

    switch (token.getType()) {
      case FACE_CAPTURE:
        {
          if (session != null) {
            if (this.isValidSessionIdentity(
                session, identity, SessionType.CREATE, Protection.FACE, CreateStep.FACE_CAPTURE)) {

              identity.setProtectedTs(Instant.now());

              identity = em.merge(identity);

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("FACE_CAPTURE_SUCCESSFULL", session.getConnectionId())));

              this.purgeSession(session);
              session.setType(SessionType.ISSUE);
              session.setIdentity(identity);
              session = em.merge(session);
              this.issueEntryPoint(session.getConnectionId(), null, session, null);

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case FINGERPRINT_CAPTURE:
        {
          if (session != null) {
            if (this.isValidSessionIdentity(
                session,
                identity,
                SessionType.CREATE,
                Protection.FINGERPRINTS,
                CreateStep.FINGERPRINT_CAPTURE)) {

              identity.setProtectedTs(Instant.now());

              identity = em.merge(identity);

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("FINGERPRINT_CAPTURE_SUCCESSFULL", session.getConnectionId())));

              this.purgeSession(session);
              session.setType(SessionType.ISSUE);
              session.setIdentity(identity);
              session = em.merge(session);
              this.issueEntryPoint(session.getConnectionId(), null, session, null);

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case WEBRTC_CAPTURE:
        {
          if (session != null) {
            if (this.isValidSessionIdentity(
                session,
                identity,
                SessionType.CREATE,
                Protection.WEBRTC,
                CreateStep.WEBRTC_CAPTURE)) {

              identity.setProtectedTs(Instant.now());

              identity = em.merge(identity);

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("WEBRTC_CAPTURE_SUCCESSFULL", session.getConnectionId())));

              this.purgeSession(session);
              session.setType(SessionType.ISSUE);
              session.setIdentity(identity);
              session = em.merge(session);
              this.issueEntryPoint(session.getConnectionId(), null, session, null);

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case FACE_VERIFICATION:
        {
          if (session != null) {
            if (this.isIssueOrRestoreSession(session, identity, Protection.FACE)) {

              identity.setAuthenticatedTs(Instant.now());
              identity.setConnectionId(session.getConnectionId());

              identity = em.merge(identity);

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("AUTHENTICATION_SUCCESSFULL", session.getConnectionId())));

              session = this.issueCredentialAndSetEditMenu(session, identity);

              messageResource.sendMessage(
                  this.getRootMenu(session.getConnectionId(), session, identity));

            } else {
              logger.info("notifySuccess: session: " + JsonUtil.serialize(session, false));
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case FINGERPRINT_VERIFICATION:
        {
          if (session != null) {
            if (this.isIssueOrRestoreSession(session, identity, Protection.FINGERPRINTS)) {

              identity.setAuthenticatedTs(Instant.now());
              identity.setConnectionId(session.getConnectionId());
              identity = em.merge(identity);

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("AUTHENTICATION_SUCCESSFULL", session.getConnectionId())));

              session = this.issueCredentialAndSetEditMenu(session, identity);

              messageResource.sendMessage(
                  this.getRootMenu(session.getConnectionId(), session, identity));

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case WEBRTC_VERIFICATION:
        {
          if (session != null) {
            if (this.isIssueOrRestoreSession(session, identity, Protection.WEBRTC)) {

              identity.setAuthenticatedTs(Instant.now());
              identity.setConnectionId(session.getConnectionId());

              identity = em.merge(identity);

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("CREDENTIAL_SUCCESSFULL", session.getConnectionId())));

              session = this.issueCredentialAndSetEditMenu(session, identity);

              messageResource.sendMessage(
                  this.getRootMenu(session.getConnectionId(), session, identity));

            } else {
              logger.info("notifySuccess: session: " + JsonUtil.serialize(session, false));
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }
      default:
        break;
    }
  }

  @Transactional
  public void notifyFailure(Token token) throws Exception {
    Identity identity = token.getIdentity();
    Session session = getSession(token.getConnectionId());

    loggerInfoSerializeObject("notifyFailure: session: ", session);

    switch (token.getType()) {
      case FACE_CAPTURE:
        {
          if (session != null) {
            if (this.isValidSessionIdentity(
                session, identity, SessionType.CREATE, Protection.FACE, CreateStep.FACE_CAPTURE)) {

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("FACE_CAPTURE_ERROR", session.getConnectionId())));

              this.createEntryPoint(session.getConnectionId(), null, session, null, null);

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case FINGERPRINT_CAPTURE:
        {
          if (session != null) {
            if (this.isValidSessionIdentity(
                session,
                identity,
                SessionType.CREATE,
                Protection.FINGERPRINTS,
                CreateStep.FINGERPRINT_CAPTURE)) {

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("FINGERPRINT_CAPTURE_ERROR", session.getConnectionId())));

              this.createEntryPoint(session.getConnectionId(), null, session, null, null);

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case WEBRTC_CAPTURE:
        {
          if (session != null) {
            if (this.isValidSessionIdentity(
                session,
                identity,
                SessionType.CREATE,
                Protection.WEBRTC,
                CreateStep.WEBRTC_CAPTURE)) {

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("WEBRTC_CAPTURE_ERROR", session.getConnectionId())));

              this.createEntryPoint(session.getConnectionId(), null, session, null, null);

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case FACE_VERIFICATION:
        {
          if (session != null) {
            if (this.isIssueOrRestoreSession(session, identity, Protection.FACE)) {

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("FACE_AUTHENTICATION_ERROR", session.getConnectionId())));

              if (session.getType().equals(SessionType.ISSUE)) {
                this.issueEntryPoint(session.getConnectionId(), null, session, null);
              } else {
                this.restoreEntryPoint(session.getConnectionId(), null, session, null);
              }

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case FINGERPRINT_VERIFICATION:
        {
          if (session != null) {
            if (this.isIssueOrRestoreSession(session, identity, Protection.FINGERPRINTS)) {

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("FINGERPRINT_AUTHENTICATION_ERROR", session.getConnectionId())));

              if (session.getType().equals(SessionType.ISSUE)) {
                this.issueEntryPoint(session.getConnectionId(), null, session, null);
              } else {
                this.restoreEntryPoint(session.getConnectionId(), null, session, null);
              }

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }

      case WEBRTC_VERIFICATION:
        {
          if (session != null) {
            if (this.isIssueOrRestoreSession(session, identity, Protection.WEBRTC)) {

              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("WEBRTC_AUTHENTICATION_ERROR", session.getConnectionId())));

              if (session.getType().equals(SessionType.ISSUE)) {
                this.issueEntryPoint(session.getConnectionId(), null, session, null);
              } else {
                this.restoreEntryPoint(session.getConnectionId(), null, session, null);
              }

            } else {
              throw new TokenException();
            }
          } else {
            throw new TokenException();
          }
          break;
        }
      default:
        break;
    }
  }

  private boolean isValidSessionIdentity(
      Session session,
      Identity identity,
      SessionType sessionType,
      Protection protection,
      CreateStep createStep) {
    return (session.getType() != null)
        && (session.getType().equals(sessionType))
        && (identity.getProtection().equals(protection))
        && (session.getCreateStep().equals(createStep))
        && (identity.getProtectedTs() == null);
  }

  private boolean isIssueOrRestoreSession(
      Session session, Identity identity, Protection protection) {
    return (session.getType() != null)
        && (session.getType().equals(SessionType.ISSUE)
            || session.getType().equals(SessionType.RESTORE))
        && (identity.getProtection().equals(protection))
        && (identity.getProtectedTs() != null);
  }

  private void saveJp2Picture(String imageDataUrl, Session session, Token token) throws Exception {

    String[] parts = imageDataUrl.split(",", 2);
    String base64Data = parts[1];
    byte[] dataBytes = Base64.decode(base64Data);

    MediaMessage mms = new MediaMessage();
    mms.setConnectionId(session.getConnectionId());
    mms.setTimestamp(Instant.now());

    List<MediaItem> items = new ArrayList<MediaItem>();
    MediaItem item = new MediaItem();
    item.setMimeType(parts[0].substring(5).split(";")[0]);

    Ciphering c = Aes256cbc.randomCipheringData();
    item.setByteCount(dataBytes.length);
    item.setCiphering(c);
    items.add(item);
    mms.setItems(items);

    UUID uuid = UUID.randomUUID();
    this.setAvatarPictureSession(session, item.getMimeType(), uuid, c, null, false);
    visionService.linkMedia(token.getId(), uuid);
    this.dataStoreLoad(uuid, new ByteArrayInputStream(dataBytes));
    logger.info("saveJp2Picture: mimetype: " + item.getMimeType() + " UUID: " + uuid);
  }

  private void saveAvatarPicture(MediaMessage mm, Session session) throws Exception {
    UUID uuid = null;
    String mediaType = null;
    List<MediaItem> items = mm.getItems();

    if (items.size() == 0) {
      this.resetPictureValues(
          session, "MEDIA_NO_ATTACHMENT_ERROR", "incomingAvatarPicture: no items");
      return;
    }

    MediaItem item = items.iterator().next();

    if (item.getByteCount() < 5000000) {

      mediaType = item.getMimeType();

      if ((mediaType != null) && (mediaType.length() > 0)) {
        mediaType = mediaType.toLowerCase().strip();

        if ((mediaType.equals("image/svg+xml"))
            || (mediaType.equals("image/jpg"))
            || (mediaType.equals("image/jpeg"))
            || (mediaType.equals("image/png"))) {

          Ciphering c = item.getCiphering();
          Parameters p = c.getParameters();

          if (item.getUri() != null) {

            try {
              byte[] reencrypted = this.getMedia(item.getUri());

              if (!(mediaType.equals("image/svg+xml"))) {
                byte[] decrypted = Aes256cbc.decrypt(p.getKey(), p.getIv(), reencrypted);
                InputStream inputStream = new ByteArrayInputStream(decrypted);
                BufferedImage image = ImageIO.read(inputStream);
                BufferedImage outputImage = image;

                int currentWidth = image.getWidth();
                int currentHeight = image.getHeight();

                if ((currentWidth > avatarMaxDim) || (currentHeight > avatarMaxDim)) {

                  float width = (float) image.getWidth();
                  float height = (float) image.getHeight();

                  int newImageWidth = currentWidth;
                  int newImageHeight = currentHeight;

                  if (width >= height) {
                    float newWidth = avatarMaxDim;
                    float newHeight = height * (newWidth / width);
                    newImageWidth = (int) newWidth;
                    newImageHeight = (int) newHeight;

                  } else {
                    float newHeight = avatarMaxDim;
                    float newWidth = width * (newHeight / height);
                    newImageWidth = (int) newWidth;
                    newImageHeight = (int) newHeight;
                  }
                  Image resultingImage =
                      image.getScaledInstance(newImageWidth, newImageHeight, Image.SCALE_SMOOTH);
                  outputImage =
                      new BufferedImage(newImageWidth, newImageHeight, BufferedImage.TYPE_INT_RGB);
                  outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);

                  logger.info(
                      "incomingAvatarPicture: old size: " + currentWidth + "x" + currentHeight);

                  logger.info(
                      "incomingAvatarPicture: new size: " + newImageWidth + "x" + newImageHeight);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if (mediaType.equals("image/jpg")) {
                  ImageIO.write(outputImage, "jpg", baos);
                } else if (mediaType.equals("image/jpeg")) {
                  ImageIO.write(outputImage, "jpg", baos);
                } else if (mediaType.equals("image/png")) {
                  ImageIO.write(outputImage, "png", baos);
                }
                baos.close();
                byte[] bytes = baos.toByteArray();
                logger.info(
                    "saveAvatarPicture: reencrypted: "
                        + c.getAlgorithm()
                        + " Key "
                        + p.getKey()
                        + " Iv "
                        + p.getIv()
                        + " size: "
                        + bytes.length);

                reencrypted = Aes256cbc.encrypt(p.getKey(), p.getIv(), bytes);
              }

              // properly deciphered

              uuid = UUID.randomUUID();
              File file = new File(System.getProperty("java.io.tmpdir") + "/" + uuid);

              FileOutputStream fos = new FileOutputStream(file);
              fos.write(reencrypted);
              fos.flush();
              fos.close();

              this.dataStoreLoad(uuid, new FileInputStream(file));

              file.delete();

              this.setAvatarPictureSession(session, mediaType, uuid, c, item.getUri(), true);
            } catch (Exception e) {
              logger.error("incomingAvatarPicture", e);
              this.resetPictureValues(
                  session, "MEDIA_SAVE_ERROR", "incomingAvatarPicture: could not save avatar");
              return;
            }
          } else {

            this.resetPictureValues(session, "MEDIA_URI_ERROR", "incomingAvatarPicture: no uri");
            return;
          }

        } else {
          this.resetPictureValues(
              session, "MEDIA_TYPE_ERROR", "incomingAvatarPicture: invalid type: " + mediaType);
          return;
        }
      } else {
        this.resetPictureValues(
            session, "MEDIA_TYPE_ERROR", "incomingAvatarPicture: invalid type: " + mediaType);
        return;
      }

    } else {
      // too big too big ;-)
      this.resetPictureValues(session, "MEDIA_SIZE_ERROR", "incomingAvatarPicture: no items");
      return;
    }
  }

  private void resetPictureValues(Session session, String messageError, String log) {
    logger.info(log);
    messageResource.sendMessage(
        TextMessage.build(
            session.getConnectionId(), null, getMessage(messageError, session.getConnectionId())));
    session.setAvatarPic(null);
    session.setIsAvatarPicCiphered(null);
    session.setAvatarPicCiphAlg(null);
    session.setAvatarPicCiphIv(null);
    session.setAvatarPicCiphKey(null);
    session.setAvatarMimeType(null);
    session.setAvatarURI(null);
  }

  private void setAvatarPictureSession(
      Session session, String mediaType, UUID uuid, Ciphering c, String uri, Boolean ciphered) {
    session.setAvatarMimeType(mediaType);
    session.setAvatarPic(uuid);
    session.setIsAvatarPicCiphered(ciphered);
    session.setAvatarPicCiphAlg(c.getAlgorithm());
    session.setAvatarPicCiphIv(c.getParameters().getIv());
    session.setAvatarPicCiphKey(c.getParameters().getKey());
    session.setAvatarURI(uri);
  }

  private void dataStoreLoad(UUID uuid, InputStream inputStream) {
    mediaResource.createOrUpdate(uuid, 1, null);
    Resource r = new Resource();
    r.chunk = inputStream;
    mediaResource.uploadChunk(uuid, 0, null, r);
    logger.info("dataStoreLoad: uuid: " + uuid);
  }

  private byte[] getMedia(String uri) throws IOException, ClientProtocolException {
    HttpClient httpclient = HttpClientBuilder.create().build();
    HttpGet httpget = new HttpGet(uri);
    HttpResponse response = httpclient.execute(httpget);
    HttpEntity entity = response.getEntity();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    entity.writeTo(baos);
    return baos.toByteArray();
  }

  public void loggerInfoSerializeObject(String origin, Object object) {
    if (debug) {
      try {
        logger.info(origin + JsonUtil.serialize(object, false));
      } catch (JsonProcessingException e) {
      }
    }
  }
}
