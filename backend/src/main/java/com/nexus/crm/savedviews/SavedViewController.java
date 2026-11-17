package com.nexus.crm.savedviews;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Role;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * Saved list views: a name plus the filters of a list page. A view is private to its owner unless it is shared, and a
 * shared view is visible to everyone who can sign in (the app serves one sales team). Filters are only stored: the list
 * endpoints still check access when the filters are applied.
 */
@RestController
@RequestMapping("/api/v1/saved-views")
@RequiredArgsConstructor
class SavedViewController {

	private static final int MAX_PER_USER = 50;

	private static final int MAX_VALUE_LENGTH = 100;

	/** The filters each list page understands. Anything else is refused, so a view cannot be used to store arbitrary data. */
	private static final Map<String, Set<String>> FILTERS = Map.of(
			"lead", Set.of("q", "source", "status", "ownerId", "view", "tag"),
			"contact", Set.of("q", "ownerId", "tag"),
			"deal", Set.of("q", "stage", "ownerId", "tag"));

	private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final AuditService audit;

	private final JsonMapper json;

	record View(UUID id, String entityType, String name, Map<String, String> filters, boolean shared, boolean mine) {
	}

	record CreateRequest(String entityType, String name, Map<String, String> filters, Boolean shared) {
	}

	record UpdateRequest(String name, Map<String, String> filters, Boolean shared) {
	}

	@GetMapping
	List<View> list(@AuthenticationPrincipal CurrentUser user, @RequestParam String entityType) {
		requireType(entityType);
		return this.jdbc.sql("select id, entity_type, name, filters::text as filters, shared, owner_id from saved_view "
				+ "where entity_type = :type and (owner_id = :user or shared) order by lower(name) limit 200")
			.param("type", entityType)
			.param("user", user.id())
			.query((rs, i) -> new View(rs.getObject("id", UUID.class), rs.getString("entity_type"), rs.getString("name"),
					this.json.readValue(rs.getString("filters"), STRING_MAP), rs.getBoolean("shared"),
					user.id().equals(rs.getObject("owner_id", UUID.class))))
			.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	View create(@AuthenticationPrincipal CurrentUser user, @RequestBody CreateRequest r) {
		if (r == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Give the view a name.");
		}
		requireType(r.entityType());
		String name = cleanName(r.name());
		Map<String, String> filters = cleanFilters(r.entityType(), r.filters());
		long owned = this.jdbc.sql("select count(*) from saved_view where owner_id = :user").param("user", user.id()).query(Long.class).single();
		if (owned >= MAX_PER_USER) {
			throw new ApiException(ErrorCode.INVALID_STATE, "You can keep up to " + MAX_PER_USER + " saved views. Delete one first.");
		}
		UUID id = UUID.randomUUID();
		boolean shared = Boolean.TRUE.equals(r.shared());
		this.jdbc.sql("insert into saved_view (id, owner_id, entity_type, name, filters, shared) values (:id, :user, :type, :name, cast(:filters as jsonb), :shared)")
			.param("id", id).param("user", user.id()).param("type", r.entityType()).param("name", name)
			.param("filters", this.json.writeValueAsString(filters)).param("shared", shared).update();
		this.audit.record(user.id(), "SavedView", id, AuditLog.Action.CREATE, null);
		return new View(id, r.entityType(), name, filters, shared, true);
	}

	@PatchMapping("/{id}")
	@Transactional
	View update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @RequestBody UpdateRequest r) {
		Existing current = find(user, id);
		String name = (r.name() == null) ? current.name() : cleanName(r.name());
		Map<String, String> filters = (r.filters() == null) ? current.filters() : cleanFilters(current.entityType(), r.filters());
		boolean shared = (r.shared() == null) ? current.shared() : r.shared();
		this.jdbc.sql("update saved_view set name = :name, filters = cast(:filters as jsonb), shared = :shared, updated_at = now() where id = :id")
			.param("id", id).param("name", name).param("filters", this.json.writeValueAsString(filters)).param("shared", shared).update();
		this.audit.record(user.id(), "SavedView", id, AuditLog.Action.UPDATE,
				AuditService.changes().field("name", current.name(), name).field("shared", current.shared(), shared));
		return new View(id, current.entityType(), name, filters, shared, user.id().equals(current.ownerId()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void remove(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		find(user, id);
		this.jdbc.sql("delete from saved_view where id = :id").param("id", id).update();
		this.audit.record(user.id(), "SavedView", id, AuditLog.Action.DELETE, null);
	}

	private record Existing(UUID ownerId, String entityType, String name, Map<String, String> filters, boolean shared) {
	}

	/** A view the user may change: their own, or any view when they are an admin. Others get "not found". */
	private Existing find(CurrentUser user, UUID id) {
		Existing found = this.jdbc.sql("select owner_id, entity_type, name, filters::text as filters, shared from saved_view where id = :id")
			.param("id", id)
			.query((rs, i) -> new Existing(rs.getObject("owner_id", UUID.class), rs.getString("entity_type"), rs.getString("name"),
					this.json.readValue(rs.getString("filters"), STRING_MAP), rs.getBoolean("shared")))
			.optional()
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Saved view not found."));
		if (!found.ownerId().equals(user.id()) && user.role() != Role.ADMIN) {
			throw new ApiException(ErrorCode.NOT_FOUND, "Saved view not found.");
		}
		return found;
	}

	private static void requireType(String type) {
		if (type == null || !FILTERS.containsKey(type)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose leads, contacts or deals.");
		}
	}

	private static String cleanName(String name) {
		String clean = (name == null) ? "" : name.strip();
		if (clean.isEmpty() || clean.length() > 60) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Give the view a name of up to 60 characters.");
		}
		return clean;
	}

	private Map<String, String> cleanFilters(String type, Map<String, String> filters) {
		Map<String, String> clean = new LinkedHashMap<>();
		if (filters != null) {
			filters.forEach((key, value) -> {
				if (!FILTERS.get(type).contains(key)) {
					throw new ApiException(ErrorCode.VALIDATION_FAILED, "\"" + key + "\" cannot be saved in a " + type + " view.");
				}
				if (value != null && !value.isBlank()) {
					if (value.length() > MAX_VALUE_LENGTH) {
						throw new ApiException(ErrorCode.VALIDATION_FAILED, "A filter value is too long.");
					}
					clean.put(key, value.strip());
				}
			});
		}
		requireKnownReferences(clean);
		return clean;
	}

	/**
	 * A view stores ids (a tag, a person), so it must not point at something that does not exist: it would silently show
	 * an empty list. The words "me" and "unassigned" are allowed for the owner. Deleting a tag later removes it from the
	 * views that used it (see {@code TagService.delete}).
	 */
	private void requireKnownReferences(Map<String, String> filters) {
		String tag = filters.get("tag");
		if (tag != null && !exists("select count(*) from tag where id = :id", tag)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "That tag no longer exists. Choose another tag.");
		}
		String owner = filters.get("ownerId");
		boolean words = "me".equals(owner) || "unassigned".equals(owner);
		if (owner != null && !words && !exists("select count(*) from app_user where id = :id", owner)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "That person does not exist. Choose someone else.");
		}
	}

	/** True when the text is an id that the query finds. Text that is not an id counts as not found. */
	private boolean exists(String countQuery, String id) {
		UUID parsed;
		try {
			parsed = UUID.fromString(id);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
		return this.jdbc.sql(countQuery).param("id", parsed).query(Long.class).single() > 0;
	}

}
