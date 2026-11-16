package com.nexus.crm.activities;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.activities.dto.ActivityFilter;
import com.nexus.crm.activities.dto.ActivityInput;
import com.nexus.crm.activities.dto.ActivityNames;
import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.contacts.ContactService;
import com.nexus.crm.deals.DealService;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.leads.LeadService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/** The timeline: notes, calls and meetings that people log, and entries the app writes by itself. */
@Service
@RequiredArgsConstructor
public class ActivityService {

	private final ActivityRepository activities;

	private final LeadService leads;

	private final ContactService contacts;

	private final DealService deals;

	private final UserService users;

	private final AuditService audit;

	@Transactional(readOnly = true)
	public Page<Activity> list(CurrentUser user, ActivityFilter filter, int page, int size) {
		return this.activities.findAll(visible(user).and(matching(filter)), PageRequest.of(Math.max(0, page - 1), size,
				Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
	}

	/** How many activities of each type match, ignoring the type filter. They are the numbers on the filter chips. */
	@Transactional(readOnly = true)
	public Map<Activity.Type, Long> counts(CurrentUser user, UUID actorId) {
		Map<Activity.Type, Long> counts = new EnumMap<>(Activity.Type.class);
		for (Activity.Type type : Activity.Type.values()) {
			counts.put(type, this.activities.count(visible(user).and(matching(new ActivityFilter(type, actorId, null, null, null)))));
		}
		return counts;
	}

	/** Logs something a person did. SYSTEM entries cannot be made by hand. */
	@Transactional
	public Activity log(CurrentUser user, ActivityInput in) {
		if (in.type() == Activity.Type.SYSTEM) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "System entries are written by the app.");
		}
		requireLinkVisible(user, in.leadId(), in.contactId(), in.dealId());
		Activity activity = new Activity(in.type(), in.subject().strip(), in.occurredAt() == null ? Instant.now() : in.occurredAt(),
				user.id(), in.leadId(), in.contactId(), in.dealId());
		activity.setBody(blankToNull(in.body()));
		this.activities.save(activity);
		this.audit.record(user.id(), "Activity", activity.getId(), AuditLog.Action.CREATE, null);
		return activity;
	}

	/** Writes an entry about something the app did. Called by the listeners, never by a request. */
	@Transactional
	public void system(String subject, String body, UUID actorId, UUID leadId, UUID contactId, UUID dealId) {
		Activity activity = new Activity(Activity.Type.SYSTEM, subject, Instant.now(), null, leadId, contactId, dealId);
		activity.setBody(body);
		this.activities.save(activity);
	}

	/** Only the author can change or delete an entry. */
	@Transactional
	public Activity update(CurrentUser user, UUID id, Integer version, String subject, String body) {
		Activity activity = ownEntry(user, id);
		activity.checkVersion(version);
		if (subject != null) {
			activity.setSubject(subject.strip());
		}
		if (body != null) {
			activity.setBody(blankToNull(body));
		}
		this.activities.save(activity);
		this.audit.record(user.id(), "Activity", id, AuditLog.Action.UPDATE, null);
		return activity;
	}

	@Transactional
	public void delete(CurrentUser user, UUID id) {
		Activity activity = ownEntry(user, id);
		activity.softDelete(Instant.now());
		this.audit.record(user.id(), "Activity", id, AuditLog.Action.DELETE, null);
	}

	/** ActivityNames for the response: who did it, and who or what it was about. */
	@Transactional(readOnly = true)
	public ActivityNames names(List<Activity> list) {
		return new ActivityNames(this.leads.leadSummaries(ids(list, Activity::getLeadId)),
				this.contacts.contactSummaries(ids(list, Activity::getContactId)),
				this.deals.dealSummaries(ids(list, Activity::getDealId)),
				this.users.displayNames(ids(list, Activity::getActorId)));
	}

	// ---- helpers ----

	private Activity ownEntry(CurrentUser user, UUID id) {
		Activity activity = this.activities.findById(id)
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Activity not found."));
		if (activity.getType() == Activity.Type.SYSTEM || !user.id().equals(activity.getActorId())) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Only the person who logged an entry can change it.");
		}
		return activity;
	}

	/** Each given link must point to something the user can see. */
	private void requireLinkVisible(CurrentUser user, UUID leadId, UUID contactId, UUID dealId) {
		if (leadId == null && contactId == null && dealId == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a lead, contact or deal.");
		}
		if (leadId != null) {
			this.leads.get(user, leadId);
		}
		if (contactId != null) {
			this.contacts.requireVisibleContact(user, contactId);
		}
		if (dealId != null) {
			this.deals.requireVisibleDeal(user, dealId);
		}
	}

	private Specification<Activity> visible(CurrentUser user) {
		Set<UUID> ids = this.users.visibleOwnerIds(user).orElse(null);
		return (root, query, cb) -> RecordScope.linkedTo(ids, root, query, cb);
	}

	private static Specification<Activity> matching(ActivityFilter f) {
		return (root, q, cb) -> {
			List<Predicate> all = new ArrayList<>();
			if (f.type() != null) {
				all.add(cb.equal(root.get("type"), f.type()));
			}
			if (f.actorId() != null) {
				all.add(cb.equal(root.get("actorId"), f.actorId()));
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

	private static <T> Set<UUID> ids(Collection<T> list, java.util.function.Function<T, UUID> getter) {
		return list.stream().map(getter).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
