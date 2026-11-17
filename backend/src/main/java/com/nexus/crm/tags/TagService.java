package com.nexus.crm.tags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;
import com.nexus.crm.tags.dto.TagRef;

/**
 * Tags and the records they sit on. This class never looks at leads, contacts or deals itself: the caller must already
 * have checked that the user may change those records.
 */
@Service
@RequiredArgsConstructor
public class TagService {

	public static final Set<String> TYPES = Set.of("lead", "contact", "company", "deal");

	private static final Set<String> COLORS = Set.of("neutral", "brand", "violet", "green", "amber", "red");

	private static final int MAX_TAGS = 200;

	private final JdbcClient jdbc;

	private final AuditService audit;

	@Transactional(readOnly = true)
	public List<TagRef> list() {
		return this.jdbc.sql("select id, name, color from tag order by name")
			.query((rs, i) -> new TagRef(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("color")))
			.list();
	}

	@Transactional
	public TagRef create(CurrentUser user, String name, String color) {
		String clean = (name == null) ? "" : name.strip().replaceAll("\\s+", " ");
		if (clean.isEmpty() || clean.length() > 40) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Give the tag a name of up to 40 characters.");
		}
		String tone = (color == null || color.isBlank()) ? "neutral" : color;
		if (!COLORS.contains(tone)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose one of the listed tag colors.");
		}
		long count = this.jdbc.sql("select count(*) from tag").query(Long.class).single();
		if (count >= MAX_TAGS) {
			throw new ApiException(ErrorCode.INVALID_STATE, "There can be at most " + MAX_TAGS + " tags. Delete one you no longer use.");
		}
		UUID id = UUID.randomUUID();
		try {
			this.jdbc.sql("insert into tag (id, name, color, created_by) values (:id, :name, :color, :user)")
				.param("id", id).param("name", clean).param("color", tone).param("user", user.id()).update();
		}
		catch (DuplicateKeyException ex) {
			throw new ApiException(ErrorCode.DUPLICATE, "A tag with this name already exists.");
		}
		this.audit.record(user.id(), "Tag", id, AuditLog.Action.CREATE, null);
		return new TagRef(id, clean, tone);
	}

	/** Deletes a tag and takes it off every record. */
	@Transactional
	public void delete(CurrentUser user, UUID id) {
		int removed = this.jdbc.sql("delete from tag where id = :id").param("id", id).update();
		if (removed == 0) {
			throw new ApiException(ErrorCode.NOT_FOUND, "Tag not found.");
		}
		this.audit.record(user.id(), "Tag", id, AuditLog.Action.DELETE, null);
	}

	/** The tags on each of the given records, read in one query. Records without tags are not in the map. */
	@Transactional(readOnly = true)
	public Map<UUID, List<TagRef>> tagsFor(String entityType, Collection<UUID> ids) {
		requireType(entityType);
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<UUID, List<TagRef>> byRecord = new HashMap<>();
		this.jdbc.sql("select et.entity_id, t.id, t.name, t.color from entity_tag et join tag t on t.id = et.tag_id "
				+ "where et.entity_type = :type and et.entity_id in (:ids) order by t.name")
			.param("type", entityType)
			.param("ids", ids)
			.query((rs, i) -> {
				byRecord.computeIfAbsent(rs.getObject("entity_id", UUID.class), (k) -> new ArrayList<>())
					.add(new TagRef(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("color")));
				return null;
			})
			.list();
		return byRecord;
	}

	/**
	 * Adds and removes tags on records. All of them change or none do, and each record gets its own audit entry. The
	 * caller must have checked access to every record first.
	 */
	@Transactional
	public void change(CurrentUser user, String entityType, Collection<UUID> records, Collection<UUID> add, Collection<UUID> remove) {
		requireType(entityType);
		Set<UUID> ids = new LinkedHashSet<>(records);
		Set<UUID> toAdd = (add == null) ? new LinkedHashSet<>() : new LinkedHashSet<>(add);
		Set<UUID> toRemove = (remove == null) ? new LinkedHashSet<>() : new LinkedHashSet<>(remove);
		if (toAdd.isEmpty() && toRemove.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a tag to add or remove.");
		}
		// A tag in both lists ends up removed.
		toAdd.removeAll(toRemove);
		requireTagsExist(toAdd);
		requireTagsExist(toRemove);

		Map<UUID, List<TagRef>> before = tagsFor(entityType, ids);
		// The table name comes from a fixed list (see requireType), never from the request.
		for (UUID tag : toAdd) {
			this.jdbc.sql("insert into entity_tag (entity_type, entity_id, tag_id) select :type, r.id, :tag from " + entityType
					+ " r where r.id in (:ids) on conflict do nothing")
				.param("type", entityType).param("tag", tag).param("ids", ids).update();
		}
		if (!toRemove.isEmpty()) {
			this.jdbc.sql("delete from entity_tag where entity_type = :type and entity_id in (:ids) and tag_id in (:tags)")
				.param("type", entityType).param("ids", ids).param("tags", toRemove).update();
		}
		Map<UUID, List<TagRef>> after = tagsFor(entityType, ids);
		String entityName = entityName(entityType);
		for (UUID id : ids) {
			this.audit.record(user.id(), entityName, id, AuditLog.Action.UPDATE,
					AuditService.changes().field("tags", names(before.get(id)), names(after.get(id))));
		}
	}

	/** Limits a list query to records that carry the tag. Pass a null tag to leave the query unchanged. */
	public static <T> Specification<T> hasTag(String entityType, UUID tagId) {
		return (root, query, cb) -> {
			if (tagId == null) {
				return cb.conjunction();
			}
			var tagged = query.subquery(UUID.class);
			var link = tagged.from(EntityTag.class);
			tagged.select(link.get("entityId")).where(cb.equal(link.get("entityType"), entityType), cb.equal(link.get("tagId"), tagId));
			return root.get("id").in(tagged);
		};
	}

	private void requireTagsExist(Collection<UUID> tags) {
		if (tags.isEmpty()) {
			return;
		}
		long found = this.jdbc.sql("select count(*) from tag where id in (:ids)").param("ids", tags).query(Long.class).single();
		if (found != tags.size()) {
			throw new ApiException(ErrorCode.NOT_FOUND, "Tag not found.");
		}
	}

	private static List<String> names(List<TagRef> tags) {
		return (tags == null) ? List.of() : tags.stream().map(TagRef::name).toList();
	}

	private static String entityName(String entityType) {
		return Character.toUpperCase(entityType.charAt(0)) + entityType.substring(1);
	}

	private static void requireType(String entityType) {
		if (!TYPES.contains(entityType)) {
			throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Unknown record type.");
		}
	}

}
