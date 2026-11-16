package com.nexus.crm.deals;

import java.util.List;
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

import com.nexus.crm.deals.dto.CreateDealRequest;
import com.nexus.crm.deals.dto.DealFilter;
import com.nexus.crm.deals.dto.DealInput;
import com.nexus.crm.deals.dto.DealNames;
import com.nexus.crm.deals.dto.DealResponse;
import com.nexus.crm.deals.dto.MoveRequest;
import com.nexus.crm.deals.dto.PipelineStageResponse;
import com.nexus.crm.deals.dto.UpdateDealRequest;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Ref;
import com.nexus.crm.shared.currency.Money;
import com.nexus.crm.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class DealController {

	private final DealService deals;

	@GetMapping("/pipeline/stages")
	List<PipelineStageResponse> stages() {
		return this.deals.stages().stream().map(PipelineStageResponse::from).toList();
	}

	/** Every open deal and the recently closed ones, in one call. */
	@GetMapping("/pipeline/board")
	List<DealResponse> board(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) UUID ownerId) {
		return responses(this.deals.board(user, ownerId));
	}

	@GetMapping("/deals")
	PageResponse<DealResponse> list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String q,
			@RequestParam(required = false) String stage, @RequestParam(required = false) UUID ownerId,
			@RequestParam(required = false) UUID companyId, @RequestParam(required = false) UUID contactId,
			@RequestParam(required = false) Boolean open, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "25") int size) {
		Page<Deal> found = this.deals.list(user, new DealFilter(q, stage, ownerId, companyId, contactId, open),
				page, PageResponse.clampSize(size));
		List<DealResponse> items = responses(found.getContent());
		return new PageResponse<>(items, found.getTotalElements(), found.getNumber() + 1, found.getSize());
	}

	@GetMapping("/deals/{id}")
	DealResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return responses(List.of(this.deals.get(user, id))).getFirst();
	}

	@PostMapping("/deals")
	@ResponseStatus(HttpStatus.CREATED)
	DealResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateDealRequest r) {
		Deal deal = this.deals.create(user, new DealInput(r.name(), r.companyId(), r.primaryContactId(),
				r.ownerId(), r.stageCode(), r.amount(), r.expectedCloseDate(), null, null));
		return responses(List.of(deal)).getFirst();
	}

	@PatchMapping("/deals/{id}")
	DealResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateDealRequest r) {
		Deal deal = this.deals.update(user, id, r.version(), new DealInput(r.name(), null,
				r.primaryContactId(), r.ownerId(), null, r.amount(), r.expectedCloseDate(), null, null));
		return responses(List.of(deal)).getFirst();
	}

	@PatchMapping("/deals/{id}/stage")
	DealResponse move(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody MoveRequest r) {
		Deal deal = this.deals.moveToStage(user, id, r.version(), r.stageCode(), r.lostReason());
		return responses(List.of(deal)).getFirst();
	}

	@DeleteMapping("/deals/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		this.deals.delete(user, id);
	}

	private List<DealResponse> responses(List<Deal> list) {
		DealNames names = this.deals.names(list);
		return list.stream()
			.map((d) -> new DealResponse(d.getId(), d.getName(), new Ref(d.getCompanyId(), names.companies().get(d.getCompanyId())),
					(d.getPrimaryContactId() == null) ? null
							: new Ref(d.getPrimaryContactId(), names.contacts().get(d.getPrimaryContactId())),
					new Ref(d.getOwnerId(), names.owners().get(d.getOwnerId())), d.getStageCode(),
					new Money(d.getAmount(), d.getCurrency()), d.getBaseAmount(), d.getFxRate(), d.getFxRateDate(),
					d.getExpectedCloseDate(), d.getClosedAt(), d.getLostReason(), d.getLeadId(), d.getCreatedAt(),
					d.getUpdatedAt(), d.getVersion()))
			.toList();
	}

}
