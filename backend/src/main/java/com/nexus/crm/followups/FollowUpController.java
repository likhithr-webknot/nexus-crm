package com.nexus.crm.followups;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/follow-ups")
@RequiredArgsConstructor
class FollowUpController {

	/** A calendar month with its padding days can hold a lot of entries, so this list allows a bigger page. */
	private static final int MAX_SIZE = 500;

	record FollowUpResponse(UUID id, String title, FollowUp.Type type, String notes, Instant dueAt, FollowUp.Status status,
			Instant completedAt, UUID assigneeId, String assigneeName, UUID leadId, UUID contactId, UUID dealId, Who about,
			Instant createdAt, Integer version) {
	}

	record CreateRequest(@NotBlank @Size(max = 160) String title, FollowUp.Type type, @Size(max = 2000) String notes,
			@NotNull Instant dueAt, UUID assigneeId, UUID leadId, UUID contactId, UUID dealId) {
	}

	record UpdateRequest(Integer version, @Size(max = 160) String title, FollowUp.Type type, @Size(max = 2000) String notes,
			Instant dueAt, UUID assigneeId) {
	}

	private final FollowUpService followUps;

	@GetMapping
	PageResponse<FollowUpResponse> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(required = false) FollowUp.Status status, @RequestParam(required = false) String assignee,
			@RequestParam(required = false) UUID leadId, @RequestParam(required = false) UUID contactId,
			@RequestParam(required = false) UUID dealId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "100") int size) {
		UUID assigneeId = "me".equals(assignee) ? user.id() : parse(assignee);
		Page<FollowUp> found = this.followUps.list(user,
				new FollowUpService.Filter(from, to, status, assigneeId, leadId, contactId, dealId), page,
				Math.max(1, Math.min(size, MAX_SIZE)));
		List<FollowUpResponse> items = responses(found.getContent());
		return new PageResponse<>(items, found.getTotalElements(), found.getNumber() + 1, found.getSize());
	}

	/** Counts for the tiles. {@code dayStart} and {@code dayEnd} are the viewer's today, as instants. */
	@GetMapping("/summary")
	FollowUpService.Summary summary(@AuthenticationPrincipal CurrentUser user, @RequestParam Instant dayStart,
			@RequestParam Instant dayEnd, @RequestParam Instant monthStart) {
		return this.followUps.summary(user, dayStart, dayEnd, monthStart);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	FollowUpResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateRequest r) {
		FollowUp followUp = this.followUps.create(user, new FollowUpService.FollowUpInput(r.title(), r.type(), r.notes(),
				r.dueAt(), r.assigneeId(), r.leadId(), r.contactId(), r.dealId()));
		return responses(List.of(followUp)).getFirst();
	}

	@PatchMapping("/{id}")
	FollowUpResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateRequest r) {
		FollowUp followUp = this.followUps.update(user, id, r.version(), new FollowUpService.FollowUpInput(r.title(), r.type(),
				r.notes(), r.dueAt(), r.assigneeId(), null, null, null));
		return responses(List.of(followUp)).getFirst();
	}

	@PostMapping("/{id}/complete")
	FollowUpResponse complete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return responses(List.of(this.followUps.complete(user, id))).getFirst();
	}

	@PostMapping("/{id}/reopen")
	FollowUpResponse reopen(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return responses(List.of(this.followUps.reopen(user, id))).getFirst();
	}

	@PostMapping("/{id}/cancel")
	FollowUpResponse cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return responses(List.of(this.followUps.cancel(user, id))).getFirst();
	}

	private static UUID parse(String value) {
		try {
			return (value == null) ? null : UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			throw new com.nexus.crm.shared.error.ApiException(com.nexus.crm.shared.error.ErrorCode.MALFORMED_REQUEST,
					"assignee must be an ID or \"me\".");
		}
	}

	private List<FollowUpResponse> responses(List<FollowUp> list) {
		FollowUpService.Names names = this.followUps.names(list);
		return list.stream()
			.map((f) -> new FollowUpResponse(f.getId(), f.getTitle(), f.getType(), f.getNotes(), f.getDueAt(), f.getStatus(),
					f.getCompletedAt(), f.getAssigneeId(), names.assignees().get(f.getAssigneeId()), f.getLeadId(),
					f.getContactId(), f.getDealId(), names.about(f), f.getCreatedAt(), f.getVersion()))
			.toList();
	}

}
