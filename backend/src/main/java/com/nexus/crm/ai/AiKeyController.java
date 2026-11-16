package com.nexus.crm.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.ai.AiKeyService.Provider;
import com.nexus.crm.shared.CurrentUser;

@RestController
@RequestMapping("/api/v1/ai/admin/key")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
class AiKeyController {
  private final AiKeyService keys;
  record Input(String key, String name) { }

  /* "/api/v1/ai/admin/key" alone is the OpenAI key, as before. "/sarvam" is the voice key. */

  @GetMapping
  AiKeyService.KeyStatus status() { return keys.status(Provider.OPENAI); }

  @PutMapping
  AiKeyService.KeyStatus save(@AuthenticationPrincipal CurrentUser user, @RequestBody Input input) { return save(user, Provider.OPENAI, input); }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void clear(@AuthenticationPrincipal CurrentUser user) { keys.clear(user.id(), Provider.OPENAI); }

  @GetMapping("/sarvam")
  AiKeyService.KeyStatus sarvamStatus() { return keys.status(Provider.SARVAM); }

  @PutMapping("/sarvam")
  AiKeyService.KeyStatus saveSarvam(@AuthenticationPrincipal CurrentUser user, @RequestBody Input input) { return save(user, Provider.SARVAM, input); }

  @DeleteMapping("/sarvam")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void clearSarvam(@AuthenticationPrincipal CurrentUser user) { keys.clear(user.id(), Provider.SARVAM); }

  private AiKeyService.KeyStatus save(CurrentUser user, Provider provider, Input input) {
    keys.set(user.id(), provider, input.key(), input.name());
    return keys.status(provider);
  }
}
