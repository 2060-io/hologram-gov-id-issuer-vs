package io.unicid.registry.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import io.unicid.registry.enums.TokenType;


/**
 * The persistent class for the session database table.
 * 
 */
@Entity
@Table(name="token")
@DynamicUpdate
@DynamicInsert
@NamedQueries({
	@NamedQuery(name="Token.findForConnection", query="SELECT t FROM Token t where t.connectionId=:connectionId and t.type=:type "),
	
})
@Setter
@Getter
public class Token implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@Id
	private UUID id;
	
	@Column(columnDefinition="timestamptz")
	private Instant expireTs;

	
	@ManyToOne
	@JoinColumn(name="identity_fk")
	private Identity identity;
	
	
	
	
	private UUID connectionId;
	private UUID threadId;
	
	private TokenType type;
	
	
}