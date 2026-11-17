package com.nexus.crm.modules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
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
import com.nexus.crm.shared.web.PageResponse;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

@Service
@RequiredArgsConstructor
public class ModuleService {
	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final UserService users;
	private final AuditService audit;
	/** Most addresses allowed in Cc, and in Bcc. */
	static final int MAX_COPIES = 20;

	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

	/** Stands in for "nobody" because an empty IN () is not valid SQL. */
	private static final UUID NOBODY = new UUID(0, 0);

	/** One page of a workspace. {@code total} lets the screen say when there is more than one page. */
	@Transactional(readOnly = true)
	public PageResponse<ModuleRecord> list(CurrentUser user, ModuleKind kind, String q, String status, boolean byName,
			int page, int size) {
		Map<String, Object> params = new HashMap<>();
		params.put("kind", kind.path());
		StringBuilder from = new StringBuilder("from crm_module_record m join app_user u on u.id = m.owner_id "
			+ "where m.kind = :kind and m.deleted_at is null");
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
		long total = jdbc.sql("select count(*) " + from).params(params).query(Long.class).single();
		List<ModuleRecord> items = jdbc.sql("select m.*, u.display_name " + from + " order by " + order + " limit :size offset :offset")
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
		return jdbc.sql(sql.toString()).params(params).query((rs, row) -> map(rs)).optional()
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Record not found."));
	}

	/** Whose records the user can see. Empty means everyone's. Products are shared by the whole team. */
	private Optional<Set<UUID>> visibleOwners(CurrentUser user, ModuleKind kind) {
		return kind == ModuleKind.PRODUCTS ? Optional.empty() : users.visibleOwnerIds(user);
	}

	private void limitToVisible(StringBuilder sql, Map<String, Object> params, Optional<Set<UUID>> visible) {
		visible.ifPresent(ids -> {
			sql.append(" and m.owner_id in (:ids)");
			// An empty IN () is not valid SQL, so a user with nobody in scope matches no one.
			params.put("ids", ids.isEmpty() ? Set.of(NOBODY) : ids);
		});
	}

	@Transactional
	public ModuleRecord create(CurrentUser user, ModuleKind kind, ModuleInput input, String key) {
		if (key != null && !key.isBlank()) {
			if (key.length() > 100) throw invalid("Idempotency key is too long.");
			var previous = jdbc.sql("select id from crm_module_record where kind=:kind and owner_id=:owner and request_key=:key")
				.param("kind", kind.path()).param("owner", user.id()).param("key", key)
				.query(UUID.class).optional();
			if (previous.isPresent()) return get(user, kind, previous.get());
		}
		Fields fields = validate(kind, input);
		UUID id = UUID.randomUUID();
		jdbc.sql("insert into crm_module_record (id,kind,name,status,owner_id,account,description,amount,details,request_key) "
				+ "values (:id,:kind,:name,:status,:owner,:account,:description,:amount,cast(:details as jsonb),:key)")
			.param("id", id).param("kind", kind.path()).param("name", fields.name())
			.param("status", fields.status()).param("owner", user.id()).param("account", fields.account())
			.param("description", fields.description()).param("amount", fields.amount())
			.param("details", json.writeValueAsString(fields.details())).param("key", key).update();
		audit.record(user.id(), kind.entity(), id, AuditLog.Action.CREATE, null);
		return get(user, kind, id);
	}

	@Transactional
	public ModuleRecord update(CurrentUser user, ModuleKind kind, UUID id, ModuleInput input) {
		ModuleRecord existing = get(user, kind, id);
		if (kind == ModuleKind.COMMUNICATIONS && !Set.of("Draft", "Failed").contains(existing.status()))
			throw invalid("A sent or unconfirmed email cannot be edited. Create a new draft instead.");
		if (input.version() == null || input.version() != existing.version())
			throw new ApiException(ErrorCode.VERSION_CONFLICT, "This record changed. Reload it and try again.");
		Fields fields = validate(kind, input);
		int changed = jdbc.sql("update crm_module_record set name=:name,status=:status,account=:account,description=:description,"
				+ "amount=:amount,details=cast(:details as jsonb),updated_at=now(),version=version+1 "
				+ "where id=:id and version=:version")
			.param("name", fields.name()).param("status", fields.status()).param("account", fields.account())
			.param("description", fields.description()).param("amount", fields.amount())
			.param("details", json.writeValueAsString(fields.details()))
			.param("id", id).param("version", existing.version()).update();
		if (changed != 1) throw new ApiException(ErrorCode.VERSION_CONFLICT, "This record changed. Reload it and try again.");
		audit.record(user.id(), kind.entity(), id, AuditLog.Action.UPDATE, null);
		return get(user, kind, id);
	}

	@Transactional
	public void archive(CurrentUser user, ModuleKind kind, UUID id) {
		get(user, kind, id);
		jdbc.sql("update crm_module_record set deleted_at=now(),updated_at=now(),version=version+1 where id=:id")
			.param("id", id).update();
		audit.record(user.id(), kind.entity(), id, AuditLog.Action.DELETE, null);
	}

