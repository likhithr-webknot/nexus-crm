package com.nexus.crm.modules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Emails;
import com.nexus.crm.shared.Search;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;
import com.nexus.crm.shared.web.PageResponse;

/**
 * The workspaces that share one table ({@code crm_module_record}): campaigns, products, quotes, communications and
 * automations. Each kind has the same few columns, and keeps anything extra (quote lines, email addresses, workflow steps)
 * in a JSON {@code details} column. This class reads and writes them and checks what the user sent.
 */
@Service
@RequiredArgsConstructor
public class ModuleService {

	/** Most addresses allowed in Cc, and in Bcc. */
	static final int MAX_COPIES = 20;

	private static final int MAX_NAME_LENGTH = 160;

	private static final int MAX_DESCRIPTION_LENGTH = 10_000;

	private static final int MAX_EMAIL_BODY_LENGTH = 50_000;

	private static final int MAX_QUOTE_LINES = 100;

	private static final int MAX_WORKFLOW_STEPS = 50;

	private static final int MAX_WORKFLOW_CONNECTIONS = 100;

	private static final int MAX_REQUEST_KEY_LENGTH = 100;

	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

	/** Unsent drafts older than this are deleted by a nightly job. */
	private static final Duration DRAFT_KEPT_FOR = Duration.ofDays(30);

	/** Stands in for "nobody" because an empty IN () is not valid SQL. */
	private static final UUID NOBODY = new UUID(0, 0);

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final UserService users;

	private final AuditService audit;

	// ---- reading ----

	/** One page of a workspace. {@code total} lets the screen say when there is more than one page. */
	@Transactional(readOnly = true)
	public PageResponse<ModuleRecord> list(CurrentUser user, ModuleKind kind, String q, String status, boolean byName, int page,
			int size) {
		Map<String, Object> params = new HashMap<>();
		params.put("kind", kind.path());
		StringBuilder from = new StringBuilder(
				"from crm_module_record m join app_user u on u.id = m.owner_id where m.kind = :kind and m.deleted_at is null");
		limitToVisible(from, params, visibleOwners(user, kind));
		if (status != null && !status.isBlank()) {
			from.append(" and m.status = :status");
			params.put("status", status);
		}
		if (q != null && !q.isBlank()) {
			from.append(" and lower(concat_ws(' ', m.name, m.account, u.display_name)) like :q escape '\\'");
			params.put("q", Search.pattern(q));
		}

		int pageNumber = Math.max(1, page);
		String order = byName ? "lower(m.name), m.id" : "m.updated_at desc, m.id";
		long total = this.jdbc.sql("select count(*) " + from).params(params).query(Long.class).single();
		List<ModuleRecord> items = this.jdbc.sql("select m.*, u.display_name " + from + " order by " + order + " limit :size offset :offset")
			.params(params)
			.param("size", size)
			.param("offset", (long) (pageNumber - 1) * size)
			.query((rs, row) -> map(rs))
			.list();
		return new PageResponse<>(items, total, pageNumber, size);
	}

