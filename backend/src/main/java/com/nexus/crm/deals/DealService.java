package com.nexus.crm.deals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.contacts.ContactService;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.currency.CurrencyService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ErrorCode;
import com.nexus.crm.shared.currency.Money;
import com.nexus.crm.shared.Search;

/**
 * Deals and the one fixed pipeline they move through. Moving a deal to Won or
 * Lost saves its INR value at that day's exchange rate, so reported revenue
 * never changes afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealService {

	/** The most deals the board loads in one go. */
	private static final int BOARD_LIMIT = 1000;

	/** The board shows deals closed in the last 90 days. */
	private static final int BOARD_CLOSED_DAYS = 90;

	/** The fields of a deal. On update, a null field is left as it is. */
	public record DealInput(String name, UUID companyId, UUID primaryContactId, UUID ownerId, String stageCode,
			Money amount, LocalDate expectedCloseDate, UUID leadId, String source) {
	}

	/** Filters for the deal list. Any of them may be null. */
	public record DealFilter(String query, String stageCode, UUID ownerId, UUID companyId, UUID contactId,
			Boolean open) {
	}

	private final DealRepository deals;

	private final DealStageHistoryRepository history;

	private final PipelineStageRepository stages;

	private final ContactService contacts;

	private final CurrencyService currencies;

	private final UserService users;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	@Transactional(readOnly = true)
	public List<PipelineStage> stages() {
		return this.stages.findAllByOrderByPositionAsc();
	}

	@Transactional(readOnly = true)
	public Page<Deal> list(CurrentUser user, DealFilter filter, int page, int size) {
		return this.deals.findAll(visible(user).and(matching(filter)),
				PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
	}

	/** Every open deal, plus the ones closed recently: what the board shows. */
	@Transactional(readOnly = true)
	public List<Deal> board(CurrentUser user, UUID ownerId) {
		Instant since = Instant.now().minus(java.time.Duration.ofDays(BOARD_CLOSED_DAYS));
		Specification<Deal> recent = (root, q, cb) -> cb.or(cb.isNull(root.get("closedAt")),
				cb.greaterThanOrEqualTo(root.get("closedAt"), since));
		Specification<Deal> spec = visible(user).and(recent).and(matching(new DealFilter(null, null, ownerId, null, null, null)));
		return this.deals.findAll(spec, PageRequest.of(0, BOARD_LIMIT, Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent();
	}

	@Transactional(readOnly = true)
	public Deal get(CurrentUser user, UUID id) {
		return this.deals.findById(id)
			.filter((deal) -> canSee(user, deal.getOwnerId()))
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Deal not found."));
	}

	@Transactional
	public Deal create(CurrentUser user, DealInput in) {
		PipelineStage stage = stage(in.stageCode() == null ? "QUALIFIED" : in.stageCode());
		if (stage.getKind() != PipelineStage.Kind.OPEN) {
			throw new ApiException(ErrorCode.INVALID_STATE, "A new deal must start in an open stage.");
		}
		this.currencies.validate(in.amount());
		UUID ownerId = (in.ownerId() == null) ? user.id() : this.users.requireCanAssignTo(user, in.ownerId());
		UUID companyId = this.contacts.getCompany(user, in.companyId()).getId();
		Deal deal = new Deal(in.name().strip(), companyId, ownerId, stage.getCode(), in.amount().amount());
		deal.setCurrency(in.amount().currency().toUpperCase(java.util.Locale.ROOT));
		deal.setExpectedCloseDate(in.expectedCloseDate());
		if (in.primaryContactId() != null) {
			deal.setPrimaryContactId(this.contacts.requireVisibleContact(user, in.primaryContactId()).getId());
		}
		deal.convertedFrom(in.leadId(), in.source());
		this.deals.save(deal);
		this.history.save(new DealStageHistory(deal, null, user.id()));
		this.audit.record(user.id(), "Deal", deal.getId(), AuditLog.Action.CREATE, null);
		this.events.publishEvent(new DealEvents.Created(deal.getId(), deal.getName(), ownerId, user.id(), in.leadId(), deal.getPrimaryContactId()));
		return deal;
	}

	@Transactional
	public Deal update(CurrentUser user, UUID id, Integer version, DealInput in) {
		Deal deal = get(user, id);
		deal.checkVersion(version);
		AuditService.Changes changes = AuditService.changes();
		if (in.name() != null) {
			changes.field("name", deal.getName(), in.name().strip());
			deal.setName(in.name().strip());
		}
		if (in.amount() != null) {
			if (deal.isClosed() && !in.amount().currency().equalsIgnoreCase(deal.getCurrency())) {
				throw new ApiException(ErrorCode.INVALID_STATE, "The currency of a won or lost deal cannot change.");
			}
			this.currencies.validate(in.amount());
			changes.field("amount", deal.getAmount(), in.amount().amount())
				.field("currency", deal.getCurrency(), in.amount().currency().toUpperCase(java.util.Locale.ROOT));
			deal.setAmount(in.amount().amount());
			deal.setCurrency(in.amount().currency().toUpperCase(java.util.Locale.ROOT));
		}
		if (in.expectedCloseDate() != null) {
			changes.field("expectedCloseDate", deal.getExpectedCloseDate(), in.expectedCloseDate());
			deal.setExpectedCloseDate(in.expectedCloseDate());
		}
		if (in.primaryContactId() != null) {
			deal.setPrimaryContactId(this.contacts.requireVisibleContact(user, in.primaryContactId()).getId());
		}
		if (in.ownerId() != null && !in.ownerId().equals(deal.getOwnerId())) {
			UUID newOwner = this.users.requireCanAssignTo(user, in.ownerId());
			changes.field("ownerId", deal.getOwnerId(), newOwner);
			deal.setOwnerId(newOwner);
			this.events.publishEvent(new DealEvents.Reassigned(id, deal.getName(), newOwner, user.id(), deal.getPrimaryContactId()));
		}
		this.deals.save(deal);
		this.audit.record(user.id(), "Deal", id, AuditLog.Action.UPDATE, changes);
		return deal;
	}

	/** Moves a deal to another stage. Winning or losing it closes the deal for good. */
	@Transactional
	public Deal moveToStage(CurrentUser user, UUID id, Integer version, String stageCode, String lostReason) {
		Deal deal = get(user, id);
		deal.checkVersion(version);
		PipelineStage target = stage(stageCode);
		String from = deal.getStageCode();
		if (from.equals(target.getCode())) {
			return deal;
		}
		if (deal.isClosed()) {
			throw new ApiException(ErrorCode.INVALID_STATE, "A won or lost deal cannot move to another stage.");
		}
		switch (target.getKind()) {
			case OPEN -> deal.moveTo(target.getCode());
			case WON -> {
				Snapshot fx = snapshot(deal);
				deal.win(Instant.now(), fx.baseAmount(), fx.fxRate(), fx.fxRateDate());
			}
			case LOST -> {
				if (lostReason == null || lostReason.isBlank()) {
					throw new ApiException(ErrorCode.VALIDATION_FAILED, "Give a reason for losing the deal.");
				}
				Snapshot fx = snapshot(deal);
				deal.lose(lostReason.strip(), Instant.now(), fx.baseAmount(), fx.fxRate(), fx.fxRateDate());
			}
		}
		this.deals.save(deal);
		this.history.save(new DealStageHistory(deal, from, user.id()));
		this.audit.record(user.id(), "Deal", id, AuditLog.Action.UPDATE,
				AuditService.changes().field("stage", from, target.getCode()));
		this.events.publishEvent(new DealEvents.StageChanged(id, deal.getName(), from, target.getCode(),
				target.getName(), deal.getOwnerId(), user.id(), deal.getLostReason(), deal.getPrimaryContactId()));
		return deal;
	}

	@Transactional
	public void delete(CurrentUser user, UUID id) {
		Deal deal = get(user, id);
		deal.softDelete(Instant.now());
		this.audit.record(user.id(), "Deal", id, AuditLog.Action.DELETE, null);
	}

	/** Names for the response: companies, contacts and owners looked up once for the whole list. */
	@Transactional(readOnly = true)
	public Names names(List<Deal> list) {
		return new Names(
				this.contacts.companyNames(list.stream().map(Deal::getCompanyId).collect(Collectors.toSet())),
				this.contacts.contactNames(list.stream().map(Deal::getPrimaryContactId).filter(java.util.Objects::nonNull)
					.collect(Collectors.toSet())),
				this.users.displayNames(list.stream().map(Deal::getOwnerId).collect(Collectors.toSet())));
	}

	public record Names(Map<UUID, String> companies, Map<UUID, String> contacts, Map<UUID, String> owners) {
	}

	/** Deals with their company, for timelines and follow-up lists. */
	@Transactional(readOnly = true)
	public Map<UUID, com.nexus.crm.shared.Who> dealSummaries(java.util.Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		List<Deal> found = this.deals.findAllById(ids);
		Map<UUID, String> companies = this.contacts.companyNames(found.stream().map(Deal::getCompanyId).collect(Collectors.toSet()));
		return found.stream().collect(Collectors.toMap(Deal::getId,
				(d) -> new com.nexus.crm.shared.Who(d.getName(), companies.get(d.getCompanyId()))));
	}

	/** Deal names by ID. */
	@Transactional(readOnly = true)
	public Map<UUID, String> dealNames(java.util.Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return this.deals.findAllById(ids).stream().collect(Collectors.toMap(Deal::getId, Deal::getName));
	}

	/** Checks that the user can see this deal. Used before linking something to it. */
	@Transactional(readOnly = true)
	public Deal requireVisibleDeal(CurrentUser user, UUID id) {
		return get(user, id);
	}

	// ---- helpers ----

	private PipelineStage stage(String code) {
		return this.stages.findById(code.toUpperCase(java.util.Locale.ROOT))
			.orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown stage."));
	}

	/** The INR value of the deal at today's rate, saved when the deal closes. */
	private Snapshot snapshot(Deal deal) {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		CurrencyService.Rate rate = this.currencies.rateOn(deal.getCurrency(), today)
			.orElseThrow(() -> new ApiException(ErrorCode.INVALID_STATE,
					"There is no exchange rate for " + deal.getCurrency() + " yet, so the deal cannot be closed."));
		if (rate.stale()) {
			log.warn("Closing a deal with an old exchange rate: currency={} rateDate={}", deal.getCurrency(), rate.rateDate());
		}
		BigDecimal base = deal.getAmount().multiply(rate.rateToBase()).setScale(4, RoundingMode.HALF_UP);
		return new Snapshot(base, rate.rateToBase(), rate.rateDate());
	}

	record Snapshot(BigDecimal baseAmount, BigDecimal fxRate, LocalDate fxRateDate) {
	}

	private boolean canSee(CurrentUser user, UUID ownerId) {
		return this.users.visibleOwnerIds(user).map((ids) -> ids.contains(ownerId)).orElse(true);
	}

	private Specification<Deal> visible(CurrentUser user) {
		Set<UUID> ids = this.users.visibleOwnerIds(user).orElse(null);
		return (root, q, cb) -> (ids == null) ? cb.conjunction() : root.get("ownerId").in(ids);
	}

	private Specification<Deal> matching(DealFilter f) {
		return (root, q, cb) -> {
			var all = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
			if (f.stageCode() != null) {
				all.add(cb.equal(root.get("stageCode"), f.stageCode().toUpperCase(java.util.Locale.ROOT)));
			}
			if (f.ownerId() != null) {
				all.add(cb.equal(root.get("ownerId"), f.ownerId()));
			}
			if (f.companyId() != null) {
				all.add(cb.equal(root.get("companyId"), f.companyId()));
			}
			if (f.contactId() != null) {
				all.add(cb.equal(root.get("primaryContactId"), f.contactId()));
			}
			if (f.open() != null) {
				all.add(f.open() ? cb.isNull(root.get("closedAt")) : cb.isNotNull(root.get("closedAt")));
			}
			if (f.query() != null && !f.query().isBlank()) {
				all.add(cb.like(cb.lower(root.get("name")), Search.pattern(f.query())));
			}
			return cb.and(all.toArray(jakarta.persistence.criteria.Predicate[]::new));
		};
	}

}
