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
import jakarta.annotation.PostConstruct;
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

  @ConfigProperty(name = "io.unicid.identity.def.claim.citizenid")
  Boolean enableCitizenIdClaim;

  @ConfigProperty(name = "io.unicid.identity.def.claim.firstName")
  Boolean enableFirstNameClaim;

  @ConfigProperty(name = "io.unicid.identity.def.claim.lastName")
  Boolean enableLastNameClaim;

  @ConfigProperty(name = "io.unicid.identity.def.claim.avatarName")
  Boolean enableAvatarNameClaim;

  @ConfigProperty(name = "io.unicid.identity.def.claim.avatarPic")
  Boolean enableAvatarPicClaim;

  @ConfigProperty(name = "io.unicid.identity.def.claim.birthDate")
  Boolean enableBirthDateClaim;

  @ConfigProperty(name = "io.unicid.identity.def.claim.birthplace")
  Boolean enableBirthplaceClaim;

  @ConfigProperty(name = "io.unicid.identity.def.claim.mrz")
  Boolean enableMrzClaim;

  @ConfigProperty(name = "io.unicid.identity.def.claim.photo")
  Boolean enablePhotoClaim;

  @ConfigProperty(name = "io.unicid.identity.restore.claim.citizenid")
  Boolean restoreCitizenidClaim;

  @ConfigProperty(name = "io.unicid.identity.restore.claim.firstName")
  Boolean restoreFirstNameClaim;

  @ConfigProperty(name = "io.unicid.identity.restore.claim.lastName")
  Boolean restoreLastNameClaim;

  @ConfigProperty(name = "io.unicid.identity.restore.claim.avatarName")
  Boolean restoreAvatarNameClaim;

  @ConfigProperty(name = "io.unicid.identity.restore.claim.birthDate")
  Boolean restoreBirthDateClaim;

  @ConfigProperty(name = "io.unicid.identity.restore.claim.birthplace")
  Boolean restoreBirthplaceClaim;

  @ConfigProperty(name = "io.unicid.identity.restore.claim.mrz")
  Boolean restoreMrzClaim;

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

  // private static String ROOT_MENU_TITLE = "🌎 Gaia Identity Registry";
  // private static String ROOT_MENU_NO_SELECTED_ID_DESCRIPTION = "Use the contextual menu to select
  // an Identity, or create a new one.";

  // private static String WELCOME = "Welcome to GIR (🌎 Gaia Identity Registry). Use the contextual
  // menu to get started.";

  private static String CMD_SELECT_ID = "/select@";

  private static String CMD_CREATE = "/create";
  // private static String CMD_CREATE_LABEL = "Create a new Identity";

  private static String CMD_RESTORE = "/restore";
  // private static String CMD_RESTORE_LABEL = "Restore an Identity";

  private static String CMD_CREATE_ABORT = "/create_abort";
  // private static String CMD_CREATE_ABORT_LABEL = "Abort and return to main menu";
  private static String CMD_RESTORE_ABORT = "/restore_abort";
  // private static String CMD_RESTORE_ABORT_LABEL = "Abort and return to main menu";
  private static String CMD_EDIT_ABORT = "/edit_abort";
  // private static String CMD_EDIT_ABORT_LABEL = "Return to main menu";
  private static String CMD_VIEW_ID = "/view";
  // private static String CMD_VIEW_ID_LABEL = "View Identity";
  private static String CMD_UNDELETE = "/undelete";
  // private static String CMD_UNDELETE_LABEL = "Undelete this Identity";
  private static String CMD_ISSUE = "/issue";
  // private static String CMD_ISSUE_LABEL = "Issue Credential";
  private static String CMD_ISSUE_ABORT = "/issue_abort";
  // private static String CMD_ISSUE_ABORT_LABEL = "Abort and return to previous menu";

  private static String CMD_CONTINUE_SETUP = "/continue";
  // private static String CMD_CONTINUE_SETUP_LABEL = "Finish Identity Setup";

  private static String CMD_DELETE = "/delete";
  // private static String CMD_DELETE_LABEL = "Delete this Identity";
  private static String CMD_REVOKE = "/revoke";

  private static String COMPLETE_IDENTITY_CONFIRM_YES_VALUE = "CI_Yes";
  private static String COMPLETE_IDENTITY_CONFIRM_NO_VALUE = "CI_No";

  @PostConstruct
  void init() {
    if (Boolean.TRUE.equals(enableMrzClaim)) {
      enableCitizenIdClaim = false;
      enableFirstNameClaim = false;
      enableLastNameClaim = false;
      enableAvatarNameClaim = false;
      enableAvatarPicClaim = false;
      enableBirthDateClaim = false;
      enableBirthplaceClaim = false;
      enablePhotoClaim = false;
    }
    if (Boolean.TRUE.equals(restoreMrzClaim)) {
      restoreCitizenidClaim = false;
      restoreFirstNameClaim = false;
      restoreLastNameClaim = false;
      restoreAvatarNameClaim = false;
      restoreBirthDateClaim = false;
      restoreBirthplaceClaim = false;
    }
  }

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
      List<Identity> myIdentities = this.getMyIdentities(connectionId);
      int i = 0;

      if (myIdentities.size() != 0) {

        for (Identity currentIdentity : myIdentities) {
          i++;
          String label = this.getIdentityLabel(currentIdentity);
          String id = CMD_SELECT_ID + currentIdentity.getId();

          options.add(ContextualMenuItem.build(id, label, null));
        }
      }

      if (i < 5) {
        // max 5 identities
        options.add(
            ContextualMenuItem.build(
                CMD_CREATE, getMessage("CMD_CREATE_LABEL", connectionId), null));
        options.add(
            ContextualMenuItem.build(
                CMD_RESTORE, getMessage("CMD_RESTORE_LABEL", connectionId), null));
      }

    } else
      switch (session.getType()) {
        case CREATE:
          {
            /* create menu */

            // abort and return to main menu
            options.add(
                ContextualMenuItem.build(
                    CMD_CREATE_ABORT, getMessage("CMD_CREATE_ABORT_LABEL", connectionId), null));
            break;
          }

        case RESTORE:
          {
            // restore menu
            // abort and return to main menu
            options.add(
                ContextualMenuItem.build(
                    CMD_RESTORE_ABORT, getMessage("CMD_RESTORE_ABORT_LABEL", connectionId), null));
            break;
          }
        case EDIT:
          {
            // edit menu
            // show identity in menu

            String idStr = getIdentityLabel(identity);
            menu.setDescription(idStr.toString());

            if (identity.getDeletedTs() != null) {
              options.add(
                  ContextualMenuItem.build(
                      CMD_VIEW_ID, getMessage("CMD_VIEW_ID_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_UNDELETE, getMessage("CMD_UNDELETE_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_EDIT_ABORT, getMessage("CMD_EDIT_ABORT_LABEL", connectionId), null));
            } else if (identity.getRevokedTs() != null) {
              options.add(
                  ContextualMenuItem.build(
                      CMD_VIEW_ID, getMessage("CMD_VIEW_ID_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_ISSUE, getMessage("CMD_ISSUE_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_DELETE, getMessage("CMD_DELETE_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_EDIT_ABORT, getMessage("CMD_EDIT_ABORT_LABEL", connectionId), null));
            } else if (identity.getIssuedTs() != null) {
              options.add(
                  ContextualMenuItem.build(
                      CMD_VIEW_ID, getMessage("CMD_VIEW_ID_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_REVOKE, getMessage("CMD_REVOKE_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_EDIT_ABORT, getMessage("CMD_EDIT_ABORT_LABEL", connectionId), null));
            } else if (identity.getProtectedTs() == null) {
              options.add(
                  ContextualMenuItem.build(
                      CMD_CONTINUE_SETUP,
                      getMessage("CMD_CONTINUE_SETUP_LABEL", connectionId),
                      null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_DELETE, getMessage("CMD_DELETE_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_EDIT_ABORT, getMessage("CMD_EDIT_ABORT_LABEL", connectionId), null));
            } else if (identity.getIssuedTs() == null) {
              options.add(
                  ContextualMenuItem.build(
                      CMD_VIEW_ID, getMessage("CMD_VIEW_ID_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_ISSUE, getMessage("CMD_ISSUE_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_DELETE, getMessage("CMD_DELETE_LABEL", connectionId), null));
              options.add(
                  ContextualMenuItem.build(
                      CMD_EDIT_ABORT, getMessage("CMD_EDIT_ABORT_LABEL", connectionId), null));
            } else {
              options.add(
                  ContextualMenuItem.build(
                      CMD_EDIT_ABORT, getMessage("CMD_EDIT_ABORT_LABEL", connectionId), null));
            }
            break;
          }

        case ISSUE:
          {
            // edit menu
            // show identity in menu

            String idStr = getIdentityLabel(identity);
            menu.setDescription(idStr.toString());
            options.add(
                ContextualMenuItem.build(
                    CMD_ISSUE_ABORT, getMessage("CMD_ISSUE_ABORT_LABEL", connectionId), null));
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
  public List<Identity> getMyIdentities(UUID connectionId) {
    Query q = em.createNamedQuery("Identity.findForConnection");
    q.setParameter("connectionId", connectionId);
    q.setParameter("deletedTs", Instant.now().minusSeconds(identityRecoverableSeconds));
    return q.getResultList();
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
    } else if ((message instanceof MrzDataSubmitMessage)) {
      MrzDataSubmitMessage mrz = (MrzDataSubmitMessage) message;
      messageResource.sendMessage(
          TextMessage.build(
              message.getConnectionId(),
              null,
              getMessage("MRZ_SUCCESSFULL", message.getConnectionId())));
      content = JsonUtil.serialize(mrz, false);
    } else if ((message instanceof CredentialReceptionMessage)) {
      CredentialReceptionMessage crp = (CredentialReceptionMessage) message;
      switch (crp.getState()) {
        case DONE:
          messageResource.sendMessage(
              TextMessage.build(
                  message.getConnectionId(),
                  null,
                  getMessage("CREDENTIAL_ACCEPTED", message.getConnectionId())));
          messageResource.sendMessage(
              TextMessage.build(
                  message.getConnectionId(),
                  null,
                  getMessage("NEW_CREDENTIAL", message.getConnectionId())));
          break;
        case DECLINED:
          messageResource.sendMessage(
              TextMessage.build(
                  message.getConnectionId(),
                  null,
                  getMessage("CREDENTIAL_REJECTED", message.getConnectionId())));
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

    UUID identityId = null;

    if (content.startsWith(CMD_SELECT_ID)) {

      logger.info("userInput: CMD_SELECT_ID : session before: " + session);

      String[] ids = content.split("@");
      boolean found = false;
      if ((ids != null) && (ids.length > 1)) {
        if (ids[1] != null) {
          try {
            identityId = UUID.fromString(ids[1]);
          } catch (Exception e) {
          }
          if (identityId != null) {
            identity = em.find(Identity.class, identityId);
            if (identity != null) {
              session = createSession(session, message.getConnectionId());
              session.setConnectionId(message.getConnectionId());
              session.setType(SessionType.EDIT);
              session.setIdentity(identity);
              session = em.merge(session);
              messageResource.sendMessage(
                  TextMessage.build(
                      message.getConnectionId(),
                      message.getThreadId(),
                      getMessage("IDENTITY_SELECTED", message.getConnectionId())
                          .replace("IDENTITY", this.getIdentityLabel(identity))));
              found = true;
            }
          }
        }
      }
      if (!found) {
        messageResource.sendMessage(
            TextMessage.build(
                message.getConnectionId(),
                message.getThreadId(),
                getMessage("IDENTITY_NOT_FOUND", message.getConnectionId())));
        em.remove(session);
        session = null;
      }

    } else if (content.equals(CMD_CREATE)) {
      logger.info("userInput: CMD_CREATE : session before: " + session);

      session = createSession(session, message.getConnectionId());
      session.setType(SessionType.CREATE);
      session = em.merge(session);

      this.createEntryPoint(message.getConnectionId(), message.getThreadId(), session, null, null);

    } else if (content.equals(CMD_RESTORE)) {

      logger.info("userInput: CMD_RESTORE : session before: " + session);

      session = createSession(session, message.getConnectionId());
      session.setType(SessionType.RESTORE);
      session = em.merge(session);
      this.restoreEntryPoint(message.getConnectionId(), message.getThreadId(), session, null);

    } else if (content.equals(CMD_CONTINUE_SETUP)) {
      logger.info("userInput: CMD_CONTINUE_SETUP : session before: " + session);

      if (session != null) {
        session.setType(SessionType.CREATE);

        session.setCreateStep(CreateStep.CAPTURE);
        session = em.merge(session);

        this.createEntryPoint(
            message.getConnectionId(), message.getThreadId(), session, null, null);
      }

      logger.info("userInput: CMD_CONTINUE_SETUP : session after: " + session);
    } else if (content.equals(CMD_CREATE_ABORT)) {
      logger.info("userInput: CMD_CREATE_ABORT : session before: " + session);

      if (session != null) {
        em.remove(session);
        session = null;
      }
      messageResource.sendMessage(
          TextMessage.build(
              message.getConnectionId(),
              message.getThreadId(),
              getMessage("IDENTITY_CREATE_ABORTED", message.getConnectionId())));

      logger.info("userInput: CMD_CREATE_ABORT : session after: " + session);
    } else if (content.equals(CMD_RESTORE_ABORT)) {
      logger.info("userInput: CMD_RESTORE_ABORT : session before: " + session);
      if (session != null) {
        em.remove(session);
        session = null;
      }
      messageResource.sendMessage(
          TextMessage.build(
              message.getConnectionId(),
              message.getThreadId(),
              getMessage("IDENTITY_RESTORE_ABORTED", message.getConnectionId())));

    } else if (content.equals(CMD_VIEW_ID)) {
      logger.info("userInput: CMD_VIEW_ID : session before: " + session);
      if ((session != null)
          && (session.getType() != null)
          && (session.getType().equals(SessionType.EDIT))
          && (identity != null)) {

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

    } else if (content.equals(CMD_UNDELETE)) {
      logger.info("userInput: CMD_UNDELETE : session before: " + session);
      if ((session != null)
          && (session.getType() != null)
          && (session.getType().equals(SessionType.EDIT))
          && (identity != null)) {
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

    } else if (content.equals(CMD_EDIT_ABORT)) {
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

    } else if (content.equals(CMD_ISSUE)) {
      logger.info("userInput: CMD_ISSUE : session before: " + session);
      if ((session != null)
          && (session.getType() != null)
          && (session.getType().equals(SessionType.EDIT))
          && (identity != null)) {

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

    } else if (content.equals(CMD_ISSUE_ABORT)) {
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

    } else if (content.equals(CMD_DELETE)) {
      logger.info("userInput: CMD_DELETE : session before: " + session);

      if ((session != null)
          && (session.getType() != null)
          && (session.getType().equals(SessionType.EDIT))
          && (identity != null)) {
        if ((identity.getIssuedTs() == null) || (identity.getRevokedTs() != null)) {

          identity.setDeletedTs(Instant.now());
          em.merge(identity);
          messageResource.sendMessage(
              TextMessage.build(
                  message.getConnectionId(),
                  message.getThreadId(),
                  getMessage("IDENTITY_DELETED", message.getConnectionId())
                      .replace("IDENTITY", this.getIdentityLabel(identity))));
        }

      } else {
        messageResource.sendMessage(
            TextMessage.build(
                message.getConnectionId(),
                message.getThreadId(),
                getMessage("ERROR_SELECT_IDENTITY_FIRST", message.getConnectionId())));
      }

    } else if (content.equals(CMD_REVOKE)) {
      logger.info("userInput: CMD_REVOKE : session before: " + session);
      if ((session != null)
          && (session.getType() != null)
          && (session.getType().equals(SessionType.EDIT))
          && (identity != null)) {
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

    } else if ((session != null)
        && (session.getType() != null)
        && (session.getType().equals(SessionType.CREATE))) {
      logger.info("userInput: CREATE entryPoint session before: " + session);

      this.createEntryPoint(message.getConnectionId(), message.getThreadId(), session, content, mm);
    } else if ((session != null)
        && (session.getType() != null)
        && (session.getType().equals(SessionType.RESTORE))) {
      logger.info("userInput: RESTORE entryPoint session before: " + session);

      this.restoreEntryPoint(message.getConnectionId(), message.getThreadId(), session, content);
    } else if ((session != null)
        && (session.getType() != null)
        && (session.getType().equals(SessionType.ISSUE))) {
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
            + session.getIdentity());

    messageResource.sendMessage(
        this.getRootMenu(message.getConnectionId(), session, session.getIdentity()));
  }

  private void updatePreferLanguage(ProfileMessage profile) {
    Connection session = this.getConnection(profile.getConnectionId());
    session.setLanguage(profile.getPreferredLanguage());
    em.merge(session);

    // Send welcome message after
    messageResource.sendMessage(
        TextMessage.build(
            profile.getConnectionId(),
            profile.getThreadId(),
            getMessage("WELCOME", profile.getConnectionId())));
    if (WELCOME2.isPresent()) {
      messageResource.sendMessage(
          TextMessage.build(
              profile.getConnectionId(),
              profile.getThreadId(),
              getMessage("WELCOME2", profile.getConnectionId())));
    }
    if (WELCOME3.isPresent()) {
      messageResource.sendMessage(
          TextMessage.build(
              profile.getConnectionId(),
              profile.getThreadId(),
              getMessage("WELCOME3", profile.getConnectionId())));
    }
    messageResource.sendMessage(this.getRootMenu(profile.getConnectionId(), null, null));
  }

  // ?token=TOKEN&d=D_DOMAIN&q=Q_DOMAIN
  private String buildVisionUrl(String url, UUID connection) {
    Connection session = this.getConnection(connection);

    if (redirDomain.isPresent()) {
      url = url + "&rd=" + redirDomain.get();
    }
    if (qRedirDomain.isPresent()) {
      url = url + "&q=" + qRedirDomain.get();
    }
    if (dRedirDomain.isPresent()) {
      url = url + "&d=" + dRedirDomain.get();
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
              session.updateSessionWithMrzData(
                  objectMapper.readValue(content, MrzDataSubmitMessage.class), session);
              session.setRestoreStep(getNextRestoreStep(session.getRestoreStep()));
              session = em.merge(session);
            }
            this.restoreSendMessage(connectionId, threadId, session);
            break;
          }

        case FACE_VERIFICATION:
          {
            Token token =
                this.getToken(connectionId, TokenType.FACE_VERIFICATION, session.getIdentity());
            messageResource.sendMessage(
                generateFaceVerificationMediaMessage(connectionId, threadId, token));
            /*messageResource.sendMessage(TextMessage.build(connectionId, threadId, FACE_VERIFICATION_REQUEST.replaceFirst("URL", faceVerificationUrl.replaceFirst("TOKEN", token.getId().toString())
            		.replaceFirst("REDIRDOMAIN", redirDomain)
            		.replaceFirst("Q_DOMAIN", qRedirDomain)
            		.replaceFirst("D_DOMAIN", dRedirDomain)
            		)));
            */
            break;
          }

        case FINGERPRINT_VERIFICATION:
          {
            Token token =
                this.getToken(
                    connectionId, TokenType.FINGERPRINT_VERIFICATION, session.getIdentity());

            break;
          }
        case WEBRTC_VERIFICATION:
          {
            this.getToken(connectionId, TokenType.WEBRTC_VERIFICATION, session.getIdentity());
            this.sendWebRTCCapture(session, threadId);

            break;
          }
        case PASSWORD:
          {
            if (content != null) {
              logger.info("restoreEntryPoint: password: " + content);
              String password = DigestUtils.sha256Hex(content);
              // 	@NamedQuery(name="Identity.findForRestore", query="SELECT i FROM Identity i where
              // i.connectionId<>:connectionId and (i.deletedTs IS NULL or i.deletedTs>:deletedTs)
              // and i.firstName=:firstName and i.lastName=:lastName and i.birthDate=:birthDate
              // ORDER by i.id ASC"),

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

      CriteriaBuilder builder = em.getCriteriaBuilder();
      CriteriaQuery<Identity> query = builder.createQuery(Identity.class);
      Root<Identity> root = query.from(Identity.class);

      List<Predicate> allPredicates = new ArrayList<Predicate>();

      if (restoreCitizenidClaim) {
        Predicate predicate = builder.equal(root.get("citizenId"), session.getCitizenId());
        allPredicates.add(predicate);
      }

      if (restoreFirstNameClaim) {
        Predicate predicate = builder.equal(root.get("firstName"), session.getFirstName());
        allPredicates.add(predicate);
      }
      if (restoreLastNameClaim) {
        Predicate predicate = builder.equal(root.get("lastName"), session.getLastName());
        allPredicates.add(predicate);
      }
      if (restoreAvatarNameClaim) {
        Predicate predicate = builder.equal(root.get("avatarName"), session.getAvatarName());
        allPredicates.add(predicate);
      }

      if (restoreBirthDateClaim) {
        Predicate predicate = builder.equal(root.get("birthDate"), session.getBirthDate());
        allPredicates.add(predicate);
      }
      if (restoreBirthplaceClaim) {
        Predicate predicate = builder.equal(root.get("placeOfBirth"), session.getPlaceOfBirth());
        allPredicates.add(predicate);
      }
      if (restoreMrzClaim) {
        Predicate predicate = builder.equal(root.get("mrz"), session.getMrz());
        allPredicates.add(predicate);
      }

      query.where(builder.and(allPredicates.toArray(new Predicate[allPredicates.size()])));

      query.orderBy(builder.desc(root.get("id")));
      Query q = em.createQuery(query);

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
        session.setIdentity(res);
        switch (res.getProtection()) {
          case PASSWORD:
            {
              logger.info(
                  "restoreEntryPoint: found password method for identity "
                      + this.getIdentityDataString(res));
              session.setRestoreStep(RestoreStep.PASSWORD);
              session = em.merge(session);
              this.restoreSendMessage(connectionId, threadId, session);
              break;
            }

          case FACE:
            {
              logger.info(
                  "restoreEntryPoint: found face verification method for identity "
                      + this.getIdentityDataString(res));

              session.setRestoreStep(RestoreStep.FACE_VERIFICATION);
              session = em.merge(session);

              Token token = this.getToken(connectionId, TokenType.FACE_VERIFICATION, res);

              messageResource.sendMessage(
                  generateFaceVerificationMediaMessage(connectionId, threadId, token));
              /*messageResource.sendMessage(TextMessage.build(connectionId, threadId, FACE_VERIFICATION_REQUEST.replaceFirst("URL", faceVerificationUrl.replaceFirst("TOKEN", token.getId().toString())
              .replaceFirst("REDIRDOMAIN", redirDomain)
              .replaceFirst("Q_DOMAIN", qRedirDomain)
              .replaceFirst("D_DOMAIN", dRedirDomain)

              )));*/

              logger.info("restoreEntryPoint: session: " + JsonUtil.serialize(session, false));

              break;
            }

          case FINGERPRINTS:
            {
              logger.info(
                  "restoreEntryPoint: found fingerprint verification method for identity "
                      + this.getIdentityDataString(res));

              session.setRestoreStep(RestoreStep.FINGERPRINT_VERIFICATION);
              session = em.merge(session);

              Token token = this.getToken(connectionId, TokenType.FINGERPRINT_VERIFICATION, res);

              break;
            }
          case WEBRTC:
            {
              logger.info(
                  "restoreEntryPoint: found webrtc verification method for identity "
                      + this.getIdentityDataString(res));

              session.setRestoreStep(RestoreStep.WEBRTC_VERIFICATION);
              session = em.merge(session);

              this.getToken(connectionId, TokenType.WEBRTC_VERIFICATION, res);
              this.sendWebRTCCapture(session, threadId);

              break;
            }
        }
      }
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
                TextMessage.build(connectionId, threadId, getMessage("RESTORE_MRZ", connectionId)));
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

    CriteriaBuilder builder = em.getCriteriaBuilder();
    CriteriaQuery<Identity> query = builder.createQuery(Identity.class);
    Root<Identity> root = query.from(Identity.class);

    List<Predicate> allPredicates = new ArrayList<Predicate>();

    if (restoreCitizenidClaim) {
      Predicate predicate = builder.equal(root.get("citizenId"), session.getCitizenId());
      allPredicates.add(predicate);
      if (debug) logger.info("identityAlreadyExists: citizenId: " + session.getCitizenId());
    }

    if (restoreFirstNameClaim) {
      Predicate predicate = builder.equal(root.get("firstName"), session.getFirstName());
      allPredicates.add(predicate);

      if (debug) logger.info("identityAlreadyExists: firstName: " + session.getFirstName());
    }
    if (restoreLastNameClaim) {
      Predicate predicate = builder.equal(root.get("lastName"), session.getLastName());
      allPredicates.add(predicate);
      if (debug) logger.info("identityAlreadyExists: lastName: " + session.getLastName());
    }
    if (restoreAvatarNameClaim) {
      Predicate predicate = builder.equal(root.get("avatarName"), session.getAvatarName());
      allPredicates.add(predicate);
      if (debug) logger.info("identityAlreadyExists: avatarName: " + session.getAvatarName());
    }

    if (restoreBirthDateClaim) {
      Predicate predicate = builder.equal(root.get("birthDate"), session.getBirthDate());
      allPredicates.add(predicate);
      if (debug) logger.info("identityAlreadyExists: birthDate: " + session.getBirthDate());
    }

    if (restoreMrzClaim) {
      Predicate predicate = builder.equal(root.get("mrz"), session.getMrz());
      allPredicates.add(predicate);
      if (debug) logger.info("identityAlreadyExists: mrz: " + session.getMrz());
    }
    if (restoreBirthplaceClaim) {
      Predicate predicate = builder.equal(root.get("placeOfBirth"), session.getPlaceOfBirth());
      allPredicates.add(predicate);
      if (debug) logger.info("identityAlreadyExists: placeOfBirth: " + session.getPlaceOfBirth());
    }

    query.where(builder.and(allPredicates.toArray(new Predicate[allPredicates.size()])));

    query.orderBy(builder.desc(root.get("id")));
    Query q = em.createQuery(query);

    List<Identity> founds = q.getResultList();
    if (debug) {
      try {
        logger.info("identityAlreadyExists: found: " + JsonUtil.serialize(founds, false));
      } catch (JsonProcessingException e) {
        logger.error("", e);
      }
    }

    return (founds.size() > 0);
  }

  private CreateStep getNextCreateStep(CreateStep current) throws Exception {

    if (current == null) {
      if (enableMrzClaim) return CreateStep.MRZ;
      if (enableCitizenIdClaim) return CreateStep.CITIZEN_ID;
      if (enableFirstNameClaim) return CreateStep.FIRST_NAME;
      if (enableLastNameClaim) return CreateStep.LAST_NAME;
      if (enableAvatarNameClaim) return CreateStep.AVATAR_NAME;
      if (enableAvatarPicClaim) return CreateStep.AVATAR_PIC;
      if (enableBirthDateClaim) return CreateStep.BIRTH_DATE;
      if (enableBirthplaceClaim) return CreateStep.PLACE_OF_BIRTH;

      throw new Exception("no claim has been enabled");
    } else {
      switch (current) {
        case CITIZEN_ID:
          {
            if (enableFirstNameClaim) return CreateStep.FIRST_NAME;
            if (enableLastNameClaim) return CreateStep.LAST_NAME;
            if (enableAvatarNameClaim) return CreateStep.AVATAR_NAME;
            if (enableAvatarPicClaim) return CreateStep.AVATAR_PIC;
            if (enableBirthDateClaim) return CreateStep.BIRTH_DATE;
            if (enableBirthplaceClaim) return CreateStep.PLACE_OF_BIRTH;
            return CreateStep.PENDING_CONFIRM;
          }
        case FIRST_NAME:
          {
            if (enableLastNameClaim) return CreateStep.LAST_NAME;
            if (enableAvatarNameClaim) return CreateStep.AVATAR_NAME;
            if (enableAvatarPicClaim) return CreateStep.AVATAR_PIC;
            if (enableBirthDateClaim) return CreateStep.BIRTH_DATE;
            if (enableBirthplaceClaim) return CreateStep.PLACE_OF_BIRTH;
            return CreateStep.PENDING_CONFIRM;
          }
        case LAST_NAME:
          {
            if (enableAvatarNameClaim) return CreateStep.AVATAR_NAME;
            if (enableAvatarPicClaim) return CreateStep.AVATAR_PIC;
            if (enableBirthDateClaim) return CreateStep.BIRTH_DATE;
            if (enableBirthplaceClaim) return CreateStep.PLACE_OF_BIRTH;
            return CreateStep.PENDING_CONFIRM;
          }
        case AVATAR_NAME:
          {
            if (enableAvatarPicClaim) return CreateStep.AVATAR_PIC;
            if (enableBirthDateClaim) return CreateStep.BIRTH_DATE;
            if (enableBirthplaceClaim) return CreateStep.PLACE_OF_BIRTH;
            return CreateStep.PENDING_CONFIRM;
          }
        case AVATAR_PIC:
          {
            if (enableBirthDateClaim) return CreateStep.BIRTH_DATE;
            if (enableBirthplaceClaim) return CreateStep.PLACE_OF_BIRTH;
            return CreateStep.PENDING_CONFIRM;
          }
        case BIRTH_DATE:
          {
            if (enableBirthplaceClaim) return CreateStep.PLACE_OF_BIRTH;
            return CreateStep.PENDING_CONFIRM;
          }
        case PLACE_OF_BIRTH:
        case MRZ:
        default:
          {
            return CreateStep.PENDING_CONFIRM;
          }
      }
    }
  }

  private RestoreStep getNextRestoreStep(RestoreStep current) throws Exception {

    if (current == null) {
      if (restoreMrzClaim) return RestoreStep.MRZ;
      if (restoreCitizenidClaim) return RestoreStep.CITIZEN_ID;
      if (restoreFirstNameClaim) return RestoreStep.FIRST_NAME;
      if (restoreLastNameClaim) return RestoreStep.LAST_NAME;
      if (restoreAvatarNameClaim) return RestoreStep.AVATAR_NAME;
      if (restoreBirthDateClaim) return RestoreStep.BIRTH_DATE;
      if (restoreBirthplaceClaim) return RestoreStep.PLACE_OF_BIRTH;

      throw new Exception("no claim has been enabled");
    } else {
      switch (current) {
        case CITIZEN_ID:
          {
            if (restoreFirstNameClaim) return RestoreStep.FIRST_NAME;
            if (restoreLastNameClaim) return RestoreStep.LAST_NAME;
            if (restoreAvatarNameClaim) return RestoreStep.AVATAR_NAME;
            if (restoreBirthDateClaim) return RestoreStep.BIRTH_DATE;
            if (restoreBirthplaceClaim) return RestoreStep.PLACE_OF_BIRTH;
            return RestoreStep.DONE;
          }
        case FIRST_NAME:
          {
            if (restoreLastNameClaim) return RestoreStep.LAST_NAME;
            if (restoreAvatarNameClaim) return RestoreStep.AVATAR_NAME;
            if (restoreBirthDateClaim) return RestoreStep.BIRTH_DATE;
            if (restoreBirthplaceClaim) return RestoreStep.PLACE_OF_BIRTH;
            return RestoreStep.DONE;
          }
        case LAST_NAME:
          {
            if (restoreAvatarNameClaim) return RestoreStep.AVATAR_NAME;
            if (restoreBirthDateClaim) return RestoreStep.BIRTH_DATE;
            if (restoreBirthplaceClaim) return RestoreStep.PLACE_OF_BIRTH;
            return RestoreStep.DONE;
          }
        case AVATAR_NAME:
          {
            if (restoreBirthDateClaim) return RestoreStep.BIRTH_DATE;
            if (restoreBirthplaceClaim) return RestoreStep.PLACE_OF_BIRTH;
            return RestoreStep.DONE;
          }

        case BIRTH_DATE:
          {
            if (restoreBirthplaceClaim) return RestoreStep.PLACE_OF_BIRTH;
            return RestoreStep.DONE;
          }
        case PLACE_OF_BIRTH:
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
              session.updateSessionWithMrzData(
                  objectMapper.readValue(content, MrzDataSubmitMessage.class), session);
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

        case PENDING_CONFIRM:
          {
            Identity identity = null;
            if (content != null) {
              if (content.equals(COMPLETE_IDENTITY_CONFIRM_YES_VALUE)) {

                if (!this.identityAlreadyExists(session)) {
                  identity = new Identity();
                  identity.setId(UUID.randomUUID());
                  identity.setCitizenId(session.getCitizenId());
                  identity.setFirstName(session.getFirstName());
                  identity.setLastName(session.getLastName());
                  identity.setAvatarPic(session.getAvatarPic());
                  identity.setAvatarPicCiphAlg(session.getAvatarPicCiphAlg());
                  identity.setAvatarPicCiphIv(session.getAvatarPicCiphIv());
                  identity.setAvatarPicCiphKey(session.getAvatarPicCiphKey());
                  identity.setAvatarMimeType(session.getAvatarMimeType());
                  identity.setAvatarName(session.getAvatarName());
                  identity.setBirthDate(session.getBirthDate());
                  identity.setPlaceOfBirth(session.getPlaceOfBirth());
                  identity.setMrz(session.getMrz());
                  identity.setDocumentType(session.getDocumentType());
                  identity.setDocumentNumber(session.getDocumentNumber());
                  identity.setCitizenSinceTs(Instant.now());
                  identity.setConnectionId(connectionId);
                  identity.setProtection(protection);
                  em.persist(identity);

                  session.setIdentity(identity);
                  session = em.merge(session);
                }

                session.setCreateStep(CreateStep.CAPTURE);
                session = em.merge(session);

              } else if (content.equals(COMPLETE_IDENTITY_CONFIRM_NO_VALUE)) {
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
                        this.getToken(connectionId, TokenType.FACE_CAPTURE, session.getIdentity());
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId,
                            threadId,
                            getMessage("FACE_CAPTURE_REQUIRED", connectionId)));
                    messageResource.sendMessage(
                        generateFaceCaptureMediaMessage(connectionId, threadId, token));
                    /*
                    messageResource.sendMessage(TextMessage.build(connectionId, threadId, FACE_CAPTURE_REQUEST.replaceFirst("URL", faceCaptureUrl.replaceFirst("TOKEN", token.getId().toString())
                    		.replaceFirst("REDIRDOMAIN", redirDomain)
                    		.replaceFirst("Q_DOMAIN", qRedirDomain)
                    		.replaceFirst("D_DOMAIN", dRedirDomain)
                    		)));
                    */
                    break;
                  }
                case FINGERPRINTS:
                  {
                    session.setCreateStep(CreateStep.FINGERPRINT_CAPTURE);
                    session = em.merge(session);

                    Token token =
                        this.getToken(
                            connectionId, TokenType.FINGERPRINT_CAPTURE, session.getIdentity());
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId,
                            threadId,
                            getMessage("FINGERPRINT_CAPTURE_REQUIRED", connectionId)));
                    /*	messageResource.sendMessage(TextMessage.build(connectionId, threadId, getMessage("FINGERPRINT_CAPTURE_REQUEST").replaceFirst("URL", fingerprintsCaptureUrl.replaceFirst("TOKEN", token.getId().toString())
                    .replaceFirst("REDIRDOMAIN", redirDomain)
                    .replaceFirst("Q_DOMAIN", qRedirDomain)
                    .replaceFirst("D_DOMAIN", dRedirDomain))));*/

                    break;
                  }
                case WEBRTC:
                  {
                    session.setCreateStep(CreateStep.WEBRTC_CAPTURE);
                    session = em.merge(session);

                    Token token =
                        this.getToken(
                            connectionId, TokenType.WEBRTC_CAPTURE, session.getIdentity());
                    messageResource.sendMessage(
                        TextMessage.build(
                            connectionId, threadId, getMessage("WEBRTC_REQUIRED", connectionId)));
                    this.notifySuccess(token);
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
                session = em.merge(session);
              } else if (content.equals(IdentityClaim.FIRST_NAME.toString())) {
                session.setCreateStep(CreateStep.CHANGE_FIRST_NAME);
                session = em.merge(session);
              } else if (content.equals(IdentityClaim.LAST_NAME.toString())) {
                session.setCreateStep(CreateStep.CHANGE_LAST_NAME);
                session = em.merge(session);
              } else if (content.equals(IdentityClaim.AVATAR_NAME.toString())) {
                session.setCreateStep(CreateStep.CHANGE_AVATAR_NAME);
                session = em.merge(session);
              } else if (content.equals(IdentityClaim.AVATAR_PIC.toString())) {
                session.setCreateStep(CreateStep.CHANGE_AVATAR_PIC);
                session = em.merge(session);
              } else if (content.equals(IdentityClaim.BIRTH_DATE.toString())) {
                session.setCreateStep(CreateStep.CHANGE_BIRTH_DATE);
                session = em.merge(session);
              } else if (content.equals(IdentityClaim.PLACE_OF_BIRTH.toString())) {
                session.setCreateStep(CreateStep.CHANGE_PLACE_OF_BIRTH);
                session = em.merge(session);
              } else if (content.equals(IdentityClaim.MRZ.toString())) {
                session.setCreateStep(CreateStep.CHANGE_MRZ);
                session = em.merge(session);
              }
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
              session.updateSessionWithMrzData(
                  objectMapper.readValue(content, MrzDataSubmitMessage.class), session);

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
                this.getToken(connectionId, TokenType.FACE_CAPTURE, session.getIdentity());

            messageResource.sendMessage(
                generateFaceCaptureMediaMessage(connectionId, threadId, token));

            /*messageResource.sendMessage(TextMessage.build(connectionId, threadId, FACE_CAPTURE_REQUEST.replaceFirst("URL", faceCaptureUrl.replaceFirst("TOKEN", token.getId().toString())
            .replaceFirst("REDIRDOMAIN", redirDomain)
            .replaceFirst("Q_DOMAIN", qRedirDomain)
            .replaceFirst("D_DOMAIN", dRedirDomain))));*/
            break;
          }
        case FINGERPRINT_CAPTURE:
          {
            Token token =
                this.getToken(connectionId, TokenType.FINGERPRINT_CAPTURE, session.getIdentity());

            break;
          }
        case WEBRTC_CAPTURE:
          {
            Token token =
                this.getToken(connectionId, TokenType.WEBRTC_CAPTURE, session.getIdentity());
            this.notifySuccess(token);

            break;
          }

        default:
          break;
      }
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

  private Token getToken(UUID connectionId, TokenType type, Identity identity) {

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

  private void sendWebRTCCapture(Session session, UUID threadId) {

    CreateRoomRequest request = new CreateRoomRequest(redirDomain.get() + "/call-event", 50);
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
      cr.setIdentity(session.getIdentity());
      cr.setRoomId(wsUrl.getRoomId());
      cr.setWsUrl(wsUrl.getWsUrl());
      cr.setType(PeerType.PEER_USER);
      em.persist(cr);
    }

    messageResource.sendMessage(
        TextMessage.build(
            session.getConnectionId(),
            null,
            getMessage("MRZ_FACE_VERIFICATION", session.getConnectionId())));
    messageResource.sendMessage(
        this.generateOfferMessage(session.getConnectionId(), threadId, wsUrlMap));
  }

  private BaseMessage getWhichToChangeUserRequested(UUID connectionId, UUID threadId) {
    List<MenuItem> menuItems = new ArrayList<MenuItem>();

    MenuDisplayMessage confirm = new MenuDisplayMessage();
    confirm.setPrompt(getMessage("CHANGE_CLAIM_TITLE", connectionId));

    if (enableCitizenIdClaim) {
      MenuItem citizenId = new MenuItem();
      citizenId.setId(IdentityClaim.CITIZEN_ID.toString());
      citizenId.setText(IdentityClaim.CITIZEN_ID.getClaimLabel());
      menuItems.add(citizenId);
    }

    if (enableFirstNameClaim) {
      MenuItem firstName = new MenuItem();
      firstName.setId(IdentityClaim.FIRST_NAME.toString());
      firstName.setText(IdentityClaim.FIRST_NAME.getClaimLabel());
      menuItems.add(firstName);
    }
    if (enableLastNameClaim) {
      MenuItem lastName = new MenuItem();
      lastName.setId(IdentityClaim.LAST_NAME.toString());
      lastName.setText(IdentityClaim.LAST_NAME.getClaimLabel());
      menuItems.add(lastName);
    }

    if (enableAvatarNameClaim) {
      MenuItem avatarName = new MenuItem();
      avatarName.setId(IdentityClaim.AVATAR_NAME.toString());
      avatarName.setText(IdentityClaim.AVATAR_NAME.getClaimLabel());
      menuItems.add(avatarName);
    }

    if (enableAvatarPicClaim) {
      MenuItem avatarName = new MenuItem();
      avatarName.setId(IdentityClaim.AVATAR_PIC.toString());
      avatarName.setText(IdentityClaim.AVATAR_PIC.getClaimLabel());
      menuItems.add(avatarName);
    }
    if (enableBirthDateClaim) {
      MenuItem birthDate = new MenuItem();
      birthDate.setId(IdentityClaim.BIRTH_DATE.toString());
      birthDate.setText(IdentityClaim.BIRTH_DATE.getClaimLabel());
      menuItems.add(birthDate);
    }

    if (enableBirthplaceClaim) {
      MenuItem placeOfBirth = new MenuItem();
      placeOfBirth.setId(IdentityClaim.PLACE_OF_BIRTH.toString());
      placeOfBirth.setText(IdentityClaim.PLACE_OF_BIRTH.getClaimLabel());
      menuItems.add(placeOfBirth);
    }

    if (enableMrzClaim) {
      MenuItem mrz = new MenuItem();
      mrz.setId(IdentityClaim.MRZ.toString());
      mrz.setText(IdentityClaim.MRZ.getClaimLabel());
      menuItems.add(mrz);
    }

    confirm.setConnectionId(connectionId);
    confirm.setThreadId(threadId);
    confirm.setMenuItems(menuItems);
    return confirm;
  }

  private BaseMessage getWhichToChangeNeeded(UUID connectionId, UUID threadId) {
    List<MenuItem> menuItems = new ArrayList<MenuItem>();

    MenuDisplayMessage confirm = new MenuDisplayMessage();
    confirm.setPrompt(getMessage("CONFLICTIVE_CLAIM_TITLE", connectionId));

    if (restoreCitizenidClaim) {
      MenuItem citizenId = new MenuItem();
      citizenId.setId(IdentityClaim.CITIZEN_ID.toString());
      citizenId.setText(IdentityClaim.CITIZEN_ID.getClaimLabel());
      menuItems.add(citizenId);
    }

    if (restoreFirstNameClaim) {
      MenuItem firstName = new MenuItem();
      firstName.setId(IdentityClaim.FIRST_NAME.toString());
      firstName.setText(IdentityClaim.FIRST_NAME.getClaimLabel());
      menuItems.add(firstName);
    }
    if (restoreLastNameClaim) {
      MenuItem lastName = new MenuItem();
      lastName.setId(IdentityClaim.LAST_NAME.toString());
      lastName.setText(IdentityClaim.LAST_NAME.getClaimLabel());
      menuItems.add(lastName);
    }

    if (restoreAvatarNameClaim) {
      MenuItem avatarName = new MenuItem();
      avatarName.setId(IdentityClaim.AVATAR_NAME.toString());
      avatarName.setText(IdentityClaim.AVATAR_NAME.getClaimLabel());
      menuItems.add(avatarName);
    }

    if (restoreBirthDateClaim) {
      MenuItem birthDate = new MenuItem();
      birthDate.setId(IdentityClaim.BIRTH_DATE.toString());
      birthDate.setText(IdentityClaim.BIRTH_DATE.getClaimLabel());
      menuItems.add(birthDate);
    }

    if (restoreBirthplaceClaim) {
      MenuItem placeOfBirth = new MenuItem();
      placeOfBirth.setId(IdentityClaim.PLACE_OF_BIRTH.toString());
      placeOfBirth.setText(IdentityClaim.PLACE_OF_BIRTH.getClaimLabel());
      menuItems.add(placeOfBirth);
    }

    if (restoreMrzClaim) {
      MenuItem mrz = new MenuItem();
      mrz.setId(IdentityClaim.MRZ.toString());
      mrz.setText(IdentityClaim.MRZ.getClaimLabel());
      menuItems.add(mrz);
    }

    confirm.setConnectionId(connectionId);
    confirm.setThreadId(threadId);
    confirm.setMenuItems(menuItems);
    return confirm;
  }

  private BaseMessage getConfirmData(UUID connectionId, UUID threadId) {

    MenuDisplayMessage confirm = new MenuDisplayMessage();
    confirm.setPrompt(getMessage("COMPLETE_IDENTITY_CONFIRM_TITLE", connectionId));

    MenuItem yes = new MenuItem();
    yes.setId(COMPLETE_IDENTITY_CONFIRM_YES_VALUE);
    yes.setText(getMessage("COMPLETE_IDENTITY_CONFIRM_YES", connectionId));

    MenuItem no = new MenuItem();
    no.setId(COMPLETE_IDENTITY_CONFIRM_NO_VALUE);
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

      messageResource.sendMessage(
          TextMessage.build(connectionId, threadId, getMessage("IDENTITY_LOCKED", connectionId)));

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
                this.getToken(connectionId, TokenType.FACE_VERIFICATION, session.getIdentity());
            messageResource.sendMessage(
                generateFaceVerificationMediaMessage(connectionId, threadId, token));
            /*messageResource.sendMessage(TextMessage.build(connectionId, threadId, FACE_VERIFICATION_REQUEST.replaceFirst("URL", faceVerificationUrl.replaceFirst("TOKEN", token.getId().toString())
            .replaceFirst("REDIRDOMAIN", redirDomain)
            .replaceFirst("Q_DOMAIN", qRedirDomain)
            .replaceFirst("D_DOMAIN", dRedirDomain))));*/

            break;
          }
        case FINGERPRINTS:
          {
            session.setIssueStep(IssueStep.FINGERPRINT_AUTH);
            session = em.merge(session);

            Token token =
                this.getToken(
                    connectionId, TokenType.FINGERPRINT_VERIFICATION, session.getIdentity());

            break;
          }
        case WEBRTC:
          {
            session.setIssueStep(IssueStep.WEBRTC_AUTH);
            session = em.merge(session);

            this.getToken(connectionId, TokenType.WEBRTC_VERIFICATION, session.getIdentity());
            this.sendWebRTCCapture(session, threadId);

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
                this.getToken(connectionId, TokenType.FACE_VERIFICATION, session.getIdentity());
            messageResource.sendMessage(
                generateFaceVerificationMediaMessage(connectionId, threadId, token));
            /*messageResource.sendMessage(TextMessage.build(connectionId, threadId, FACE_VERIFICATION_REQUEST.replaceFirst("URL", faceVerificationUrl.replaceFirst("TOKEN", token.getId().toString())
            .replaceFirst("REDIRDOMAIN", redirDomain)
            .replaceFirst("Q_DOMAIN", qRedirDomain)
            .replaceFirst("D_DOMAIN", dRedirDomain))));*/

            break;
          }

        case FINGERPRINT_AUTH:
          {
            Token token =
                this.getToken(
                    connectionId, TokenType.FINGERPRINT_VERIFICATION, session.getIdentity());

            break;
          }

        case WEBRTC_AUTH:
          {
            this.getToken(connectionId, TokenType.WEBRTC_VERIFICATION, session.getIdentity());
            this.sendWebRTCCapture(session, threadId);

            break;
          }
      }
  }

  private String getIdentityDataString(Identity identity) {
    StringBuffer data = new StringBuffer(1024);
    data.append(getMessage("IDENTITY_DATA_STR_HEADER", identity.getConnectionId())).append("\n");

    if (enableCitizenIdClaim) {
      data.append(IdentityClaim.CITIZEN_ID.getClaimLabel()).append(": ");

      if (identity.getCitizenId() != null) {
        data.append(identity.getCitizenId()).append("\n");
      } else {
        data.append("<unset citizenId>").append("\n");
      }
    }

    if (enableFirstNameClaim) {
      data.append(IdentityClaim.FIRST_NAME.getClaimLabel()).append(": ");

      if (identity.getFirstName() != null) {
        data.append(identity.getFirstName()).append("\n");
      } else {
        data.append("<unset firstName>").append("\n");
      }
    }
    if (enableLastNameClaim) {
      data.append(IdentityClaim.LAST_NAME.getClaimLabel()).append(": ");

      if (identity.getLastName() != null) {
        data.append(identity.getLastName()).append("\n");
      } else {
        data.append("<unset lastName>").append("\n");
      }
    }

    if (enableAvatarNameClaim) {
      data.append(IdentityClaim.AVATAR_NAME.getClaimLabel()).append(": ");

      if (identity.getAvatarName() != null) {
        data.append(identity.getAvatarName()).append("\n");
      } else {
        data.append("<unset avatarName>").append("\n");
      }
    }
    if (enableAvatarPicClaim) {
      data.append(IdentityClaim.AVATAR_PIC.getClaimLabel()).append(": ");

      if (identity.getAvatarPic() != null) {
        data.append(identity.getAvatarPic()).append("\n");
      } else {
        data.append("<unset avatarPic>").append("\n");
      }
    }
    if (enableBirthDateClaim) {
      data.append(IdentityClaim.BIRTH_DATE.getClaimLabel()).append(": ");
      if (identity.getBirthDate() != null) {
        data.append(identity.getBirthDate()).append("\n");
      } else {
        data.append("<unset birthDate>").append("\n");
      }
    }

    if (enableBirthplaceClaim) {
      data.append(IdentityClaim.PLACE_OF_BIRTH.getClaimLabel()).append(": ");
      if (identity.getPlaceOfBirth() != null) {
        data.append(identity.getPlaceOfBirth()).append("\n");
      } else {
        data.append("<unset placeOfBirth>").append("\n");
      }
    }

    if (enableMrzClaim) {
      data.append(IdentityClaim.MRZ.getClaimLabel()).append(": ");
      if (identity.getMrz() != null) {
        data.append(identity.getMrz()).append("\n");
      } else {
        data.append("<unset mrz>").append("\n");
      }
    }

    return data.toString();
  }

  private String getSessionDataString(Session session) {
    StringBuffer data = new StringBuffer(1024);
    data.append(getMessage("IDENTITY_DATA_STR_HEADER", session.getConnectionId())).append("\n");

    if (enableCitizenIdClaim) {
      data.append(IdentityClaim.CITIZEN_ID.getClaimLabel()).append(": ");

      if (session.getCitizenId() != null) {
        data.append(session.getCitizenId()).append("\n");
      } else {
        data.append("<unset citizenId>").append("\n");
      }
    }

    if (enableFirstNameClaim) {
      data.append(IdentityClaim.FIRST_NAME.getClaimLabel()).append(": ");

      if (session.getFirstName() != null) {
        data.append(session.getFirstName()).append("\n");
      } else {
        data.append("<unset firstName>").append("\n");
      }
    }
    if (enableLastNameClaim) {
      data.append(IdentityClaim.LAST_NAME.getClaimLabel()).append(": ");

      if (session.getLastName() != null) {
        data.append(session.getLastName()).append("\n");
      } else {
        data.append("<unset lastName>").append("\n");
      }
    }

    if (enableAvatarNameClaim) {
      data.append(IdentityClaim.AVATAR_NAME.getClaimLabel()).append(": ");

      if (session.getAvatarName() != null) {
        data.append(session.getAvatarName()).append("\n");
      } else {
        data.append("<unset avatarName>").append("\n");
      }
    }
    if (enableAvatarPicClaim) {
      data.append(IdentityClaim.AVATAR_PIC.getClaimLabel()).append(": ");

      if (session.getAvatarPic() != null) {
        data.append(session.getAvatarPic()).append("\n");
      } else {
        data.append("<unset avatarPic>").append("\n");
      }
    }
    if (enableBirthDateClaim) {
      data.append(IdentityClaim.BIRTH_DATE.getClaimLabel()).append(": ");
      if (session.getBirthDate() != null) {
        data.append(session.getBirthDate()).append("\n");
      } else {
        data.append("<unset birthDate>").append("\n");
      }
    }

    if (enableBirthplaceClaim) {
      data.append(IdentityClaim.PLACE_OF_BIRTH.getClaimLabel()).append(": ");
      if (session.getPlaceOfBirth() != null) {
        data.append(session.getPlaceOfBirth()).append("\n");
      } else {
        data.append("<unset placeOfBirth>").append("\n");
      }
    }
    if (enableMrzClaim) {
      data.append(IdentityClaim.MRZ.getClaimLabel()).append(": ");
      if (session.getMrz() != null) {
        data.append(session.getMrz()).append("\n");
      } else {
        data.append("<unset mrz>").append("\n");
      }
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
          if (enableCitizenIdClaim) attributes.add("citizenId");
          if (enableFirstNameClaim) attributes.add("firstName");
          if (enableLastNameClaim) attributes.add("lastName");
          if (enableAvatarNameClaim) attributes.add("avatarName");
          if (enableAvatarPicClaim) attributes.add("avatarPic");
          if (enableBirthDateClaim) attributes.add("birthDate");
          if (enableBirthplaceClaim) attributes.add("placeOfBirth");
          if (enablePhotoClaim) attributes.add("photo");
          if (enableMrzClaim) {
            attributes.add("documentType");
            attributes.add("country");
            attributes.add("firstName");
            attributes.add("lastName");
            attributes.add("birthDate");
            attributes.add("documentNumber");
            attributes.add("photo");
          }
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

    Claim idId = new Claim();
    idId.setName("id");
    idId.setValue(id.getId().toString());
    claims.add(idId);

    if (enableCitizenIdClaim) {
      Claim citizenId = new Claim();
      citizenId.setName("citizenId");
      citizenId.setValue(id.getCitizenId().toString());
      claims.add(citizenId);
    }
    if (enableFirstNameClaim) {
      Claim firstName = new Claim();
      firstName.setName("firstName");
      firstName.setValue(id.getFirstName());
      claims.add(firstName);
    }
    if (enableLastNameClaim) {
      Claim lastName = new Claim();
      lastName.setName("lastName");
      lastName.setValue(id.getLastName());
      claims.add(lastName);
    }
    if (enableAvatarNameClaim) {
      Claim avatarName = new Claim();
      avatarName.setName("avatarName");
      avatarName.setValue(id.getAvatarName());
      claims.add(avatarName);
    }
    if (enableAvatarPicClaim) {

      UUID mediaId = id.getAvatarPic();
      String mimeType = id.getAvatarMimeType();

      if (mediaId == null) {
        logger.error("sendCredential: no media defined for id " + id.getId());
        throw new NoMediaException();
      }

      byte[] imageBytes = mediaResource.render(mediaId);

      if (imageBytes == null) {
        logger.error(
            "sendCredential: datastore returned null value for mediaId "
                + mediaId
                + " id "
                + id.getId());
        throw new NoMediaException();
      }
      if (mimeType == null) {
        mimeType = "image/jpeg";
      }

      logger.info(
          "sendCredential: imageBytes: "
              + imageBytes.length
              + " "
              + id.getAvatarPicCiphIv()
              + " "
              + id.getAvatarPicCiphKey());

      byte[] decrypted =
          Aes256cbc.decrypt(id.getAvatarPicCiphKey(), id.getAvatarPicCiphIv(), imageBytes);
      logger.info("sendCredential: decrypted: " + decrypted.length);

      Claim image = new Claim();
      image.setName("avatarPic");
      String encPhoto = "data:" + mimeType + ";base64," + Base64.encodeBytes(decrypted);

      image.setValue(encPhoto);

      if (debug) {
        logger.info("sendCredential: avatarPic: " + encPhoto);
        logger.info("sendCredential: avatarPic: " + JsonUtil.serialize(image, false));
        logger.info("sendCredential: avatarPic: encPhoto.length: " + encPhoto.length());
      }
      claims.add(image);
    }
    if (enableBirthDateClaim) {
      Claim birthDate = new Claim();
      birthDate.setName("birthDate");
      birthDate.setValue(id.getBirthDate().toString());
      claims.add(birthDate);
    }
    if (enableBirthplaceClaim) {
      Claim placeOfBirth = new Claim();
      placeOfBirth.setName("placeOfBirth");
      placeOfBirth.setValue(id.getPlaceOfBirth());

      claims.add(placeOfBirth);
    }
    if (enableMrzClaim) {
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
          Optional.ofNullable(id.getBirthDate()).map(Object::toString).orElse("null"));
      this.addClaim(
          claims,
          "documentNumber",
          Optional.ofNullable(id.getDocumentNumber()).map(Object::toString).orElse("null"));
      this.addClaim(
          claims, "photo", Optional.ofNullable(null).map(Object::toString).orElse("null"));
    }

    if (enablePhotoClaim) {

      Query q = this.em.createNamedQuery("Media.find");
      q.setParameter("identity", id);
      q.setParameter("type", MediaType.FACE);
      List<UUID> faceMedias = q.getResultList();

      if (faceMedias.size() < 1) {
        logger.error(
            "sendCredential: faceMedias.size() " + faceMedias.size() + " id " + id.getId());
        throw new NoMediaException();
      }
      UUID mediaId = faceMedias.iterator().next();
      byte[] imageBytes = mediaResource.render(mediaId);

      if (imageBytes == null) {
        logger.error(
            "sendCredential: datastore returned null value for mediaId "
                + mediaId
                + " id "
                + id.getId());
        throw new NoMediaException();
      }

      String mimeType = em.find(Media.class, mediaId).getMimeType();
      if (mimeType == null) {
        mimeType = "image/jpeg";
      }

      Claim image = new Claim();
      image.setName("photo");
      String encPhoto = "data:" + mimeType + ";base64," + Base64.encodeBytes(imageBytes);
      image.setValue(encPhoto);

      claims.add(image);

      if (debug) {
        logger.info("sendCredential: photo: " + encPhoto);
        logger.info("sendCredential: photo: " + JsonUtil.serialize(image, false));
      }
    }

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

  private void addClaim(List<Claim> claims, String name, String value) {
    Claim claim = new Claim();
    claim.setName(name);
    claim.setValue(value);
    claims.add(claim);
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

  private Connection getConnection(UUID connectionId) {
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

    try {
      logger.info("userInput: session: " + JsonUtil.serialize(session, false));
    } catch (JsonProcessingException e) {

    }
    switch (token.getType()) {
      case FACE_CAPTURE:
        {
          if (session != null) {
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.CREATE))
                && (identity.getProtection().equals(Protection.FACE))
                && (session.getCreateStep().equals(CreateStep.FACE_CAPTURE))
                && (identity.getProtectedTs() == null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.CREATE))
                && (identity.getProtection().equals(Protection.FINGERPRINTS))
                && (session.getCreateStep().equals(CreateStep.FINGERPRINT_CAPTURE))
                && (identity.getProtectedTs() == null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.CREATE))
                && (identity.getProtection().equals(Protection.WEBRTC))
                && (session.getCreateStep().equals(CreateStep.WEBRTC_CAPTURE))
                && (identity.getProtectedTs() == null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.ISSUE)
                    || session.getType().equals(SessionType.RESTORE))
                && (identity.getProtection().equals(Protection.FACE))
                && (identity.getProtectedTs() != null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.ISSUE)
                    || session.getType().equals(SessionType.RESTORE))
                && (identity.getProtection().equals(Protection.FINGERPRINTS))
                && (identity.getProtectedTs() != null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.ISSUE)
                    || session.getType().equals(SessionType.RESTORE))
                && (identity.getProtection().equals(Protection.WEBRTC))
                && (identity.getProtectedTs() != null)) {

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
    }
  }

  @Transactional
  public void notifyFailure(Token token) throws Exception {
    Identity identity = token.getIdentity();
    Session session = getSession(token.getConnectionId());
    try {
      logger.info("userInput: session: " + JsonUtil.serialize(session, false));
    } catch (JsonProcessingException e) {

    }
    switch (token.getType()) {
      case FACE_CAPTURE:
        {
          if (session != null) {
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.CREATE))
                && (identity.getProtection().equals(Protection.FACE))
                && (session.getCreateStep().equals(CreateStep.FACE_CAPTURE))
                && (identity.getProtectedTs() == null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.CREATE))
                && (identity.getProtection().equals(Protection.FINGERPRINTS))
                && (session.getCreateStep().equals(CreateStep.FINGERPRINT_CAPTURE))
                && (identity.getProtectedTs() == null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.CREATE))
                && (identity.getProtection().equals(Protection.WEBRTC))
                && (session.getCreateStep().equals(CreateStep.WEBRTC_CAPTURE))
                && (identity.getProtectedTs() == null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.ISSUE)
                    || session.getType().equals(SessionType.RESTORE))
                && (identity.getProtection().equals(Protection.FACE))
                && (identity.getProtectedTs() != null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.ISSUE)
                    || session.getType().equals(SessionType.RESTORE))
                && (identity.getProtection().equals(Protection.FINGERPRINTS))
                && (identity.getProtectedTs() != null)) {

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
            if ((session.getType() != null)
                && (session.getType().equals(SessionType.CREATE)
                    || session.getType().equals(SessionType.RESTORE))
                && (identity.getProtection().equals(Protection.WEBRTC))
                && (identity.getProtectedTs() != null)) {

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
    }
  }

  /*
  private static String MEDIA_NO_ATTACHMENT_ERROR = "Received message does not include any attachment.";
  private static String MEDIA_SIZE_ERROR = "Received media is too big. Make sure it is smaller than 5MB.";
  private static String MEDIA_TYPE_ERROR = "Received media is not an image. Accepted: image/jpeg, image/png, image/svg+xml";
  private static String MEDIA_URI_ERROR = "Received media has no URI";
  private static String MEDIA_SAVE_ERROR = "Cannot save Avatar";
  */

  private void saveAvatarPicture(MediaMessage mm, Session session) throws Exception {
    UUID uuid = null;
    String mediaType = null;
    List<MediaItem> items = mm.getItems();

    if (items.size() == 0) {
      logger.info("incomingAvatarPicture: no items");
      messageResource.sendMessage(
          TextMessage.build(
              session.getConnectionId(),
              null,
              getMessage("MEDIA_NO_ATTACHMENT_ERROR", session.getConnectionId())));
      session.setAvatarPic(null);
      session.setAvatarPicCiphAlg(null);
      session.setAvatarPicCiphIv(null);
      session.setAvatarPicCiphKey(null);
      session.setAvatarMimeType(null);
      session.setAvatarURI(null);
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
          // logger.info("saveAvatarPicture: ciphering: " + c.getAlgorithm() + " Key " + p.getKey()
          // + " Iv " + p.getIv());

          if (item.getUri() != null) {

            try {
              byte[] encrypted = this.getMedia(item.getUri());
              byte[] reencrypted = encrypted;

              if (!(mediaType.equals("image/svg+xml"))) {
                byte[] decrypted = Aes256cbc.decrypt(p.getKey(), p.getIv(), encrypted);
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
              mediaResource.createOrUpdate(uuid, 1, null);
              File file = new File(System.getProperty("java.io.tmpdir") + "/" + uuid);

              FileOutputStream fos = new FileOutputStream(file);
              fos.write(reencrypted);
              fos.flush();
              fos.close();

              Resource r = new Resource();
              r.chunk = new FileInputStream(file);
              mediaResource.uploadChunk(uuid, 0, null, r);

              file.delete();

              session.setAvatarMimeType(mediaType);
              session.setAvatarPic(uuid);
              session.setAvatarPicCiphAlg(c.getAlgorithm());
              session.setAvatarPicCiphIv(p.getIv());
              session.setAvatarPicCiphKey(p.getKey());
              session.setAvatarURI(item.getUri());

            } catch (Exception e) {
              logger.error("incomingAvatarPicture", e);
              logger.info("incomingAvatarPicture: could not save avatar");
              messageResource.sendMessage(
                  TextMessage.build(
                      session.getConnectionId(),
                      null,
                      getMessage("MEDIA_SAVE_ERROR", session.getConnectionId())));
              session.setAvatarPic(null);
              session.setAvatarPicCiphAlg(null);
              session.setAvatarPicCiphIv(null);
              session.setAvatarPicCiphKey(null);
              session.setAvatarMimeType(null);
              session.setAvatarURI(null);
              return;
            }
          } else {

            logger.info("incomingAvatarPicture: no uri");
            messageResource.sendMessage(
                TextMessage.build(
                    session.getConnectionId(),
                    null,
                    getMessage("MEDIA_URI_ERROR", session.getConnectionId())));
            session.setAvatarPic(null);
            session.setAvatarPicCiphAlg(null);
            session.setAvatarPicCiphIv(null);
            session.setAvatarPicCiphKey(null);
            session.setAvatarMimeType(null);
            session.setAvatarURI(null);
            return;
          }

        } else {
          logger.info("incomingAvatarPicture: invalid type: " + mediaType);
          messageResource.sendMessage(
              TextMessage.build(
                  session.getConnectionId(),
                  null,
                  getMessage("MEDIA_TYPE_ERROR", session.getConnectionId())));
          session.setAvatarPic(null);
          session.setAvatarPicCiphAlg(null);
          session.setAvatarPicCiphIv(null);
          session.setAvatarPicCiphKey(null);
          session.setAvatarMimeType(null);
          session.setAvatarURI(null);
          return;
        }
      } else {
        logger.info("incomingAvatarPicture: invalid type: " + mediaType);
        messageResource.sendMessage(
            TextMessage.build(
                session.getConnectionId(),
                null,
                getMessage("MEDIA_TYPE_ERROR", session.getConnectionId())));
        session.setAvatarPic(null);
        session.setAvatarPicCiphAlg(null);
        session.setAvatarPicCiphIv(null);
        session.setAvatarPicCiphKey(null);
        session.setAvatarMimeType(null);
        session.setAvatarURI(null);
        return;
      }

    } else {
      // too big too big ;-)
      logger.info("incomingAvatarPicture: no items");
      messageResource.sendMessage(
          TextMessage.build(
              session.getConnectionId(),
              null,
              getMessage("MEDIA_SIZE_ERROR", session.getConnectionId())));
      session.setAvatarPic(null);
      session.setAvatarPicCiphAlg(null);
      session.setAvatarPicCiphIv(null);
      session.setAvatarPicCiphKey(null);
      session.setAvatarMimeType(null);
      session.setAvatarURI(null);
      return;
    }
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
