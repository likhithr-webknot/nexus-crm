package com.nexus.crm.trash;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;
import com.nexus.crm.trash.dto.TrashItem;

/**
 * The recycle bin: records that were deleted can be listed and brought back.
 *
 * <p>
 * Deleted rows are hidden from every entity by {@code @SQLRestriction("deleted_at is null")}, so this class reads
 * and updates them with plain SQL instead. Only managers and admins use it, and they only see records they
 * could have seen before the delete (their own, their team's, or everyone's for an admin).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashService {

	/** The most records one list returns. Older ones are reached by restoring or waiting for the clean-up. */
	static final int LIMIT = 100;

	/** A sentinel for "no IDs", because an empty IN () is not valid SQL. */
	private static final UUID NOBODY = new UUID(0, 0);

	private final JdbcClient jdbc;

	private final UserService users;

	private final AuditService audit;

	@Transactional(readOnly = true)
	public List<TrashItem> list(CurrentUser user, Optional<TrashType> only) {
		Optional<Set<UUID>> visible = this.users.visibleOwnerIds(user);
		List<Row> items = new java.util.ArrayList<>();
		for (TrashType type : TrashType.values()) {
			if (only.isPresent() && only.get() != type) {
				continue;
			}
			items.addAll(read(type, visible));
		}
		var names = this.users.displayNames(items.stream().map(Row::owner).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
		return items.stream()
			.sorted((a, b) -> b.deletedAt().compareTo(a.deletedAt()))
			.limit(LIMIT)
			.map((row) -> new TrashItem(row.type(), row.id(), row.name(), row.deletedAt(), names.get(row.owner())))
			.toList();
	}

	/** Brings a deleted record back. Refused when that would break a rule, with the reason. */
	@Transactional
	public void restore(CurrentUser user, TrashType type, UUID id) {
		Optional<Set<UUID>> visible = this.users.visibleOwnerIds(user);
		Row row = read(type, id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "That record is not in the recycle bin."));
		boolean allowed = visible.map((ids) -> row.owner() == null ? type.ownerMayBeEmpty() : ids.contains(row.owner())).orElse(true);
		if (!allowed) {
			// Same answer as for a record that does not exist, so a manager cannot probe other teams' records.
			throw new ApiException(ErrorCode.NOT_FOUND, "That record is not in the recycle bin.");
		}
		requireParentsExist(type, id);

		// A restore can clash with something created since the delete, for example a new lead with the same
		// email. The database's unique index stops it, and the global handler reports that as 409 "duplicate".
		String updatedBy = "crm_module_record".equals(type.table()) ? "" : "updated_by = :user, ";
		this.jdbc.sql("update " + type.table() + " set deleted_at = null, updated_at = now(), " + updatedBy + "version = version + 1 where id = :id and deleted_at is not null" + type.kindFilter())
			.param("user", user.id())
			.param("id", id)
			.update();
		this.audit.record(user.id(), type.entityName(), id, AuditLog.Action.RESTORE, null);
		log.info("Restored from the recycle bin: type={} id={} by={}", type, id, user.id());
	}

	/** A deal or contact cannot come back while its company is still deleted. */
	private void requireParentsExist(TrashType type, UUID id) {
		if (type != TrashType.DEAL && type != TrashType.CONTACT) {
			return;
		}
		boolean companyDeleted = this.jdbc.sql("select exists (select 1 from company c join " + type.table() + " x on x.company_id = c.id where x.id = :id and c.deleted_at is not null)")
			.param("id", id)
			.query(Boolean.class)
			.single();
		if (companyDeleted) {
			throw new ApiException(ErrorCode.INVALID_STATE, "Restore its company first.");
		}
	}

	private List<Row> read(TrashType type, Optional<Set<UUID>> visible) {
		String scope = visible.isEmpty() ? "true"
				: "(owner_id in (:ids)" + (type.ownerMayBeEmpty() ? " or owner_id is null" : "") + ")";
		Collection<UUID> ids = visible.filter((v) -> !v.isEmpty()).map((v) -> (Collection<UUID>) v).orElse(List.of(NOBODY));
		return this.jdbc.sql("select id, " + type.nameSql() + " as name, deleted_at, owner_id from " + type.table()
				+ " where deleted_at is not null" + type.kindFilter() + " and " + scope + " order by deleted_at desc limit " + LIMIT)
			.param("ids", ids)
			.query((rs, i) -> new Row(type, rs.getObject("id", UUID.class), rs.getString("name"),
					rs.getObject("deleted_at", java.time.OffsetDateTime.class).toInstant(), rs.getObject("owner_id", UUID.class)))
			.list();
	}

	private Optional<Row> read(TrashType type, UUID id) {
		return this.jdbc.sql("select id, " + type.nameSql() + " as name, deleted_at, owner_id from " + type.table()
				+ " where id = :id and deleted_at is not null" + type.kindFilter())
			.param("id", id)
			.query((rs, i) -> new Row(type, rs.getObject("id", UUID.class), rs.getString("name"),
					rs.getObject("deleted_at", java.time.OffsetDateTime.class).toInstant(), rs.getObject("owner_id", UUID.class)))
			.optional();
	}

	private record Row(TrashType type, UUID id, String name, java.time.Instant deletedAt, UUID owner) {
	}

}
