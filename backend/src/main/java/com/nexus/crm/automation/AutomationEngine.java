package com.nexus.crm.automation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

@Service
@Slf4j
@RequiredArgsConstructor
public class AutomationEngine {
  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final TransactionTemplate transactions;
  private final NotificationService notifications;
  private final AuditService audit;

  public record Run(UUID id, String status, String eventKey, String error, Instant createdAt, Instant updatedAt) { }
  public record Context(String type, UUID recordId, String name, UUID ownerId, UUID actorId, String stage) { }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDealCreated(DealCreated event) {
    start("Deal created", "deal-created:" + event.dealId(),
      new Context("deal", event.dealId(), event.name(), event.ownerId(), event.actorId(), ""));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDealStageChanged(DealStageChanged event) {
    if ("WON".equals(event.toStage())) start("Deal won", "deal-won:" + event.dealId() + ":" + event.fromStage(),
      new Context("deal", event.dealId(), event.name(), event.ownerId(), event.actorId(), event.toStage()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLeadStatusChanged(LeadStatusChanged event) {
    if (event.to() != Lead.Status.QUALIFIED) return;
    UUID owner = jdbc.sql("select owner_id from lead where id=:id").param("id", event.leadId()).query(UUID.class).optional().orElse(null);
    if (owner != null) start("Lead qualified", "lead-qualified:" + event.leadId(),
      new Context("lead", event.leadId(), event.name(), owner, event.actorId(), "QUALIFIED"));
  }

  private void start(String trigger, String eventKey, Context context) {
    List<UUID> workflows = jdbc.sql("select id from crm_module_record where kind='automations' and status='Published' and deleted_at is null "
        + "and details->'flowNodes' @> cast(:trigger as jsonb)")
      .param("trigger", json.writeValueAsString(List.of(Map.of("type", "Trigger", "label", trigger))))
      .query(UUID.class).list();
    for (UUID workflowId : workflows) {
      UUID runId = UUID.randomUUID();
      int created = jdbc.sql("insert into automation_run (id,workflow_id,event_key,context,status) "
          + "values (:id,:workflow,:event,cast(:context as jsonb),'RUNNING') on conflict do nothing")
        .param("id", runId).param("workflow", workflowId).param("event", eventKey)
        .param("context", json.writeValueAsString(context)).update();
      if (created == 1) run(runId);
    }
  }

  public List<Run> history(UUID workflowId) {
    return jdbc.sql("select id,status,event_key,error,created_at,updated_at from automation_run "
        + "where workflow_id=:workflow order by created_at desc limit 100")
      .param("workflow", workflowId).query((rs, row) -> new Run(rs.getObject("id", UUID.class),
        rs.getString("status"), rs.getString("event_key"), rs.getString("error"),
        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant())).list();
  }

  @Scheduled(fixedDelay = 60000)
  public void resumeReady() {
    List<UUID> ready = jdbc.sql("select id from automation_run where status='WAITING' and due_at<=now() order by due_at limit 50")
      .query(UUID.class).list();
    for (UUID id : ready) {
      int claimed = jdbc.sql("update automation_run set status='RUNNING',updated_at=now() where id=:id and status='WAITING' and due_at<=now()")
        .param("id", id).update();
      if (claimed == 1) run(id);
    }
  }

  private void run(UUID runId) {
    try {
      transactions.executeWithoutResult(tx -> execute(runId));
    } catch (RuntimeException failure) {
      log.warn("Automation run {} failed", runId, failure);
      jdbc.sql("update automation_run set status='FAILED',error='A workflow step failed. Review the definition and linked record.',updated_at=now() where id=:id")
        .param("id", runId).update();
    }
  }

  @SuppressWarnings("unchecked")
  private void execute(UUID runId) {
    Map<String, Object> row = jdbc.sql("select workflow_id,context::text as context,next_node from automation_run where id=:id and status='RUNNING'")
      .param("id", runId).query((rs, n) -> Map.<String, Object>of("workflow", rs.getObject("workflow_id", UUID.class),
        "context", rs.getString("context"), "next", rs.getString("next_node") == null ? "" : rs.getString("next_node"))).single();
    UUID workflowId = (UUID) row.get("workflow");
    Context context = json.readValue((String) row.get("context"), Context.class);
    Map<String, Object> details = json.readValue(jdbc.sql("select details::text from crm_module_record where id=:id")
      .param("id", workflowId).query(String.class).single(), Map.class);
    List<Map<String, Object>> nodes = (List<Map<String, Object>>) details.get("flowNodes");
    List<Map<String, Object>> edges = (List<Map<String, Object>>) details.get("flowEdges");
    if (nodes == null || edges == null) throw new IllegalStateException("Workflow definition is missing.");
    String next = (String) row.get("next");
    if ("__done__".equals(next)) {
      jdbc.sql("update automation_run set status='COMPLETED',next_node=null,due_at=null,updated_at=now() where id=:id")
        .param("id", runId).update();
      return;
    }
    if (next.isEmpty()) {
      Map<String, Object> trigger = nodes.stream().filter(n -> "Trigger".equals(n.get("type"))).findFirst().orElseThrow();
      next = String.valueOf(trigger.get("id"));
    }
    for (int count = 0; count < 50 && next != null; count++) {
      String current = next;
      Map<String, Object> node = nodes.stream().filter(n -> current.equals(n.get("id"))).findFirst().orElseThrow();
      String type = String.valueOf(node.get("type"));
      String label = String.valueOf(node.get("label"));
      if ("Delay".equals(type)) {
        Instant due = Instant.now().plus("Wait 1 hour".equals(label) ? 1 : 24, ChronoUnit.HOURS);
        String after = next(edges, current, null);
        jdbc.sql("update automation_run set status='WAITING',next_node=:next,due_at=:due,updated_at=now() where id=:id")
          .param("next", after == null ? "__done__" : after).param("due", java.time.OffsetDateTime.ofInstant(due, java.time.ZoneOffset.UTC)).param("id", runId).update();
        return;
      }
      if ("Action".equals(type)) action(label, context, runId);
      String branch = null;
      if ("Condition".equals(type) || "Branch".equals(type)) {
        boolean yes = "Deal is won".equals(label) ? "WON".equals(context.stage())
          : "Lead is qualified".equals(label) && "QUALIFIED".equals(context.stage());
        branch = yes ? "Yes" : "No";
      }
      next = next(edges, current, branch);
    }
    if (next != null) throw new IllegalStateException("Workflow exceeded step limit.");
    jdbc.sql("update automation_run set status='COMPLETED',next_node=null,due_at=null,updated_at=now() where id=:id")
      .param("id", runId).update();
  }

  private String next(List<Map<String, Object>> edges, String source, String branch) {
    return edges.stream().filter(e -> source.equals(e.get("source")) && (branch == null || branch.equals(e.get("label"))))
      .map(e -> String.valueOf(e.get("target"))).findFirst().orElse(null);
  }

  private void action(String label, Context context, UUID runId) {
    if ("Notify owner".equals(label)) {
      notifications.send(context.ownerId(), "AUTOMATION", "Workflow update: " + context.name(),
        "deal".equals(context.type()) ? "/deals/" + context.recordId() : "/leads/" + context.recordId());
    } else if ("Create follow-up".equals(label)) {
      UUID id = UUID.randomUUID();
      jdbc.sql("insert into follow_up (id,title,due_at,assignee_id,status,lead_id,deal_id,created_by,updated_by) "
          + "values (:id,:title,:due,:owner,'OPEN',:lead,:deal,:actor,:actor)")
        .param("id", id).param("title", "Follow up: " + context.name()).param("due", java.time.OffsetDateTime.ofInstant(Instant.now().plus(1, ChronoUnit.DAYS), java.time.ZoneOffset.UTC))
        .param("owner", context.ownerId())
        .param("lead", "lead".equals(context.type()) ? context.recordId() : null)
        .param("deal", "deal".equals(context.type()) ? context.recordId() : null)
        .param("actor", context.actorId()).update();
      audit.record(context.actorId(), "FollowUp", id, AuditLog.Action.CREATE, null);
      notifications.send(context.ownerId(), "AUTOMATION", "Follow-up created: " + context.name(), "/calendar");
    } else throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported workflow action.");
  }
}
