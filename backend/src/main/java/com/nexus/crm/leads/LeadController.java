package com.nexus.crm.leads;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.nexus.crm.leads.dto.AssignLeadRequest;
import com.nexus.crm.leads.dto.ConvertLeadRequest;
import com.nexus.crm.leads.dto.CreateLeadRequest;
import com.nexus.crm.leads.dto.DeleteLeadsRequest;
import com.nexus.crm.leads.dto.LeadConversion;
import com.nexus.crm.leads.dto.LeadFilter;
import com.nexus.crm.leads.dto.LeadImportRequest;
import com.nexus.crm.leads.dto.LeadImportResult;
import com.nexus.crm.leads.dto.LeadInput;
import com.nexus.crm.leads.dto.LeadResponse;
import com.nexus.crm.leads.dto.LeadStatusRequest;
import com.nexus.crm.leads.dto.LeadSummary;
import com.nexus.crm.leads.dto.LeadSummaryResponse;
import com.nexus.crm.leads.dto.LeadView;
import com.nexus.crm.leads.dto.UpdateLeadRequest;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.limits.RateLimit;
import com.nexus.crm.shared.Ref;
import com.nexus.crm.shared.currency.Money;
import com.nexus.crm.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
class LeadController {

	private final LeadService leads;

	private final UserService users;

	@GetMapping
	PageResponse<LeadResponse> list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String q,
			@RequestParam(required = false) Lead.Source source, @RequestParam(required = false) Lead.Status status,
			@RequestParam(required = false) String ownerId, @RequestParam(required = false) LeadView view,
			@RequestParam(required = false) String sort, @RequestParam(defaultValue = "desc") String dir,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size) {
		boolean unassigned = "unassigned".equals(ownerId);
		UUID owner = (ownerId == null || unassigned) ? null : ("me".equals(ownerId) ? user.id() : parseId(ownerId));
		Page<Lead> found = this.leads.list(user, new LeadFilter(q, source, status, owner, unassigned, view),
				page, PageResponse.clampSize(size), sort, "asc".equalsIgnoreCase(dir));
		List<LeadResponse> items = responses(found.getContent());
		return new PageResponse<>(items, found.getTotalElements(), found.getNumber() + 1, found.getSize());
	}

	@GetMapping("/summary")
	LeadSummaryResponse summary(@AuthenticationPrincipal CurrentUser user) {
		LeadSummary s = this.leads.summary(user);
		return new LeadSummaryResponse(s.total(), s.newThisMonth(), s.qualified(), s.unassigned(), s.active(), s.totalChange(),
				s.newThisMonthChange());
	}

	@GetMapping("/{id}")
	LeadResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return responses(List.of(this.leads.get(user, id))).getFirst();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	LeadResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateLeadRequest r) {
		Lead lead = this.leads.create(user, new LeadInput(r.firstName(), r.lastName(), r.email(), r.phone(),
				r.companyName(), r.jobTitle(), r.source(), r.ownerId(), Boolean.TRUE.equals(r.unassigned()), r.estimatedValue()));
		return responses(List.of(lead)).getFirst();
	}

	@PatchMapping("/{id}")
	LeadResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateLeadRequest r) {
		Lead lead = this.leads.update(user, id, r.version(), new LeadInput(r.firstName(), r.lastName(),
				r.email(), r.phone(), r.companyName(), r.jobTitle(), r.source(), null, false, r.estimatedValue()));
		return responses(List.of(lead)).getFirst();
	}

	@PostMapping("/{id}/assign")
	@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
	LeadResponse assign(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody AssignLeadRequest r) {
		return responses(List.of(this.leads.assign(user, id, r.ownerId()))).getFirst();
	}

	@PostMapping("/{id}/status")
	LeadResponse status(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody LeadStatusRequest r) {
		return responses(List.of(this.leads.changeStatus(user, id, r.status(), r.reason()))).getFirst();
	}

	@PostMapping("/{id}/convert")
	LeadConversion convert(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody ConvertLeadRequest r) {
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
	void bulkDelete(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody DeleteLeadsRequest r) {
		this.leads.delete(user, r.ids());
	}

	/** Each import reports which emails already exist, so it is limited to stop it being used to look up emails in bulk. */
	private static final RateLimit IMPORT_LIMIT = new RateLimit(10, Duration.ofHours(1));

	@PostMapping("/imports")
	LeadImportResult importLeads(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody LeadImportRequest r) {
		IMPORT_LIMIT.require(user.id());
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
