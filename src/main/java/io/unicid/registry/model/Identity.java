package io.unicid.registry.model;

import io.unicid.registry.enums.IdentityClaim;
import io.unicid.registry.enums.Protection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/** The persistent class for the session database table. */
@Entity
@Table(name = "identity")
@DynamicUpdate
@DynamicInsert
@NamedQueries({
  @NamedQuery(
      name = "Identity.findForConnection",
      query =
          "SELECT i FROM Identity i where i.connectionId=:connectionId and (i.deletedTs IS NULL or i.deletedTs>:deletedTs)  ORDER by i.id ASC"),
  @NamedQuery(
      name = "Identity.findForRestorePassword",
      query =
          "SELECT i FROM Identity i where i.connectionId<>:connectionId and (i.deletedTs IS NULL or i.deletedTs>:deletedTs) and i.firstName=:firstName and i.lastName=:lastName and i.birthDate=:birthDate and i.password=:password and i.protection=:protection  ORDER by i.id ASC"),
  @NamedQuery(
      name = "Identity.findForRestoreOthers",
      query =
          "SELECT i FROM Identity i where i.connectionId<>:connectionId and (i.deletedTs IS NULL or i.deletedTs>:deletedTs) and i.firstName=:firstName and i.lastName=:lastName and i.birthDate=:birthDate and i.protection=:protection  ORDER by i.id ASC"),
  // existing: same claims, and not deleted or deleted for less than recoverable period
  @NamedQuery(
      name = "Identity.findExisting",
      query =
          "SELECT i FROM Identity i where i.firstName=:firstName and i.lastName=:lastName and i.birthDate=:birthDate and i.placeOfBirth=:placeOfBirth and (i.deletedTs IS NULL or i.deletedTs>:deletedTs)"),
})
@Getter
@Setter
@ToString
public class Identity implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private UUID id;

  private UUID connectionId;

  @Column(columnDefinition = "timestamptz")
  private Instant startedTs;

  @Column(columnDefinition = "timestamptz")
  private Instant completedTs;

  @Column(columnDefinition = "timestamptz")
  private Instant confirmedTs;

  @Column(columnDefinition = "timestamptz")
  private Instant protectedTs;

  @Column(columnDefinition = "timestamptz")
  private Instant issuedTs;

  @Column(columnDefinition = "timestamptz")
  private Instant revokedTs;

  @Column(columnDefinition = "timestamptz")
  private Instant deletedTs;

  @Column(columnDefinition = "timestamptz")
  private Instant authenticatedTs;

  private IdentityClaim creationStep;
  private IdentityClaim changeStep;

  @Column(columnDefinition = "text")
  private String citizenId;

  @Column(columnDefinition = "text")
  private String firstName;

  @Column(columnDefinition = "text")
  private String lastName;

  @Column(columnDefinition = "text")
  private String avatarName;

  @Column(columnDefinition = "text")
  private String mrz;

  @Column(columnDefinition = "text")
  private String documentType;

  @Column(columnDefinition = "text")
  private String documentNumber;

  private UUID avatarPic;

  private Boolean legacy = false;

  @Column(columnDefinition = "text")
  private String avatarMimeType;

  private Boolean isAvatarPicCiphered;

  @Column(columnDefinition = "text")
  private String avatarPicCiphKey;

  @Column(columnDefinition = "text")
  private String avatarPicCiphIv;

  @Column(columnDefinition = "text")
  private String avatarPicCiphAlg;

  @Column(columnDefinition = "date")
  private LocalDate birthDate;

  @Column(columnDefinition = "text")
  private String placeOfBirth;

  @Column(columnDefinition = "timestamptz")
  private Instant citizenSinceTs;

  private Protection protection;

  @Column(columnDefinition = "text")
  private String password;

  public void clearFields() {
    this.startedTs = null;
    this.completedTs = null;
    this.confirmedTs = null;
    this.protectedTs = null;
    this.issuedTs = null;
    this.revokedTs = null;
    this.deletedTs = null;
    this.authenticatedTs = null;
    this.creationStep = null;
    this.changeStep = null;
    this.citizenId = null;
    this.firstName = null;
    this.lastName = null;
    this.avatarName = null;
    this.mrz = null;
    this.documentType = null;
    this.documentNumber = null;
    this.avatarPic = null;
    this.legacy = null;
    this.avatarMimeType = null;
    this.isAvatarPicCiphered = null;
    this.avatarPicCiphKey = null;
    this.avatarPicCiphIv = null;
    this.avatarPicCiphAlg = null;
    this.birthDate = null;
    this.placeOfBirth = null;
    this.citizenSinceTs = null;
    this.protection = null;
    this.password = null;
  }
}
