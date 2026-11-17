package com.nexus.crm.modules;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/workspaces/{kind}")
@RequiredArgsConstructor
class ModuleController {
	private final ModuleService service;

	/** One page of a workspace. Search, status and sort are applied on the server, so nothing is cut off silently. */
	@GetMapping
	PageResponse<ModuleRecord> list(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind,
			@RequestParam(required = false) String q, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "updated") String sort, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.list(user, ModuleKind.fromPath(kind), q, status, "name".equals(sort), page,
				PageResponse.clampSize(size));
	}

	@GetMapping("/{id}")
	ModuleRecord get(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind, @PathVariable UUID id) {
		return service.get(user, ModuleKind.fromPath(kind), id);
	}

	@PostMapping
	@CanWriteWorkspace
	@ResponseStatus(HttpStatus.CREATED)
	ModuleRecord create(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind,
			@RequestHeader(name = "Idempotency-Key", required = false) String key, @RequestBody ModuleInput input) {
		return service.create(user, ModuleKind.fromPath(kind), input, key);
	}

	@PatchMapping("/{id}")
	@CanWriteWorkspace
	ModuleRecord update(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind,
			@PathVariable UUID id, @RequestBody ModuleInput input) {
		return service.update(user, ModuleKind.fromPath(kind), id, input);
	}

	@DeleteMapping("/{id}")
	@CanWriteWorkspace
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void archive(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind, @PathVariable UUID id) {
		service.archive(user, ModuleKind.fromPath(kind), id);
	}
}
