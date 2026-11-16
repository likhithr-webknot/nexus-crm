package com.nexus.crm.automation;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.modules.ModuleKind;
import com.nexus.crm.modules.ModuleService;
import com.nexus.crm.shared.CurrentUser;

@RestController
@RequestMapping("/api/v1/automations")
@RequiredArgsConstructor
class AutomationController {
  private final AutomationEngine engine;
  private final ModuleService modules;

  @GetMapping("/{id}/runs")
  List<AutomationEngine.Run> runs(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    modules.get(user, ModuleKind.AUTOMATIONS, id);
    return engine.history(id);
  }
}
