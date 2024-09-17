package io.unicid.registry.model.dts;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import lombok.Getter;
import lombok.Setter;



/**
 * The persistent class for the session database table.
 *
 */
@Entity
@DynamicUpdate
@DynamicInsert
@NamedQueries({
	@NamedQuery(name="Connection.countConnections", query="SELECT COUNT(s) FROM Connection s WHERE s.deletedTs IS NULL"),
})
@Setter
@Getter
public class Connection implements Serializable {
	private static final long serialVersionUID = 1L;


	@Id
	private UUID id;


	@Column(columnDefinition="text")
	private String avatarName;

	private UUID avatarPic;

	@Column(columnDefinition="timestamptz")
	private Instant createdTs;

	@Column(columnDefinition="timestamptz")
	private Instant deletedTs;



	@Column(columnDefinition="timestamptz")
	private Instant authTs;


	@Column(columnDefinition="timestamptz")
	private Instant lastBcTs;

	@Column(columnDefinition="timestamptz")
	private Instant nextBcTs;


	private Integer sentBcasts;

}