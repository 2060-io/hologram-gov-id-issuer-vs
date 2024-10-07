package io.unicid.registry.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import io.twentysixty.sa.client.enums.Mrz;
import io.twentysixty.sa.client.model.message.mrtd.MrzDataSubmitMessage;
import io.unicid.registry.enums.CreateStep;
import io.unicid.registry.enums.IssueStep;
import io.unicid.registry.enums.RestoreStep;
import io.unicid.registry.enums.SessionType;


/**
 * The persistent class for the session database table.
 * 
 */
@Entity
@Table(name="session")
@DynamicUpdate
@DynamicInsert
@NamedQueries({
	
})
@Getter
@Setter
@ToString
public class Session implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@Id
	private UUID connectionId;
	
	@ManyToOne
	@JoinColumn(name="identity_fk")
	private Identity identity;
	
	
	private SessionType type;
	
	private CreateStep createStep;
	private RestoreStep restoreStep;
	private IssueStep issueStep;
	
	
	@Column(columnDefinition="text")
	private String citizenId;
	@Column(columnDefinition="text")
	private String firstName;
	@Column(columnDefinition="text")
	private String lastName;
	@Column(columnDefinition="text")
	private String avatarName;
	@Column(columnDefinition="text")
	private String mrz;
	private Mrz.Format documentType;
	@Column(columnDefinition="text")
	private String documentNumber;
	private UUID avatarPic;
	@Column(columnDefinition="text")
	private String avatarURI;
	
	
	@Column(columnDefinition="text")
	private String avatarMimeType;
	
	@Column(columnDefinition="text")
	private String avatarPicCiphKey;
	@Column(columnDefinition="text")
	private String avatarPicCiphIv;
	
	@Column(columnDefinition="text")
	private String avatarPicCiphAlg;
	
	
	@Column(columnDefinition="date")
	private LocalDate birthDate;
	@Column(columnDefinition="text")
	private String placeOfBirth;

	public void updateSessionWithMrzData(MrzDataSubmitMessage mrz, Session session) {
        session.setFirstName(mrz.getMrzData().getParsed().getFields().get(Mrz.FieldName.FIRST_NAME));
        session.setLastName(mrz.getMrzData().getParsed().getFields().get(Mrz.FieldName.LAST_NAME));
        session.setPlaceOfBirth(mrz.getMrzData().getParsed().getFields().get(Mrz.FieldName.NATIONALITY));
		session.setDocumentType(mrz.getMrzData().getParsed().getFormat());
		session.setDocumentNumber(mrz.getMrzData().getParsed().getFields().get(Mrz.FieldName.DOCUMENT_NUMBER));
        session.setMrz(mrz.getMrzData().getRaw());
    }

	
}