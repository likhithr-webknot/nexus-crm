package com.nexus.crm.trash;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.trash.dto.TrashItem;

/** Managers and admins can see what was deleted and bring it back. Reps cannot. */
@RestController
@RequestMapping("/api/v1/trash")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
class TrashController {

	private final TrashService trash;

	@GetMapping
	List<TrashItem> list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) TrashType type) {
		return this.trash.list(user, Optional.ofNullable(type));
	}

	@PostMapping("/{type}/{id}/restore")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void restore(@AuthenticationPrincipal CurrentUser user, @PathVariable TrashType type, @PathVariable UUID id) {
		this.trash.restore(user, type, id);
	}

}
