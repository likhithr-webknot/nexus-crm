package com.nexus.crm.automation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.deals.event.DealCreated;
import com.nexus.crm.deals.event.DealStageChanged;
import com.nexus.crm.leads.Lead;
import com.nexus.crm.leads.event.LeadStatusChanged;
import com.nexus.crm.notifications.NotificationService;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * Runs published workflows when something happens in the CRM (a deal is created, a deal
 * is won, a lead is qualified). Every run is a row in {@code automation_run}, so it
 * survives a restart and a "wait" step can resume later.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AutomationEngine {

	/** Stored in {@code next_node} once the last step has run after a wait. */
	private static final String DONE = "__done__";

	private static final int MAX_STEPS = 50;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final TransactionTemplate transactions;

	private final NotificationService notifications;

	private final AuditService audit;

	/**
	 * One virtual thread per run. Runs mostly wait on the database, so there is no pool
	 * to size.
	 */
	private final ExecutorService runner = Executors.newVirtualThreadPerTaskExecutor();

	public record Run(UUID id, String status, String eventKey, String error, Instant createdAt, Instant updatedAt) {
	}

	/** What the workflow knows about the record that triggered it. */
	public record Context(String type, UUID recordId, String name, UUID ownerId, UUID actorId, String stage) {
	}

	/** The steps and connections of one workflow, as saved by the editor. */
	private record Flow(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {

		Map<String, Object> node(String id) {
			return nodes.stream().filter(n -> id.equals(n.get("id"))).findFirst().orElseThrow();
		}

		String triggerId() {
			Map<String, Object> trigger = nodes.stream()
				.filter(n -> "Trigger".equals(n.get("type")))
				.findFirst()
				.orElseThrow();
			return String.valueOf(trigger.get("id"));
		}

		/**
		 * The step after {@code from}, or null at the end. After a condition,
		 * {@code branch} is "Yes" or "No".
		 */
		String next(String from, String branch) {
			return edges.stream()
				.filter(e -> from.equals(e.get("source")) && (branch == null || branch.equals(e.get("label"))))
				.map(e -> String.valueOf(e.get("target")))
				.findFirst()
				.orElse(null);
		}
	}

	private record RunRow(UUID workflowId, String context, String nextNode) {
	}

	// Events. They fire after the user's change is saved, and the work moves to a virtual
	// thread,
	// so a slow or failing workflow can never slow down or fail the request that caused
	// it.

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDealCreated(DealCreated event) {
		Context context = new Context("deal", event.dealId(), event.name(), event.ownerId(), event.actorId(), "");
		background("deal-created:" + event.dealId(),
				() -> start("Deal created", "deal-created:" + event.dealId(), context));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDealStageChanged(DealStageChanged event) {
		if (!"WON".equals(event.toStage())) {
			return;
		}
		Context context = new Context("deal", event.dealId(), event.name(), event.ownerId(), event.actorId(),
				event.toStage());
		String eventKey = "deal-won:" + event.dealId() + ":" + event.fromStage();
		background(eventKey, () -> start("Deal won", eventKey, context));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onLeadStatusChanged(LeadStatusChanged event) {
		if (event.to() != Lead.Status.QUALIFIED) {
			return;
		}
		String eventKey = "lead-qualified:" + event.leadId();
		background(eventKey, () -> {
			UUID owner = jdbc.sql("select owner_id from lead where id = :id")
				.param("id", event.leadId())
				.query(UUID.class)
				.optional()
				.orElse(null);
			if (owner == null) {
				return;
			}
			start("Lead qualified", eventKey,
					new Context("lead", event.leadId(), event.name(), owner, event.actorId(), "QUALIFIED"));
		});
	}

	/** Hands the work to a virtual thread. Nothing thrown inside reaches the caller. */
	private void background(String what, Runnable work) {
		try {
			runner.execute(() -> {
				try {
					work.run();
				}
				catch (RuntimeException failure) {
					log.warn("Automation {} could not be started", what, failure);
				}
			});
		}
		catch (RejectedExecutionException serverIsStopping) {
			log.warn("Automation {} was dropped because the server is stopping", what);
		}
	}

	@PreDestroy
	void stop() throws InterruptedException {
		runner.shutdown();
		if (!runner.awaitTermination(10, TimeUnit.SECONDS)) {
			runner.shutdownNow();
		}
	}

	/**
	 * Starts a run for every published workflow with this trigger. The same event never
	 * starts the same workflow twice.
	 */
	private void start(String trigger, String eventKey, Context context) {
		List<UUID> workflows = jdbc.sql("""
				select id from crm_module_record
				where kind = 'automations' and status = 'Published' and deleted_at is null
				  and details->'flowNodes' @> cast(:trigger as jsonb)""")
			.param("trigger", json.writeValueAsString(List.of(Map.of("type", "Trigger", "label", trigger))))
			.query(UUID.class)
			.list();

		for (UUID workflowId : workflows) {
			UUID runId = UUID.randomUUID();
			int created = jdbc.sql("""
					insert into automation_run (id, workflow_id, event_key, context, status)
					values (:id, :workflow, :event, cast(:context as jsonb), 'RUNNING')
					on conflict do nothing""")
				.param("id", runId)
				.param("workflow", workflowId)
				.param("event", eventKey)
				.param("context", json.writeValueAsString(context))
				.update();
			if (created == 1) {
				run(runId);
			}
		}
	}

	public List<Run> history(UUID workflowId) {
		return jdbc.sql("""
				select id, status, event_key, error, created_at, updated_at from automation_run
				where workflow_id = :workflow order by created_at desc limit 100""")
			.param("workflow", workflowId)
			.query((rs, row) -> new Run(rs.getObject("id", UUID.class), rs.getString("status"),
					rs.getString("event_key"), rs.getString("error"),
					rs.getObject("created_at", OffsetDateTime.class).toInstant(),
					rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	/** Every minute: continue runs whose wait is over. */
	@Scheduled(fixedDelay = 60_000)
	public void resumeReady() {
		failInterruptedRuns();
		List<UUID> ready = jdbc
			.sql("select id from automation_run where status = 'WAITING' and due_at <= now() order by due_at limit 50")
			.query(UUID.class)
			.list();
		for (UUID id : ready) {
			// Changing the status is the claim. If another server got there first, this
			// updates nothing.
			int claimed = jdbc.sql(
					"update automation_run set status = 'RUNNING', updated_at = now() where id = :id and status = 'WAITING' and due_at <= now()")
				.param("id", id)
				.update();
			if (claimed == 1) {
				background("resume:" + id, () -> run(id));
			}
		}
	}

	/**
	 * A run stuck in RUNNING was cut off by a crash or restart. Mark it so it does not
	 * look alive forever.
	 */
	void failInterruptedRuns() {
		int marked = jdbc.sql("""
				update automation_run
				set status = 'FAILED', error = 'The run was interrupted before it finished.', updated_at = now()
				where status = 'RUNNING' and updated_at < now() - interval '15 minutes'""").update();
		if (marked > 0) {
			log.warn("{} automation run(s) were interrupted and are marked FAILED", marked);
		}
	}

	/**
	 * Runs one claimed run in a transaction. If a step fails, the transaction rolls back
	 * and the run is still RUNNING, so the finally block marks it FAILED. A run that
	 * finished is already COMPLETED or WAITING, and that update does nothing.
	 */
	private void run(UUID runId) {
		try {
			transactions.executeWithoutResult(tx -> execute(runId));
		}
		catch (RuntimeException failure) {
			log.warn("Automation run {} failed", runId, failure);
		}
		finally {
			markFailedIfStillRunning(runId);
		}
	}

	private void markFailedIfStillRunning(UUID runId) {
		try {
			jdbc.sql(
					"""
							update automation_run
							set status = 'FAILED', error = 'A workflow step failed. Review the definition and linked record.', updated_at = now()
							where id = :id and status = 'RUNNING'""")
				.param("id", runId)
				.update();
		}
		catch (RuntimeException cannotRecord) {
			// The sweep in failInterruptedRuns() will catch it.
			log.error("Automation run {} could not be marked FAILED", runId, cannotRecord);
		}
	}

	private void execute(UUID runId) {
		RunRow row = loadRun(runId);
		if (DONE.equals(row.nextNode())) {
			complete(runId);
			return;
		}
		Context context = json.readValue(row.context(), Context.class);
		Flow flow = loadFlow(row.workflowId());

		String stepId = row.nextNode().isEmpty() ? flow.triggerId() : row.nextNode();
		for (int steps = 0; stepId != null; steps++) {
			if (steps == MAX_STEPS) {
				throw new IllegalStateException("Workflow exceeded step limit.");
			}
			Map<String, Object> step = flow.node(stepId);
			String type = String.valueOf(step.get("type"));
			String label = String.valueOf(step.get("label"));

			if ("Delay".equals(type)) {
				waitThenContinueWith(runId, label, flow.next(stepId, null));
				return;
			}
			if ("Action".equals(type)) {
				action(label, context);
			}
			boolean isCondition = "Condition".equals(type) || "Branch".equals(type);
			stepId = flow.next(stepId, isCondition ? (conditionHolds(label, context) ? "Yes" : "No") : null);
		}
		complete(runId);
	}

	private RunRow loadRun(UUID runId) {
		return jdbc.sql(
				"select workflow_id, context::text as context, coalesce(next_node, '') as next_node from automation_run where id = :id and status = 'RUNNING'")
			.param("id", runId)
			.query((rs, n) -> new RunRow(rs.getObject("workflow_id", UUID.class), rs.getString("context"),
					rs.getString("next_node")))
			.single();
	}

	@SuppressWarnings("unchecked")
	private Flow loadFlow(UUID workflowId) {
		String details = jdbc.sql("select details::text from crm_module_record where id = :id")
			.param("id", workflowId)
			.query(String.class)
			.single();
		Map<String, Object> saved = json.readValue(details, Map.class);
		var nodes = (List<Map<String, Object>>) saved.get("flowNodes");
		var edges = (List<Map<String, Object>>) saved.get("flowEdges");
		if (nodes == null || edges == null) {
			throw new IllegalStateException("Workflow definition is missing.");
		}
		return new Flow(nodes, edges);
	}

	private void complete(UUID runId) {
		jdbc.sql(
				"update automation_run set status = 'COMPLETED', next_node = null, due_at = null, updated_at = now() where id = :id")
			.param("id", runId)
			.update();
	}

	/**
	 * Parks the run until the wait is over. {@code after} is the step to continue with,
	 * or null if the wait was the last step.
	 */
	private void waitThenContinueWith(UUID runId, String delayLabel, String after) {
		long hours = "Wait 1 hour".equals(delayLabel) ? 1 : 24;
		Instant due = Instant.now().plus(hours, ChronoUnit.HOURS);
		jdbc.sql(
				"update automation_run set status = 'WAITING', next_node = :next, due_at = :due, updated_at = now() where id = :id")
			.param("next", after == null ? DONE : after)
			.param("due", at(due))
			.param("id", runId)
			.update();
	}

	private boolean conditionHolds(String label, Context context) {
		return switch (label) {
			case "Deal is won" -> "WON".equals(context.stage());
			case "Lead is qualified" -> "QUALIFIED".equals(context.stage());
			default -> false;
		};
	}

	private void action(String label, Context context) {
		switch (label) {
			case "Notify owner" -> notifyOwner(context);
			case "Create follow-up" -> createFollowUp(context);
			default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported workflow action.");
		}
	}

	private void notifyOwner(Context context) {
		String link = ("deal".equals(context.type()) ? "/deals/" : "/leads/") + context.recordId();
		notifications.send(context.ownerId(), "AUTOMATION", "Workflow update: " + context.name(), link);
	}

	/** A follow-up for the record's owner, due tomorrow. */
	private void createFollowUp(Context context) {
		UUID id = UUID.randomUUID();
		boolean forLead = "lead".equals(context.type());
		jdbc.sql("""
				insert into follow_up (id, title, due_at, assignee_id, status, lead_id, deal_id, created_by, updated_by)
				values (:id, :title, :due, :owner, 'OPEN', :lead, :deal, :actor, :actor)""")
			.param("id", id)
			.param("title", "Follow up: " + context.name())
			.param("due", at(Instant.now().plus(1, ChronoUnit.DAYS)))
			.param("owner", context.ownerId())
			.param("lead", forLead ? context.recordId() : null)
			.param("deal", forLead ? null : context.recordId())
			.param("actor", context.actorId())
			.update();
		audit.record(context.actorId(), "FollowUp", id, AuditLog.Action.CREATE, null);
		notifications.send(context.ownerId(), "AUTOMATION", "Follow-up created: " + context.name(), "/calendar");
	}

	private static OffsetDateTime at(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
