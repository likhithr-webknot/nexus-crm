package com.nexus.crm.activities;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
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

import com.nexus.crm.activities.dto.ActivityFilter;
import com.nexus.crm.activities.dto.ActivityInput;
import com.nexus.crm.activities.dto.ActivityNames;
import com.nexus.crm.activities.dto.ActivityResponse;
import com.nexus.crm.activities.dto.LogActivityRequest;
import com.nexus.crm.activities.dto.UpdateActivityRequest;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
class ActivityController {

	private final ActivityService activities;

	@GetMapping
	PageResponse<ActivityResponse> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) Activity.Type type, @RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) UUID leadId, @RequestParam(required = false) UUID contactId,
			@RequestParam(required = false) UUID dealId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int size) {
		Page<Activity> found = this.activities.list(user, new ActivityFilter(type, actorId, leadId, contactId, dealId),
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
	ActivityResponse log(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody LogActivityRequest r) {
		Activity activity = this.activities.log(user, new ActivityInput(r.type(), r.subject(), r.body(),
				r.occurredAt(), r.leadId(), r.contactId(), r.dealId()));
		return responses(List.of(activity)).getFirst();
	}

	@PatchMapping("/{id}")
	ActivityResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateActivityRequest r) {
		return responses(List.of(this.activities.update(user, id, r.version(), r.subject(), r.body()))).getFirst();
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		this.activities.delete(user, id);
	}

	private List<ActivityResponse> responses(List<Activity> list) {
		ActivityNames names = this.activities.names(list);
		return list.stream()
			.map((a) -> new ActivityResponse(a.getId(), a.getType(), a.getSubject(), a.getBody(), a.getOccurredAt(), a.getActorId(),
					(a.getActorId() == null) ? null : names.actors().get(a.getActorId()), a.getLeadId(), a.getContactId(), a.getDealId(),
					names.subjectOf(a.getLeadId(), a.getContactId(), a.getDealId()), a.getCreatedAt(), a.getVersion()))
			.toList();
	}

}
