package com.nexus.crm.ai;

import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
class AiController {
  private final AiService ai;
  private final JdbcClient jdbc;
  private final AuditService audit;
  record Prompt(String text) { }
  record Apply(String field) { }

  @GetMapping("/status")
  AiService.Status status() { return ai.status(); }

  @PostMapping("/assist")
  AiService.Answer assist(@AuthenticationPrincipal CurrentUser user, @RequestBody Prompt input) {
    return ai.assist(user, input.text());
  }

  @PostMapping(value = "/assist/stream", produces = "text/event-stream")
  SseEmitter stream(@AuthenticationPrincipal CurrentUser user, @RequestBody Prompt input) {
    return ai.streamAssist(user, input.text());
  }

  @PostMapping("/draft-email")
  Map<String,String> draftEmail(@AuthenticationPrincipal CurrentUser user, @RequestBody Prompt input) {
    return Map.of("body", ai.draftEmail(user, input.text()));
  }

  @PostMapping("/workflow")
  AiService.Workflow workflow(@AuthenticationPrincipal CurrentUser user, @RequestBody Prompt input) {
    return ai.workflow(user, input.text());
  }

  @PostMapping("/record-draft/{kind}")
  AiService.RecordDraft recordDraft(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind, @RequestBody Prompt input) {
    return ai.recordDraft(user, kind, input.text());
  }

  @GetMapping("/research/{kind}/{id}")
  ResponseEntity<AiService.Research> research(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind, @PathVariable UUID id) {
    AiService.Research saved = ai.savedResearch(user, kind, id);
    return saved == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(saved);
  }

  @PostMapping("/research/{kind}/{id}")
  AiService.Research refreshResearch(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind, @PathVariable UUID id) {
    return ai.research(user, kind, id);
  }

  @PostMapping("/research/{kind}/{id}/apply")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  void apply(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind, @PathVariable UUID id, @RequestBody Apply input) {
    AiService.Research research = ai.savedResearch(user, kind, id);
    if (research == null || research.sources().isEmpty()) throw new ApiException(ErrorCode.INVALID_STATE, "Research this record first.");
    int changed;
    if ("company".equals(kind) && "website".equals(input.field()) && research.website() != null) {
      changed = jdbc.sql("update company set website_url=:value,updated_at=now(),updated_by=:user,version=version+1 where id=:id and deleted_at is null")
        .param("value", research.website()).param("user", user.id()).param("id", id).update();
    } else if ("company".equals(kind) && "logo".equals(input.field()) && research.logoUrl() != null) {
      changed = jdbc.sql("update company set logo_url=:value,updated_at=now(),updated_by=:user,version=version+1 where id=:id and deleted_at is null")
        .param("value", research.logoUrl()).param("user", user.id()).param("id", id).update();
    } else if ("contact".equals(kind) && "email".equals(input.field()) && research.email() != null) {
      changed = jdbc.sql("update contact set email=:value,updated_at=now(),updated_by=:user,version=version+1 "
          + "where id=:id and deleted_at is null and email is null")
        .param("value", research.email()).param("user", user.id()).param("id", id).update();
    } else throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a researched field.");
    if (changed == 0) throw new ApiException(ErrorCode.INVALID_STATE, "The record changed or already has an email.");
    audit.record(user.id(), "company".equals(kind) ? "Company" : "Contact", id, AuditLog.Action.UPDATE, null);
  }
}
