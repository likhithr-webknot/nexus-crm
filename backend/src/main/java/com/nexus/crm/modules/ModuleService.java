package com.nexus.crm.modules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

@Service
@RequiredArgsConstructor
public class ModuleService {
	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final UserService users;
	private final AuditService audit;

	@Transactional(readOnly = true)
	public List<ModuleRecord> list(CurrentUser user, ModuleKind kind) {
		var visible = kind == ModuleKind.PRODUCTS ? java.util.Optional.<Set<UUID>>empty() : users.visibleOwnerIds(user);
		var query = jdbc.sql("select m.*, u.display_name from crm_module_record m join app_user u on u.id=m.owner_id "
				+ "where m.kind=:kind and m.deleted_at is null and " + scope(visible.isPresent()) + " order by m.updated_at desc limit 200")
			.param("kind", kind.path());
		if (visible.isPresent()) query = query.param("ids", visible.get().isEmpty() ? Set.of(new UUID(0, 0)) : visible.get());
		return query.query((rs, row) -> map(rs)).list();
	}

	@Transactional(readOnly = true)
	public ModuleRecord get(CurrentUser user, ModuleKind kind, UUID id) {
		var visible = kind == ModuleKind.PRODUCTS ? java.util.Optional.<Set<UUID>>empty() : users.visibleOwnerIds(user);
		var query = jdbc.sql("select m.*, u.display_name from crm_module_record m join app_user u on u.id=m.owner_id "
				+ "where m.id=:id and m.kind=:kind and m.deleted_at is null and " + scope(visible.isPresent()))
			.param("id", id).param("kind", kind.path());
		if (visible.isPresent()) query = query.param("ids", visible.get().isEmpty() ? Set.of(new UUID(0, 0)) : visible.get());
		return query.query((rs, row) -> map(rs)).optional()
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Record not found."));
	}

	@Transactional
	public ModuleRecord create(CurrentUser user, ModuleKind kind, ModuleInput input, String key) {
		requireWrite(user, kind);
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
		requireWrite(user, kind);
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
		requireWrite(user, kind);
		get(user, kind, id);
		jdbc.sql("update crm_module_record set deleted_at=now(),updated_at=now(),version=version+1 where id=:id")
			.param("id", id).update();
		audit.record(user.id(), kind.entity(), id, AuditLog.Action.DELETE, null);
	}

	@Scheduled(cron = "0 30 3 * * *", zone = "UTC")
	@Transactional
	public void purgeCommunicationDrafts() {
		jdbc.sql("delete from crm_module_record where kind='communications' and status='Draft' and created_at < :cutoff")
			.param("cutoff", java.time.OffsetDateTime.ofInstant(Instant.now().minus(30, ChronoUnit.DAYS), java.time.ZoneOffset.UTC)).update();
	}

	private String scope(boolean scoped) {
		return scoped ? "m.owner_id in (:ids)" : "true";
	}
	private void requireWrite(CurrentUser user, ModuleKind kind) {
		if ((kind == ModuleKind.PRODUCTS || kind == ModuleKind.AUTOMATIONS) && user.role() != com.nexus.crm.shared.Role.ADMIN)
			throw new ApiException(ErrorCode.FORBIDDEN, "Only admins can change this workspace.");
		if (kind == ModuleKind.CAMPAIGNS && user.role() == com.nexus.crm.shared.Role.REP)
			throw new ApiException(ErrorCode.FORBIDDEN, "Only managers and admins can change campaigns.");
	}

	private Fields validate(ModuleKind kind, ModuleInput input) {
		if (input == null) throw invalid("A record is required.");
		String name = trim(input.name(), 160);
		if (name.length() < 2) throw invalid("Enter a name of at least two characters.");
		String status = input.status();
		if (!kind.allows(status)) throw invalid("Choose a valid status.");
		if (kind == ModuleKind.COMMUNICATIONS && !"Draft".equals(status))
			throw invalid("Use Send to deliver a communication.");
		String account = trim(input.account(), 160);
		String description = trim(input.description(), 10_000);
		BigDecimal amount = input.amount() == null ? BigDecimal.ZERO : input.amount();
		if (amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("9999999999999999.99")) > 0)
			throw invalid("Enter a valid nonnegative amount with two decimal places.");
		Map<String, Object> details = new HashMap<>();
		if (kind == ModuleKind.QUOTES) {
			List<Map<String, Object>> lines = input.lineItems() == null ? List.of() : input.lineItems();
			if (lines.size() > 100) throw invalid("A quote can have at most 100 lines.");
			BigDecimal total = BigDecimal.ZERO;
			for (Map<String, Object> line : lines) {
				BigDecimal quantity = decimal(line.get("quantity"));
				BigDecimal price = decimal(line.get("unitPrice"));
				BigDecimal discount = decimal(line.get("discountPct"));
				BigDecimal tax = decimal(line.get("taxPct"));
				if (quantity.signum() <= 0 || price.signum() < 0 || discount.signum() < 0 || discount.compareTo(BigDecimal.valueOf(100)) > 0 || tax.signum() < 0 || tax.compareTo(BigDecimal.valueOf(100)) > 0)
					throw invalid("A quote line has an invalid quantity, price, discount or tax.");
				BigDecimal net = quantity.multiply(price).multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)));
				total = total.add(net.multiply(BigDecimal.ONE.add(tax.movePointLeft(2))));
			}
			amount = total.setScale(2, RoundingMode.HALF_UP);
			details.put("lineItems", lines);
		}
		if (kind == ModuleKind.AUTOMATIONS) {
			List<Map<String, Object>> nodes = input.flowNodes() == null ? List.of() : input.flowNodes();
			List<Map<String, Object>> edges = input.flowEdges() == null ? List.of() : input.flowEdges();
			if (nodes.size() > 50 || edges.size() > 100) throw invalid("Workflow is too large.");
			if ("Published".equals(status)) validateFlow(nodes, edges);
			details.put("flowNodes", nodes);
			details.put("flowEdges", edges);
			amount = BigDecimal.ZERO;
		}
		if (kind == ModuleKind.COMMUNICATIONS) {
			String recipient = trim(input.recipient(), 254);
			if (!recipient.isEmpty() && !recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw invalid("Enter a valid recipient email.");
			details.put("recipient", recipient);
			details.put("bodyHtml", trim(input.bodyHtml(), 50_000));
			amount = BigDecimal.ZERO;
		}
		return new Fields(name, status, account, description, amount, details);
	}

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

	private ModuleRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
		@SuppressWarnings("unchecked") Map<String, Object> details = json.readValue(rs.getString("details"), Map.class);
		return new ModuleRecord(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"),
			rs.getString("display_name"), rs.getString("account"), rs.getString("description"),
			rs.getBigDecimal("amount"), rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
			rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant(), rs.getInt("version"),
			list(details.get("lineItems")), list(details.get("flowNodes")), list(details.get("flowEdges")),
			(String) details.get("recipient"), (String) details.get("bodyHtml"));
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
