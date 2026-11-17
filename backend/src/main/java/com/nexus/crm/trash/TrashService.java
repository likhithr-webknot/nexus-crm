package com.nexus.crm.trash;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

	/** The most records one list returns. */
	static final int LIMIT = 100;

	/** How long a deleted record stays in the bin. */
	static final int RETENTION_DAYS = 30;

	/** Most records of one type removed per night. The rest wait for the next night. */
	private static final int PURGE_BATCH = 1000;

	/** Children before parents, so a company goes after its deals and contacts. */
	private static final List<TrashType> PURGE_ORDER = List.of(TrashType.DEAL, TrashType.CONTACT, TrashType.LEAD, TrashType.COMPANY,
			TrashType.CAMPAIGN, TrashType.PRODUCT, TrashType.QUOTE, TrashType.COMMUNICATION, TrashType.AUTOMATION);

	/** A sentinel for "no IDs", because an empty IN () is not valid SQL. */
	private static final UUID NOBODY = new UUID(0, 0);

	private final JdbcClient jdbc;

	private final UserService users;

	private final AuditService audit;

	private final TransactionTemplate transactions;

	@Transactional(readOnly = true)
	public TrashPage list(CurrentUser user, Optional<TrashType> only) {
		Optional<Set<UUID>> visible = this.users.visibleOwnerIds(user);
		List<Row> items = new ArrayList<>();
		long total = 0;
		for (TrashType type : TrashType.values()) {
			if (only.isPresent() && only.get() != type) {
				continue;
			}
			items.addAll(read(type, visible));
			total += count(type, visible);
		}
		var names = this.users.displayNames(items.stream().map(Row::owner).filter(Objects::nonNull).collect(Collectors.toSet()));
		List<TrashItem> shown = items.stream()
			.sorted((a, b) -> b.deletedAt().compareTo(a.deletedAt()))
			.limit(LIMIT)
			.map((row) -> new TrashItem(row.type(), row.id(), row.name(), row.deletedAt(), names.get(row.owner())))
			.toList();
		return new TrashPage(shown, total, RETENTION_DAYS);
	}

	/** The newest deleted records, plus how many there are in all, so the screen can say when the list is cut short. */
	public record TrashPage(List<TrashItem> items, long total, int retentionDays) {
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

	private long count(TrashType type, Optional<Set<UUID>> visible) {
		String scope = visible.isEmpty() ? "true"
				: "(owner_id in (:ids)" + (type.ownerMayBeEmpty() ? " or owner_id is null" : "") + ")";
		Collection<UUID> ids = visible.filter((v) -> !v.isEmpty()).map((v) -> (Collection<UUID>) v).orElse(List.of(NOBODY));
		return this.jdbc.sql("select count(*) from " + type.table() + " where deleted_at is not null" + type.kindFilter() + " and " + scope)
			.param("ids", ids)
			.query(Long.class)
			.single();
	}

	/** Every night: remove records that have been in the bin longer than {@link #RETENTION_DAYS} days. */
	@Scheduled(cron = "0 45 3 * * *", zone = "UTC")
	public void purgeExpired() {
		int removed = purgeDeletedBefore(Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS));
		log.info("Recycle bin clean-up: {} record(s) removed", removed);
	}

	/**
	 * Deletes records that were deleted before {@code cutoff}, one transaction per record. Something may still use a
	 * record (a company that still has a deal). That record stays and is tried again the next night, and the rest go on.
	 */
	int purgeDeletedBefore(Instant cutoff) {
		OffsetDateTime before = OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC);
		int removed = 0;
		int blocked = 0;
		for (TrashType type : PURGE_ORDER) {
			List<UUID> expired = this.jdbc.sql("select id from " + type.table()
					+ " where deleted_at < :before" + type.kindFilter() + " order by deleted_at limit " + PURGE_BATCH)
				.param("before", before)
				.query(UUID.class)
				.list();
			for (UUID id : expired) {
				try {
					if (Boolean.TRUE.equals(this.transactions.execute((tx) -> purge(type, id, before)))) {
						removed++;
					}
				}
				catch (DataIntegrityViolationException stillInUse) {
					blocked++;
					log.info("Recycle bin clean-up: {} {} is still in use, keeping it for now", type, id);
				}
			}
		}
		if (blocked > 0) {
			log.warn("Recycle bin clean-up: {} record(s) are still in use and were kept", blocked);
		}
		return removed;
	}

	/** Deletes one record. Returns false if it was restored in the meantime. */
	private boolean purge(TrashType type, UUID id, OffsetDateTime before) {
		// Lock the row first, so a restore that is happening right now wins.
		boolean stillExpired = this.jdbc.sql("select id from " + type.table()
				+ " where id = :id and deleted_at < :before" + type.kindFilter() + " for update")
			.param("id", id)
			.param("before", before)
			.query(UUID.class)
			.optional()
			.isPresent();
		if (!stillExpired) {
			return false;
		}
		removeLinks(type, id);
		this.jdbc.sql("delete from " + type.table() + " where id = :id").param("id", id).update();
		// A scheduled job did this, so there is no user to put in the audit log.
		this.audit.record(null, type.entityName(), id, AuditLog.Action.DELETE, null);
		return true;
	}

	/**
	 * Clears everything that points at the record, so the delete is not refused. History rows go with it. Rows that
	 * belong to other records stay and only lose the link.
	 */
	private void removeLinks(TrashType type, UUID id) {
		switch (type) {
			case DEAL -> {
				exec("delete from deal_stage_history where deal_id = :id", id);
				detachNotes("deal_id", "lead_id is null and contact_id is null", id);
				unlink("lead", "converted_deal_id", id);
			}
			case CONTACT -> {
				detachNotes("contact_id", "lead_id is null and deal_id is null", id);
				unlink("deal", "primary_contact_id", id);
				unlink("lead", "converted_contact_id", id);
				exec("delete from ai_research where kind = 'contact' and record_id = :id", id);
			}
			case LEAD -> {
				exec("delete from lead_status_history where lead_id = :id", id);
				detachNotes("lead_id", "contact_id is null and deal_id is null", id);
				unlink("deal", "lead_id", id);
			}
			case COMPANY -> {
				unlink("contact", "company_id", id);
				unlink("lead", "converted_company_id", id);
				exec("delete from ai_research where kind = 'company' and record_id = :id", id);
			}
			default -> exec("delete from automation_run where workflow_id = :id", id);
		}
	}

	/** Activities and follow-ups for this record. Those that belong to nothing else are deleted. The rest lose this link. */
	private void detachNotes(String column, String ownedOnlyByThis, UUID id) {
		for (String table : List.of("activity", "follow_up")) {
			exec("delete from " + table + " where " + column + " = :id and " + ownedOnlyByThis, id);
			unlink(table, column, id);
		}
	}

	/** Clears one link. Bumps the version so someone editing the row at that moment gets a conflict instead of undoing it. */
	private void unlink(String table, String column, UUID id) {
		exec("update " + table + " set " + column + " = null, updated_at = now(), version = version + 1 where " + column + " = :id", id);
	}

	private void exec(String sql, UUID id) {
		this.jdbc.sql(sql).param("id", id).update();
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
