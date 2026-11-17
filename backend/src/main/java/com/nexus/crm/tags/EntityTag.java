package com.nexus.crm.tags;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * One tag on one record. Read-only here: it exists so list queries can ask "which leads have this tag" in a subquery.
 * Writes go through {@link TagService}.
 */
@Entity
@Immutable
@Table(name = "entity_tag")
@IdClass(EntityTag.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EntityTag {

	public record Key(String entityType, UUID entityId, UUID tagId) implements Serializable {
	}

	@Id
	@Column(name = "entity_type")
	private String entityType;

	@Id
	@Column(name = "entity_id")
	private UUID entityId;

	@Id
	@Column(name = "tag_id")
	private UUID tagId;

}
