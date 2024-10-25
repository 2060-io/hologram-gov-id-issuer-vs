package io.unicid.registry.model;

import io.twentysixty.sa.client.enums.Mrz;
import io.twentysixty.sa.client.model.message.mrtd.EMrtdData;
import io.twentysixty.sa.client.model.message.mrtd.MrzData;
import io.unicid.registry.enums.CreateStep;
import io.unicid.registry.enums.IssueStep;
import io.unicid.registry.enums.RestoreStep;
import io.unicid.registry.enums.SessionType;
import io.unicid.registry.utils.DateUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/** The persistent class for the session database table. */
@Entity
@Table(name = "session")
@DynamicUpdate
@DynamicInsert
@NamedQueries({})
@Getter
@Setter
@ToString
public class Session implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private UUID connectionId;

  @ManyToOne
  @JoinColumn(name = "identity_fk")
  private Identity identity;

  private SessionType type;

  private CreateStep createStep;
  private RestoreStep restoreStep;
  private IssueStep issueStep;

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

  @Column(columnDefinition = "text")
  private String avatarURI;

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

  public void updateSessionWithData(Object data, Session session) {
    if (data instanceof MrzData) {
      MrzData mrz = (MrzData) data;
      String format = mrz.getParsed().getFields().get(Mrz.FieldName.BIRTH_DATE).length() == 6 ? "yyMMdd": "yyyyMMdd";
      session.setFirstName(mrz.getParsed().getFields().get(Mrz.FieldName.FIRST_NAME));
      session.setLastName(mrz.getParsed().getFields().get(Mrz.FieldName.LAST_NAME));
      session.setBirthDate(
          DateUtils.parseDateString(
              mrz.getParsed().getFields().get(Mrz.FieldName.BIRTH_DATE), format));
      session.setPlaceOfBirth(mrz.getParsed().getFields().get(Mrz.FieldName.NATIONALITY));
      session.setDocumentType(mrz.getParsed().getFormat().toString());
      session.setDocumentNumber(mrz.getParsed().getFields().get(Mrz.FieldName.DOCUMENT_NUMBER));
      session.setMrz(mrz.getRaw());
    } else if (data instanceof EMrtdData) {
      EMrtdData nfc = (EMrtdData) data;
      String format = nfc.getProcessed().getDateOfBirth().length() == 6 ? "yyMMdd": "yyyyMMdd";
      if (nfc.getProcessed().getFirstName() != null)
        session.setFirstName(nfc.getProcessed().getFirstName());
      if (nfc.getProcessed().getLastName() != null)
        session.setLastName(nfc.getProcessed().getLastName());
      if (isValidDateOfBirth(nfc.getProcessed().getDateOfBirth()))
        session.setBirthDate(
            DateUtils.parseDateString(nfc.getProcessed().getDateOfBirth(), format));
      if (nfc.getProcessed().getNationality() != null)
        session.setPlaceOfBirth(nfc.getProcessed().getNationality());
      if (nfc.getProcessed().getDocumentType() != null)
        session.setDocumentType(nfc.getProcessed().getDocumentType());
      if (nfc.getProcessed().getDocumentNumber() != null)
        session.setDocumentNumber(nfc.getProcessed().getDocumentNumber());
    }
  }

  public static boolean isValidDateOfBirth(String dateOfBirth) {
    return dateOfBirth != null && !dateOfBirth.isEmpty() && !"0".equals(dateOfBirth);
  }
}
