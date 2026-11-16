package com.nexus.crm.followups;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.activities.RecordScope;
import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.contacts.ContactService;
import com.nexus.crm.deals.DealService;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.leads.LeadService;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ErrorCode;
import com.nexus.crm.shared.Who;

/** Tasks with a due time, given to one person. A reminder goes to the person once the task is due. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpService {

	public record FollowUpInput(String title, FollowUp.Type type, String notes, Instant dueAt, UUID assigneeId,
			UUID leadId, UUID contactId, UUID dealId) {
	}

	/** Any may be null. {@code from} is included and {@code to} is not. */
	public record Filter(Instant from, Instant to, FollowUp.Status status, UUID assigneeId, UUID leadId, UUID contactId,
			UUID dealId) {
	}

	/** The tiles on the follow-ups page. */
	public record Summary(long today, long todayRemaining, long upcoming, long overdue, long completedThisMonth) {
	}

	public record Names(java.util.Map<UUID, Who> leads, java.util.Map<UUID, Who> contacts,
			java.util.Map<UUID, Who> deals, java.util.Map<UUID, String> assignees) {

		public Who about(FollowUp f) {
			if (f.getContactId() != null) {
				return this.contacts.get(f.getContactId());
			}
			return (f.getLeadId() != null) ? this.leads.get(f.getLeadId()) : this.deals.get(f.getDealId());
		}

	}

	private final FollowUpRepository followUps;

	private final LeadService leads;

	private final ContactService contacts;

	private final DealService deals;

	private final UserService users;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	@Transactional(readOnly = true)
	public Page<FollowUp> list(CurrentUser user, Filter filter, int page, int size) {
		return this.followUps.findAll(visible(user).and(matching(filter)),
				PageRequest.of(Math.max(0, page - 1), size, Sort.by("dueAt", "id")));
	}

	/** Counts for the tiles. */
	@Transactional(readOnly = true)
	public Summary summary(CurrentUser user, Instant dayStart, Instant dayEnd, Instant monthStart) {
		Specification<FollowUp> mine = visible(user);
		Instant weekEnd = dayEnd.plus(java.time.Duration.ofDays(7));
		long today = this.followUps.count(mine.and(matching(new Filter(dayStart, dayEnd, null, null, null, null, null)))
			.and(notCancelled()));
		long remaining = this.followUps.count(mine.and(matching(new Filter(dayStart, dayEnd, FollowUp.Status.OPEN, null, null, null, null))));
		long upcoming = this.followUps.count(mine.and(matching(new Filter(dayEnd, weekEnd, FollowUp.Status.OPEN, null, null, null, null))));
		long overdue = this.followUps.count(mine.and(matching(new Filter(null, dayStart, FollowUp.Status.OPEN, null, null, null, null))));
		long completed = this.followUps.count(mine.and((root, q, cb) -> cb.and(cb.equal(root.get("status"), FollowUp.Status.DONE),
				cb.greaterThanOrEqualTo(root.get("completedAt"), monthStart))));
		return new Summary(today, remaining, upcoming, overdue, completed);
	}

	@Transactional(readOnly = true)
	public FollowUp get(CurrentUser user, UUID id) {
		return this.followUps.findById(id)
			.filter((f) -> this.followUps.exists(visible(user).and((root, q, cb) -> cb.equal(root.get("id"), id))))
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Follow-up not found."));
	}

	@Transactional
	public FollowUp create(CurrentUser user, FollowUpInput in) {
		if (in.leadId() == null && in.contactId() == null && in.dealId() == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a lead, contact or deal.");
		}
		requireLinksVisible(user, in);
		UUID assignee = (in.assigneeId() == null) ? user.id() : this.users.requireCanAssignTo(user, in.assigneeId());
		FollowUp followUp = new FollowUp(in.title().strip(), in.type() == null ? FollowUp.Type.TASK : in.type(), in.dueAt(),
				assignee, in.leadId(), in.contactId(), in.dealId());
		followUp.setNotes(blankToNull(in.notes()));
		this.followUps.save(followUp);
		this.audit.record(user.id(), "FollowUp", followUp.getId(), AuditLog.Action.CREATE, null);
		if (!assignee.equals(user.id())) {
			this.events.publishEvent(new FollowUpEvents.Assigned(followUp.getId(), followUp.getTitle(), assignee, user.id()));
		}
		return followUp;
	}

	@Transactional
	public FollowUp update(CurrentUser user, UUID id, Integer version, FollowUpInput in) {
		FollowUp followUp = get(user, id);
		followUp.checkVersion(version);
		AuditService.Changes changes = AuditService.changes();
		if (in.title() != null) {
			changes.field("title", followUp.getTitle(), in.title().strip());
			followUp.setTitle(in.title().strip());
		}
		if (in.type() != null) {
			followUp.setType(in.type());
		}
		if (in.notes() != null) {
			followUp.setNotes(blankToNull(in.notes()));
		}
		if (in.dueAt() != null) {
			changes.field("dueAt", followUp.getDueAt(), in.dueAt());
			if (!in.dueAt().equals(followUp.getDueAt())) {
				followUp.markReminded(null);
			}
			followUp.setDueAt(in.dueAt());
		}
		if (in.assigneeId() != null && !in.assigneeId().equals(followUp.getAssigneeId())) {
			UUID assignee = this.users.requireCanAssignTo(user, in.assigneeId());
			changes.field("assigneeId", followUp.getAssigneeId(), assignee);
			followUp.setAssigneeId(assignee);
			if (!assignee.equals(user.id())) {
				this.events.publishEvent(new FollowUpEvents.Assigned(id, followUp.getTitle(), assignee, user.id()));
			}
		}
		this.followUps.save(followUp);
		this.audit.record(user.id(), "FollowUp", id, AuditLog.Action.UPDATE, changes);
		return followUp;
	}

	@Transactional
	public FollowUp complete(CurrentUser user, UUID id) {
		return change(user, id, (f) -> f.complete(Instant.now()));
	}

	@Transactional
	public FollowUp reopen(CurrentUser user, UUID id) {
		return change(user, id, FollowUp::reopen);
	}

	@Transactional
	public FollowUp cancel(CurrentUser user, UUID id) {
		return change(user, id, FollowUp::cancel);
	}

	/** Sends the reminder for every follow-up that has come due. Runs every minute. */
	@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
	@Transactional
	public void sendDueReminders() {
		Instant now = Instant.now();
		for (FollowUp followUp : this.followUps.findByStatusAndRemindedAtIsNullAndDueAtLessThanEqual(FollowUp.Status.OPEN, now)) {
			followUp.markReminded(now);
			this.events.publishEvent(new FollowUpEvents.Due(followUp.getId(), followUp.getTitle(), followUp.getAssigneeId()));
		}
	}

	@Transactional(readOnly = true)
	public Names names(List<FollowUp> list) {
		return new Names(this.leads.leadSummaries(ids(list, FollowUp::getLeadId)),
				this.contacts.contactSummaries(ids(list, FollowUp::getContactId)),
				this.deals.dealSummaries(ids(list, FollowUp::getDealId)),
				this.users.displayNames(ids(list, FollowUp::getAssigneeId)));
	}

	// ---- helpers ----

	private FollowUp change(CurrentUser user, UUID id, java.util.function.Consumer<FollowUp> action) {
		FollowUp followUp = get(user, id);
		FollowUp.Status before = followUp.getStatus();
		try {
			action.accept(followUp);
		}
		catch (IllegalStateException ex) {
			throw new ApiException(ErrorCode.INVALID_STATE, "The follow-up is already " + before.name().toLowerCase(java.util.Locale.ROOT) + ".");
		}
		this.followUps.save(followUp);
		this.audit.record(user.id(), "FollowUp", id, AuditLog.Action.UPDATE,
				AuditService.changes().field("status", before, followUp.getStatus()));
		return followUp;
	}

	private void requireLinksVisible(CurrentUser user, FollowUpInput in) {
		if (in.leadId() != null) {
			this.leads.get(user, in.leadId());
		}
		if (in.contactId() != null) {
			this.contacts.requireVisibleContact(user, in.contactId());
		}
		if (in.dealId() != null) {
			this.deals.requireVisibleDeal(user, in.dealId());
		}
	}

	/** Follow-ups given to someone the user can see, or attached to something the user can see. */
	private Specification<FollowUp> visible(CurrentUser user) {
		Set<UUID> ids = this.users.visibleOwnerIds(user).orElse(null);
		return (root, query, cb) -> (ids == null) ? cb.conjunction()
				: cb.or(root.get("assigneeId").in(ids), RecordScope.linkedTo(ids, root, query, cb));
	}

	private static Specification<FollowUp> notCancelled() {
		return (root, q, cb) -> cb.notEqual(root.get("status"), FollowUp.Status.CANCELLED);
	}

	private static Specification<FollowUp> matching(Filter f) {
		return (root, q, cb) -> {
			List<Predicate> all = new ArrayList<>();
			if (f.from() != null) {
				all.add(cb.greaterThanOrEqualTo(root.get("dueAt"), f.from()));
			}
			if (f.to() != null) {
				all.add(cb.lessThan(root.get("dueAt"), f.to()));
			}
			if (f.status() != null) {
				all.add(cb.equal(root.get("status"), f.status()));
			}
			if (f.assigneeId() != null) {
				all.add(cb.equal(root.get("assigneeId"), f.assigneeId()));
			}
			if (f.leadId() != null) {
				all.add(cb.equal(root.get("leadId"), f.leadId()));
			}
			if (f.contactId() != null) {
				all.add(cb.equal(root.get("contactId"), f.contactId()));
			}
			if (f.dealId() != null) {
				all.add(cb.equal(root.get("dealId"), f.dealId()));
			}
			return cb.and(all.toArray(Predicate[]::new));
		};
	}

	private static <T> Set<UUID> ids(List<T> list, java.util.function.Function<T, UUID> getter) {
		return list.stream().map(getter).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
