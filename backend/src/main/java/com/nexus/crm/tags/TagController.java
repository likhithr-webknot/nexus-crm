package com.nexus.crm.tags;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.tags.dto.CreateTagRequest;
import com.nexus.crm.tags.dto.TagRef;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
class TagController {

	private final TagService tags;

	@GetMapping
	List<TagRef> list() {
		return this.tags.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	TagRef create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateTagRequest r) {
		return this.tags.create(user, r.name(), r.color());
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		this.tags.delete(user, id);
	}

}
