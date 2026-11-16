package com.nexus.crm.modules;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.CurrentUser;

@RestController
@RequestMapping("/api/v1/workspaces/{kind}")
@RequiredArgsConstructor
class ModuleController {
	private final ModuleService service;

	@GetMapping
	List<ModuleRecord> list(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind) {
		return service.list(user, ModuleKind.fromPath(kind));
	}

	@GetMapping("/{id}")
	ModuleRecord get(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind, @PathVariable UUID id) {
		return service.get(user, ModuleKind.fromPath(kind), id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ModuleRecord create(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind,
			@RequestHeader(name = "Idempotency-Key", required = false) String key, @RequestBody ModuleInput input) {
		return service.create(user, ModuleKind.fromPath(kind), input, key);
	}

	@PatchMapping("/{id}")
	ModuleRecord update(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind,
			@PathVariable UUID id, @RequestBody ModuleInput input) {
		return service.update(user, ModuleKind.fromPath(kind), id, input);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void archive(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind, @PathVariable UUID id) {
		service.archive(user, ModuleKind.fromPath(kind), id);
	}
}