	@Transactional(readOnly = true)
	public ModuleRecord get(CurrentUser user, ModuleKind kind, UUID id) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", id);
		params.put("kind", kind.path());
		StringBuilder sql = new StringBuilder("select m.*, u.display_name from crm_module_record m join app_user u on u.id = m.owner_id "
				+ "where m.id = :id and m.kind = :kind and m.deleted_at is null");
		limitToVisible(sql, params, visibleOwners(user, kind));
		return this.jdbc.sql(sql.toString())
			.params(params)
			.query((rs, row) -> map(rs))
			.optional()
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Record not found."));
	}

	/** Whose records the user can see. Empty means everyone's. Products are shared by the whole team. */
	private Optional<Set<UUID>> visibleOwners(CurrentUser user, ModuleKind kind) {
		return (kind == ModuleKind.PRODUCTS) ? Optional.empty() : this.users.visibleOwnerIds(user);
	}

	private void limitToVisible(StringBuilder sql, Map<String, Object> params, Optional<Set<UUID>> visible) {
		visible.ifPresent((ids) -> {
			sql.append(" and m.owner_id in (:ids)");
			// An empty IN () is not valid SQL, so a user with nobody in scope matches no one.
			params.put("ids", ids.isEmpty() ? Set.of(NOBODY) : ids);
		});
	}

	// ---- changing ----

	/** Creates a record. A repeated request with the same {@code key} returns the record made the first time. */
	@Transactional
	public ModuleRecord create(CurrentUser user, ModuleKind kind, ModuleInput input, String key) {
		if (key != null && !key.isBlank()) {
			if (key.length() > MAX_REQUEST_KEY_LENGTH) {
				throw invalid("Idempotency key is too long.");
			}
			Optional<UUID> earlier = this.jdbc.sql("select id from crm_module_record where kind = :kind and owner_id = :owner and request_key = :key")
				.param("kind", kind.path())
				.param("owner", user.id())
				.param("key", key)
				.query(UUID.class)
				.optional();
			if (earlier.isPresent()) {
				return get(user, kind, earlier.get());
			}
		}

		Fields fields = validate(kind, input);
		UUID id = UUID.randomUUID();
		this.jdbc.sql("""
				insert into crm_module_record (id, kind, name, status, owner_id, account, description, amount, details, request_key)
				values (:id, :kind, :name, :status, :owner, :account, :description, :amount, cast(:details as jsonb), :key)
				""")
			.param("id", id)
			.param("kind", kind.path())
			.param("name", fields.name())
			.param("status", fields.status())
			.param("owner", user.id())
			.param("account", fields.account())
			.param("description", fields.description())
			.param("amount", fields.amount())
			.param("details", this.json.writeValueAsString(fields.details()))
			.param("key", key)
			.update();
		this.audit.record(user.id(), kind.entity(), id, AuditLog.Action.CREATE, null);
		return get(user, kind, id);
	}

	@Transactional
	public ModuleRecord update(CurrentUser user, ModuleKind kind, UUID id, ModuleInput input) {
		ModuleRecord existing = get(user, kind, id);
		if (kind == ModuleKind.COMMUNICATIONS && !Set.of("Draft", "Failed").contains(existing.status())) {
			throw invalid("A sent or unconfirmed email cannot be edited. Create a new draft instead.");
		}
		if (input.version() == null || input.version() != existing.version()) {
			throw changedByAnotherUser();
		}

		Fields fields = validate(kind, input);
		int changed = this.jdbc.sql("""
				update crm_module_record
				set name = :name, status = :status, account = :account, description = :description, amount = :amount,
				    details = cast(:details as jsonb), updated_at = now(), version = version + 1
				where id = :id and version = :version
				""")
			.param("name", fields.name())
			.param("status", fields.status())
			.param("account", fields.account())
			.param("description", fields.description())
			.param("amount", fields.amount())
			.param("details", this.json.writeValueAsString(fields.details()))
			.param("id", id)
			.param("version", existing.version())
			.update();
		if (changed != 1) {
			throw changedByAnotherUser();
		}
		this.audit.record(user.id(), kind.entity(), id, AuditLog.Action.UPDATE, null);
		return get(user, kind, id);
	}

	/** Moves the record to the recycle bin. */
	@Transactional
	public void archive(CurrentUser user, ModuleKind kind, UUID id) {
		get(user, kind, id);
		this.jdbc.sql("update crm_module_record set deleted_at = now(), updated_at = now(), version = version + 1 where id = :id")
			.param("id", id)
			.update();
		this.audit.record(user.id(), kind.entity(), id, AuditLog.Action.DELETE, null);
	}

	@Scheduled(cron = "0 30 3 * * *", zone = "UTC")
	@Transactional
	public void purgeCommunicationDrafts() {
		OffsetDateTime cutoff = OffsetDateTime.ofInstant(Instant.now().minus(DRAFT_KEPT_FOR), ZoneOffset.UTC);
		this.jdbc.sql("delete from crm_module_record where kind = 'communications' and status = 'Draft' and created_at < :cutoff")
			.param("cutoff", cutoff)
			.update();
	}

	// ---- checking what the user sent ----

	/** The columns every kind shares, after checking. {@code details} holds what is specific to the kind. */
	private record Fields(String name, String status, String account, String description, BigDecimal amount, Map<String, Object> details) {
	}

	private Fields validate(ModuleKind kind, ModuleInput input) {
		if (input == null) {
			throw invalid("A record is required.");
		}
		String name = trim(input.name(), MAX_NAME_LENGTH);
		if (name.length() < 2) {
			throw invalid("Enter a name of at least two characters.");
		}
		String status = input.status();
		if (!kind.allows(status)) {
			throw invalid("Choose a valid status.");
		}
		if (kind == ModuleKind.COMMUNICATIONS && !"Draft".equals(status)) {
			throw invalid("Use Send to deliver a communication.");
		}
		String account = trim(input.account(), MAX_NAME_LENGTH);
		String description = trim(input.description(), MAX_DESCRIPTION_LENGTH);
		BigDecimal amount = (input.amount() == null) ? BigDecimal.ZERO : input.amount();
		if (amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0) {
			throw invalid("Enter a valid nonnegative amount with two decimal places.");
		}

		// Each kind keeps its own extra fields in "details". Quotes work out their own amount, the others have none.
		Map<String, Object> details = new HashMap<>();
		switch (kind) {
			case QUOTES -> amount = validateQuote(input, details);
			case AUTOMATIONS -> {
				validateAutomation(input, status, details);
				amount = BigDecimal.ZERO;
			}
			case COMMUNICATIONS -> {
				validateEmail(input, details);
				amount = BigDecimal.ZERO;
			}
			default -> {
			}
		}
		return new Fields(name, status, account, description, amount, details);
	}

	/** Checks the lines and returns the total, worked out here so the client cannot send a different one. */
	private BigDecimal validateQuote(ModuleInput input, Map<String, Object> details) {
		List<Map<String, Object>> lines = (input.lineItems() == null) ? List.of() : input.lineItems();
		if (lines.size() > MAX_QUOTE_LINES) {
			throw invalid("A quote can have at most " + MAX_QUOTE_LINES + " lines.");
		}

		BigDecimal total = BigDecimal.ZERO;
		for (Map<String, Object> line : lines) {
			BigDecimal quantity = decimal(line.get("quantity"));
			BigDecimal price = decimal(line.get("unitPrice"));
			BigDecimal discountPercent = decimal(line.get("discountPct"));
			BigDecimal taxPercent = decimal(line.get("taxPct"));

			boolean valid = quantity.signum() > 0 && price.signum() >= 0 && isPercent(discountPercent) && isPercent(taxPercent);
			if (!valid) {
				throw invalid("A quote line has an invalid quantity, price, discount or tax.");
			}
			BigDecimal afterDiscount = quantity.multiply(price).multiply(BigDecimal.ONE.subtract(discountPercent.movePointLeft(2)));
			total = total.add(afterDiscount.multiply(BigDecimal.ONE.add(taxPercent.movePointLeft(2))));
		}
		details.put("lineItems", lines);
		return total.setScale(2, RoundingMode.HALF_UP);
	}

	private static boolean isPercent(BigDecimal value) {
		return value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0;
	}

	private void validateAutomation(ModuleInput input, String status, Map<String, Object> details) {
		List<Map<String, Object>> nodes = (input.flowNodes() == null) ? List.of() : input.flowNodes();
		List<Map<String, Object>> edges = (input.flowEdges() == null) ? List.of() : input.flowEdges();
		if (nodes.size() > MAX_WORKFLOW_STEPS || edges.size() > MAX_WORKFLOW_CONNECTIONS) {
			throw invalid("Workflow is too large.");
		}
		if ("Published".equals(status)) {
			WorkflowValidator.validate(nodes, edges);
		}
		details.put("flowNodes", nodes);
		details.put("flowEdges", edges);
	}

	private void validateEmail(ModuleInput input, Map<String, Object> details) {
		String to = trim(input.recipient(), 254).toLowerCase(Locale.ROOT);
		if (!to.isEmpty() && !Emails.isValid(to)) {
			throw invalid("Enter one valid email address in To.");
		}
		List<String> cc = addresses("Cc", input.cc());
		List<String> bcc = addresses("Bcc", input.bcc());
		rejectRepeats(to, cc, bcc);

		details.put("recipient", to);
		details.put("cc", cc);
		details.put("bcc", bcc);
		details.put("bodyHtml", trim(input.bodyHtml(), MAX_EMAIL_BODY_LENGTH));
	}

	/** A Cc or Bcc list: valid addresses only, lower-cased, repeats dropped, at most {@link #MAX_COPIES}. */
	private List<String> addresses(String label, List<String> given) {
		if (given == null) {
			return List.of();
		}
		Set<String> clean = new LinkedHashSet<>();
		for (String raw : given) {
			String address = (raw == null) ? "" : raw.strip();
			if (address.isEmpty()) {
				continue;
			}
			if (!Emails.isValid(address)) {
				throw invalid(label + " has an address that is not a valid email: " + shorten(address));
			}
			clean.add(address.toLowerCase(Locale.ROOT));
		}
		if (clean.size() > MAX_COPIES) {
			throw invalid(label + " can have at most " + MAX_COPIES + " addresses.");
		}
		return List.copyOf(clean);
	}

	/** The same address in two fields would reach the person twice, and a Bcc address also in Cc would not stay hidden. */
	private void rejectRepeats(String to, List<String> cc, List<String> bcc) {
		for (String address : bcc) {
			if (address.equals(to) || cc.contains(address)) {
				throw invalid(address + " is listed in Bcc and also in To or Cc.");
			}
		}
		for (String address : cc) {
			if (address.equals(to)) {
				throw invalid(address + " is listed in both To and Cc.");
			}
		}
	}

	// ---- turning a row into a record ----

	private ModuleRecord map(ResultSet rs) throws SQLException {
		Map<String, Object> details = readDetails(rs.getString("details"));
		return new ModuleRecord(
				rs.getObject("id", UUID.class),
				rs.getString("name"),
				rs.getString("status"),
				rs.getString("display_name"),
				rs.getString("account"),
				rs.getString("description"),
				rs.getBigDecimal("amount"),
				rs.getObject("created_at", OffsetDateTime.class).toInstant(),
				rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
				rs.getInt("version"),
				maps(details.get("lineItems")),
				maps(details.get("flowNodes")),
				maps(details.get("flowEdges")),
				(String) details.get("recipient"),
				strings(details.get("cc")),
				strings(details.get("bcc")),
				(String) details.get("bodyHtml"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readDetails(String text) {
		return this.json.readValue(text, Map.class);
	}

	private static List<String> strings(Object value) {
		return (value instanceof List<?> items) ? items.stream().map(String::valueOf).toList() : List.of();
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> maps(Object value) {
		return (value instanceof List<?>) ? (List<Map<String, Object>>) value : List.of();
	}

	// ---- small helpers ----

	private static String trim(String value, int max) {
		String result = (value == null) ? "" : value.strip();
		if (result.length() > max) {
			throw invalid("A field is too long.");
		}
		return result;
	}

	private static BigDecimal decimal(Object value) {
		try {
			return new BigDecimal(String.valueOf(value));
		}
		catch (NumberFormatException ex) {
			throw invalid("A quote line has an invalid number.");
		}
	}

	private static ApiException invalid(String message) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, message);
	}

	private static ApiException changedByAnotherUser() {
		return new ApiException(ErrorCode.VERSION_CONFLICT, "This record changed. Reload it and try again.");
	}

	private static String shorten(String text) {
		return (text.length() > 60) ? text.substring(0, 60) + "…" : text;
	}

}
