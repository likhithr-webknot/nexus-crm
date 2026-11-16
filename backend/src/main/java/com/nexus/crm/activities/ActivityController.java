package com.nexus.crm.activities;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.web.PageResponse;
import com.nexus.crm.shared.Who;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
class ActivityController {

	record ActivityResponse(UUID id, Activity.Type type, String subject, String body, Instant occurredAt, UUID actorId,
			String actorName, UUID leadId, UUID contactId, UUID dealId, Who about, Instant createdAt, Integer version) {
	}

	record LogRequest(@NotNull Activity.Type type, @NotBlank @Size(max = 160) String subject, @Size(max = 5000) String body,
			Instant occurredAt, UUID leadId, UUID contactId, UUID dealId) {
	}

	record UpdateRequest(Integer version, @Size(max = 160) String subject, @Size(max = 5000) String body) {
	}

	private final ActivityService activities;

	@GetMapping
	PageResponse<ActivityResponse> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) Activity.Type type, @RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) UUID leadId, @RequestParam(required = false) UUID contactId,
			@RequestParam(required = false) UUID dealId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int size) {
		Page<Activity> found = this.activities.list(user, new ActivityService.Filter(type, actorId, leadId, contactId, dealId),
				page, PageResponse.clampSize(size));
		List<ActivityResponse> items = responses(found.getContent());
		return new PageResponse<>(items, found.getTotalElements(), found.getNumber() + 1, found.getSize());
	}

	/** How many activities there are of each type, for the filter chips. */
	@GetMapping("/counts")
	Map<Activity.Type, Long> counts(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) UUID actorId) {
		return this.activities.counts(user, actorId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ActivityResponse log(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody LogRequest r) {
		Activity activity = this.activities.log(user, new ActivityService.ActivityInput(r.type(), r.subject(), r.body(),
				r.occurredAt(), r.leadId(), r.contactId(), r.dealId()));
		return responses(List.of(activity)).getFirst();
	}

	@PatchMapping("/{id}")
	ActivityResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateRequest r) {
		return responses(List.of(this.activities.update(user, id, r.version(), r.subject(), r.body()))).getFirst();
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		this.activities.delete(user, id);
	}

	private List<ActivityResponse> responses(List<Activity> list) {
		ActivityService.Names names = this.activities.names(list);
		return list.stream()
			.map((a) -> new ActivityResponse(a.getId(), a.getType(), a.getSubject(), a.getBody(), a.getOccurredAt(), a.getActorId(),
					(a.getActorId() == null) ? null : names.actors().get(a.getActorId()), a.getLeadId(), a.getContactId(), a.getDealId(),
					names.subjectOf(a.getLeadId(), a.getContactId(), a.getDealId()), a.getCreatedAt(), a.getVersion()))
			.toList();
	}

}