	@Scheduled(cron = "0 30 3 * * *", zone = "UTC")
	@Transactional
	public void purgeCommunicationDrafts() {
		jdbc.sql("delete from crm_module_record where kind='communications' and status='Draft' and created_at < :cutoff")
			.param("cutoff", OffsetDateTime.ofInstant(Instant.now().minus(30, ChronoUnit.DAYS), ZoneOffset.UTC)).update();
	}

	private Fields validate(ModuleKind kind, ModuleInput input) {
		if (input == null) throw invalid("A record is required.");
		String name = trim(input.name(), 160);
		if (name.length() < 2) throw invalid("Enter a name of at least two characters.");
		String status = input.status();
		if (!kind.allows(status)) throw invalid("Choose a valid status.");
		if (kind == ModuleKind.COMMUNICATIONS && !"Draft".equals(status)) throw invalid("Use Send to deliver a communication.");
		String account = trim(input.account(), 160);
		String description = trim(input.description(), 10_000);
		BigDecimal amount = input.amount() == null ? BigDecimal.ZERO : input.amount();
		if (amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0)
			throw invalid("Enter a valid nonnegative amount with two decimal places.");

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
			default -> { }
		}
		return new Fields(name, status, account, description, amount, details);
	}

	/** Checks the lines and returns the total, worked out here so the client cannot send a different one. */
	private BigDecimal validateQuote(ModuleInput input, Map<String, Object> details) {
		List<Map<String, Object>> lines = input.lineItems() == null ? List.of() : input.lineItems();
		if (lines.size() > 100) throw invalid("A quote can have at most 100 lines.");
		BigDecimal total = BigDecimal.ZERO;
		for (Map<String, Object> line : lines) {
			BigDecimal quantity = decimal(line.get("quantity"));
			BigDecimal price = decimal(line.get("unitPrice"));
			BigDecimal discount = decimal(line.get("discountPct"));
			BigDecimal tax = decimal(line.get("taxPct"));
			boolean valid = quantity.signum() > 0 && price.signum() >= 0 && isPercent(discount) && isPercent(tax);
			if (!valid) throw invalid("A quote line has an invalid quantity, price, discount or tax.");
			BigDecimal afterDiscount = quantity.multiply(price).multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)));
			total = total.add(afterDiscount.multiply(BigDecimal.ONE.add(tax.movePointLeft(2))));
		}
		details.put("lineItems", lines);
		return total.setScale(2, RoundingMode.HALF_UP);
	}

	private boolean isPercent(BigDecimal value) {
		return value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0;
	}

	private void validateAutomation(ModuleInput input, String status, Map<String, Object> details) {
		List<Map<String, Object>> nodes = input.flowNodes() == null ? List.of() : input.flowNodes();
		List<Map<String, Object>> edges = input.flowEdges() == null ? List.of() : input.flowEdges();
		if (nodes.size() > 50 || edges.size() > 100) throw invalid("Workflow is too large.");
		if ("Published".equals(status)) validateFlow(nodes, edges);
		details.put("flowNodes", nodes);
		details.put("flowEdges", edges);
	}

	private void validateEmail(ModuleInput input, Map<String, Object> details) {
		String to = trim(input.recipient(), 254).toLowerCase(Locale.ROOT);
		if (!to.isEmpty() && !Emails.isValid(to)) throw invalid("Enter one valid email address in To.");
		List<String> cc = addresses("Cc", input.cc());
		List<String> bcc = addresses("Bcc", input.bcc());
		rejectRepeats(to, cc, bcc);
		details.put("recipient", to);
		details.put("cc", cc);
		details.put("bcc", bcc);
		details.put("bodyHtml", trim(input.bodyHtml(), 50_000));
	}

	/** A Cc or Bcc list: valid addresses only, lower-cased, repeats dropped, at most {@link #MAX_COPIES}. */
	private List<String> addresses(String label, List<String> given) {
		if (given == null) return List.of();
		Set<String> clean = new java.util.LinkedHashSet<>();
		for (String raw : given) {
			String address = raw == null ? "" : raw.strip();
			if (address.isEmpty()) continue;
			if (!Emails.isValid(address)) throw invalid(label + " has an address that is not a valid email: " + shorten(address));
			clean.add(address.toLowerCase(Locale.ROOT));
		}
		if (clean.size() > MAX_COPIES) throw invalid(label + " can have at most " + MAX_COPIES + " addresses.");
		return List.copyOf(clean);
	}
	/** The same address in two fields would reach the person twice, and a Bcc address also in Cc would not stay hidden. */
	private void rejectRepeats(String to, List<String> cc, List<String> bcc) {
		for (String address : bcc) if (address.equals(to) || cc.contains(address)) throw invalid(address + " is listed in Bcc and also in To or Cc.");
		for (String address : cc) if (address.equals(to)) throw invalid(address + " is listed in both To and Cc.");
	}
	private static String shorten(String text) { return text.length() > 60 ? text.substring(0, 60) + "…" : text; }

	private void validateFlow(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
		if (nodes.stream().filter(n -> "Trigger".equals(n.get("type"))).count() != 1)
			throw invalid("A published workflow needs exactly one trigger.");
		Set<String> ids = new HashSet<>();
		int actions = 0;
		for (Map<String, Object> node : nodes) {
			String id = String.valueOf(node.get("id"));
			String type = String.valueOf(node.get("type"));
			String label = String.valueOf(node.get("label"));
			if (node.get("id") == null || !ids.add(id) || id.length() > 100 || !Set.of("Trigger","Condition","Action","Delay","Branch").contains(type))
				throw invalid("Workflow has an invalid step.");
			boolean supported = switch (type) {
				case "Trigger" -> Set.of("Deal created", "Deal won", "Lead qualified").contains(label);
				case "Action" -> Set.of("Notify owner", "Create follow-up").contains(label);
				case "Delay" -> Set.of("Wait 1 hour", "Wait 1 day").contains(label);
				default -> Set.of("Deal is won", "Lead is qualified").contains(label);
			};
			if (!supported) throw invalid("Choose a supported workflow step.");
			if ("Action".equals(type)) actions++;
		}
		if (actions == 0) throw invalid("Add an action before publishing.");
		for (Map<String, Object> edge : edges) {
			if (!ids.contains(edge.get("source")) || !ids.contains(edge.get("target")) || edge.get("source").equals(edge.get("target")))
				throw invalid("Workflow has an invalid connection.");
			String sourceType = String.valueOf(nodes.stream().filter(n -> edge.get("source").equals(n.get("id"))).findFirst().orElseThrow().get("type"));
			if (("Condition".equals(sourceType) || "Branch".equals(sourceType)) && !Set.of("Yes", "No").contains(String.valueOf(edge.get("label"))))
				throw invalid("Condition paths must be Yes or No.");
		}
		Map<String, List<String>> graph = new HashMap<>();
		for (Map<String, Object> edge : edges) graph.computeIfAbsent((String) edge.get("source"), key -> new java.util.ArrayList<>()).add((String) edge.get("target"));
		String triggerId = String.valueOf(nodes.stream().filter(n -> "Trigger".equals(n.get("type"))).findFirst().orElseThrow().get("id"));
		Set<String> reachable = new HashSet<>();
		collectReachable(triggerId, graph, reachable);
		if (reachable.size() != ids.size()) throw invalid("Connect every step to the trigger.");
		for (String id : ids) {
			String type = String.valueOf(nodes.stream().filter(n -> id.equals(n.get("id"))).findFirst().orElseThrow().get("type"));
			int outgoing = graph.getOrDefault(id, List.of()).size();
			if (outgoing > ("Condition".equals(type) || "Branch".equals(type) ? 2 : 1))
				throw invalid("A step has too many connections.");
			if ("Condition".equals(type) || "Branch".equals(type)) {
				long distinct = edges.stream().filter(e -> id.equals(e.get("source"))).map(e -> e.get("label")).distinct().count();
				if (distinct != outgoing) throw invalid("Each condition path must be unique.");
			}
			if (hasCycle(id, graph, new HashSet<>())) throw invalid("Workflow connections cannot form a cycle.");
		}
	}
	private void collectReachable(String id, Map<String, List<String>> graph, Set<String> reachable) {
		if (!reachable.add(id)) return;
		for (String next : graph.getOrDefault(id, List.of())) collectReachable(next, graph, reachable);
	}
	private boolean hasCycle(String id, Map<String, List<String>> graph, Set<String> path) {
		if (!path.add(id)) return true;
		for (String next : graph.getOrDefault(id, List.of())) if (hasCycle(next, graph, path)) return true;
		path.remove(id);
		return false;
	}

	private ModuleRecord map(ResultSet rs) throws SQLException {
		@SuppressWarnings("unchecked") Map<String, Object> details = json.readValue(rs.getString("details"), Map.class);
		return new ModuleRecord(
			rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"), rs.getString("display_name"),
			rs.getString("account"), rs.getString("description"), rs.getBigDecimal("amount"),
			rs.getObject("created_at", OffsetDateTime.class).toInstant(),
			rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
			rs.getInt("version"),
			list(details.get("lineItems")), list(details.get("flowNodes")), list(details.get("flowEdges")),
			(String) details.get("recipient"), strings(details.get("cc")), strings(details.get("bcc")),
			(String) details.get("bodyHtml"));
	}

	private List<String> strings(Object value) {
		return value instanceof List<?> items ? items.stream().map(String::valueOf).toList() : List.of();
	}
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> list(Object value) { return value instanceof List<?> ? (List<Map<String, Object>>) value : List.of(); }
	private String trim(String value, int max) {
		String result = value == null ? "" : value.strip();
		if (result.length() > max) throw invalid("A field is too long.");
		return result;
	}
	private BigDecimal decimal(Object value) {
		try { return new BigDecimal(String.valueOf(value)); }
		catch (NumberFormatException e) { throw invalid("A quote line has an invalid number."); }
	}
	private ApiException invalid(String message) { return new ApiException(ErrorCode.VALIDATION_FAILED, message); }
	private record Fields(String name, String status, String account, String description, BigDecimal amount, Map<String, Object> details) { }
}
