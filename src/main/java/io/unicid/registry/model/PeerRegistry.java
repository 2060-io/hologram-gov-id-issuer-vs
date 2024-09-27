package io.unicid.registry.model;

import java.io.Serializable;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import io.unicid.registry.enums.EventNotificationType;
import io.unicid.registry.enums.PeerType;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The persistent class for the call_registry database table.
 * 
 */
@Entity
@Table(name="call_registry")
@DynamicUpdate
@DynamicInsert
@NamedQueries({
	@NamedQuery(name="PeerRegistry.findForIdentity", query="SELECT u FROM PeerRegistry u WHERE u.identity=:identity ORDER by u.id ASC"),
	@NamedQuery(name="PeerRegistry.findForId", query="SELECT u FROM PeerRegistry u WHERE u.id=:id"),
})
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PeerRegistry implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@Id
	private UUID id;
	
	@ManyToOne
	@JoinColumn(name="identity_fk")
	private Identity identity;
	private EventNotificationType event;
	private String roomId;
	private String wsUrl;
	private PeerType type;
	@CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime created;
    @UpdateTimestamp
    private LocalDateTime modified;
    
}
