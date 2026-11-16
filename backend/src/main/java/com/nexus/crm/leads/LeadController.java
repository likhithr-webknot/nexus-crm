package com.nexus.crm.leads;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.currency.Money;
import com.nexus.crm.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
class LeadController {

	record Ref(UUID id, String name) {
	}

	record LeadResponse(UUID id, String firstName, String lastName, String name, String email, String phone,
			String companyName, String jobTitle, Lead.Source source, Lead.Status status, Ref owner, Money estimatedValue,
			String disqualifyReason, UUID convertedDealId, Instant createdAt, Instant updatedAt, Integer version) {
	}

	record CreateLeadRequest(@NotBlank @Size(max = 120) String firstName, @Size(max = 120) String lastName,
			@Email @Size(max = 254) String email, @Size(max = 40) String phone, @Size(max = 160) String companyName,
			@Size(max = 120) String jobTitle, Lead.Source source, UUID ownerId, Boolean unassigned,
			@Valid Money estimatedValue) {
	}

	/** Every field is optional. A field that is left out is not changed. */
	record UpdateLeadRequest(Integer version, @Size(max = 120) String firstName, @Size(max = 120) String lastName,
			@Email @Size(max = 254) String email, @Size(max = 40) String phone, @Size(max = 160) String companyName,
			@Size(max = 120) String jobTitle, Lead.Source source, @Valid Money estimatedValue) {
	}

	/** A null ownerId puts the lead back in the unassigned queue. */
	record AssignRequest(UUID ownerId) {
	}

	record StatusRequest(@NotNull Lead.Status status, @Size(max = 500) String reason) {
	}

	record ConvertRequest(@Size(max = 160) String dealName, @Valid Money amount, LocalDate expectedCloseDate) {
	}

	record DeleteRequest(@NotEmpty @Size(max = 500) List<UUID> ids) {
	}

	record ImportRequest(@NotNull @Size(max = LeadService.MAX_IMPORT_ROWS) List<LeadService.ImportRow> rows) {
	}

	record SummaryResponse(long total, long newThisMonth, long qualified, long unassigned, long active,
			Double totalChange, Double newThisMonthChange) {
	}

	private final LeadService leads;

	private final UserService users;

	@GetMapping
	PageResponse<LeadResponse> list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String q,
			@RequestParam(required = false) Lead.Source source, @RequestParam(required = false) Lead.Status status,
			@RequestParam(required = false) String ownerId, @RequestParam(required = false) LeadService.View view,
			@RequestParam(required = false) String sort, @RequestParam(defaultValue = "desc") String dir,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size) {
		boolean unassigned = "unassigned".equals(ownerId);
		UUID owner = (ownerId == null || unassigned) ? null : ("me".equals(ownerId) ? user.id() : parseId(ownerId));
		Page<Lead> found = this.leads.list(user, new LeadService.LeadFilter(q, source, status, owner, unassigned, view),
				page, PageResponse.clampSize(size), sort, "asc".equalsIgnoreCase(dir));
		List<LeadResponse> items = responses(found.getContent());
		return new PageResponse<>(items, found.getTotalElements(), found.getNumber() + 1, found.getSize());
	}

	@GetMapping("/summary")
	SummaryResponse summary(@AuthenticationPrincipal CurrentUser user) {
		LeadService.Summary s = this.leads.summary(user);
		return new SummaryResponse(s.total(), s.newThisMonth(), s.qualified(), s.unassigned(), s.active(), s.totalChange(),
				s.newThisMonthChange());
	}

	@GetMapping("/{id}")
	LeadResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return responses(List.of(this.leads.get(user, id))).getFirst();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	LeadResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateLeadRequest r) {
		Lead lead = this.leads.create(user, new LeadService.LeadInput(r.firstName(), r.lastName(), r.email(), r.phone(),
				r.companyName(), r.jobTitle(), r.source(), r.ownerId(), Boolean.TRUE.equals(r.unassigned()), r.estimatedValue()));
		return responses(List.of(lead)).getFirst();
	}

	@PatchMapping("/{id}")
	LeadResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateLeadRequest r) {
		Lead lead = this.leads.update(user, id, r.version(), new LeadService.LeadInput(r.firstName(), r.lastName(),
				r.email(), r.phone(), r.companyName(), r.jobTitle(), r.source(), null, false, r.estimatedValue()));
		return responses(List.of(lead)).getFirst();
	}

	@PostMapping("/{id}/assign")
	LeadResponse assign(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody AssignRequest r) {
		return responses(List.of(this.leads.assign(user, id, r.ownerId()))).getFirst();
	}

	@PostMapping("/{id}/status")
	LeadResponse status(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody StatusRequest r) {
		return responses(List.of(this.leads.changeStatus(user, id, r.status(), r.reason()))).getFirst();
	}

	@PostMapping("/{id}/convert")
	LeadService.Conversion convert(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody ConvertRequest r) {
		return this.leads.convert(user, id, r.dealName(), r.amount(), r.expectedCloseDate());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		this.leads.delete(user, List.of(id));
	}

	/** Deletes several leads at once. All of them are deleted, or none. */
	@PostMapping("/bulk-delete")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void bulkDelete(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody DeleteRequest r) {
		this.leads.delete(user, r.ids());
	}

	@PostMapping("/imports")
	LeadService.ImportResult importLeads(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ImportRequest r) {
		return this.leads.importRows(user, r.rows());
	}

	private static UUID parseId(String value) {
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			throw new com.nexus.crm.shared.error.ApiException(com.nexus.crm.shared.error.ErrorCode.MALFORMED_REQUEST, "ownerId must be an ID, \"me\" or \"unassigned\".");
		}
	}

	private List<LeadResponse> responses(List<Lead> list) {
		Map<UUID, String> owners = this.users.displayNames(list.stream().map(Lead::getOwnerId)
			.filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
		return list.stream()
			.map((l) -> new LeadResponse(l.getId(), l.getFirstName(), l.getLastName(), LeadService.fullName(l), l.getEmail(),
					l.getPhone(), l.getCompanyName(), l.getJobTitle(), l.getSource(), l.getStatus(),
					(l.getOwnerId() == null) ? null : new Ref(l.getOwnerId(), owners.get(l.getOwnerId())),
					Money.of(l.getEstimatedValue(), l.getCurrency()), l.getDisqualifyReason(), l.getConvertedDealId(),
					l.getCreatedAt(), l.getUpdatedAt(), l.getVersion()))
			.toList();
	}

}
