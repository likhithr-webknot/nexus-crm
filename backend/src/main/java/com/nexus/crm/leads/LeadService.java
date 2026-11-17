package com.nexus.crm.leads;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.contacts.Company;
import com.nexus.crm.contacts.Contact;
import com.nexus.crm.contacts.ContactService;
import com.nexus.crm.contacts.dto.ContactInput;
import com.nexus.crm.deals.Deal;
import com.nexus.crm.deals.DealService;
import com.nexus.crm.deals.dto.DealInput;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.leads.dto.LeadConversion;
import com.nexus.crm.leads.dto.LeadFilter;
import com.nexus.crm.leads.dto.LeadImportResult;
import com.nexus.crm.leads.dto.LeadImportRow;
import com.nexus.crm.leads.dto.LeadImportRowError;
import com.nexus.crm.leads.dto.LeadInput;
import com.nexus.crm.leads.dto.LeadSummary;
import com.nexus.crm.leads.dto.LeadView;
import com.nexus.crm.leads.event.LeadAssigned;
import com.nexus.crm.leads.event.LeadConverted;
import com.nexus.crm.leads.event.LeadStatusChanged;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Emails;
import com.nexus.crm.shared.Search;
import com.nexus.crm.shared.currency.CurrencyService;
import com.nexus.crm.shared.currency.Money;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;
import com.nexus.crm.tags.TagService;

/** Leads. A user sees the leads they own, their team's (managers), everyone's (admins), and the unassigned queue. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

	/** The most rows one import may hold. */
	public static final int MAX_IMPORT_ROWS = 10_000;

	private static final Pattern EMAIL = Pattern.compile("^\\S+@\\S+\\.\\S+$");

	private final LeadRepository leads;

	private final LeadStatusHistoryRepository history;

	private final ImportJobRepository importJobs;

	private final ContactService contacts;

	private final DealService deals;

	private final CurrencyService currencies;

	private final UserService users;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	private final JsonMapper json;

	private final TagService tags;

	// ---- reading ----

	@Transactional(readOnly = true)
	public Page<Lead> list(CurrentUser user, LeadFilter filter, int page, int size, String sortField, boolean ascending) {
		String field = switch (sortField == null ? "" : sortField) {
			case "name" -> "firstName";
			case "status" -> "status";
			default -> "createdAt";
		};
		Sort sort = Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, field).and(Sort.by("id"));
		return this.leads.findAll(visible(user).and(matching(filter)).and(TagService.hasTag("lead", filter.tagId())), PageRequest.of(Math.max(0, page - 1), size, sort));
	}

	@Transactional(readOnly = true)
	public Lead get(CurrentUser user, UUID id) {
		return this.leads.findById(id)
			.filter((lead) -> canSee(user, lead))
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Lead not found."));
	}

	@Transactional(readOnly = true)
	public LeadSummary summary(CurrentUser user) {
		Specification<Lead> mine = visible(user);
		Instant monthStart = monthStart(0);
		Instant lastMonthStart = monthStart(-1);
		// "This month so far" is compared with the same stretch of last month, so early in the month is fair.
		Instant sameMomentLastMonth = lastMonthStart.plus(java.time.Duration.between(monthStart, Instant.now()));

		long total = this.leads.count(mine);
		long totalBeforeThisMonth = this.leads.count(mine.and(createdBetween(null, monthStart)));
		long newThisMonth = this.leads.count(mine.and(createdBetween(monthStart, null)));
		long newLastMonth = this.leads.count(mine.and(createdBetween(lastMonthStart, sameMomentLastMonth)));
		long qualified = this.leads.count(mine.and(matching(filterOf(LeadView.QUALIFIED))));
		long unassigned = this.leads.count(mine.and(matching(filterOf(LeadView.UNASSIGNED))));
		long active = this.leads.count(mine.and(statusIn(Lead.Status.NEW, Lead.Status.CONTACTED, Lead.Status.QUALIFIED)));
		return new LeadSummary(total, newThisMonth, qualified, unassigned, active, percentChange(totalBeforeThisMonth, total),
				percentChange(newLastMonth, newThisMonth));
	}

	/** Lead names with their company, for timelines and follow-up lists. */
	@Transactional(readOnly = true)
	public java.util.Map<UUID, com.nexus.crm.shared.Label> leadSummaries(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return java.util.Map.of();
		}
		return this.leads.findAllById(ids)
			.stream()
			.collect(java.util.stream.Collectors.toMap(Lead::getId, (l) -> new com.nexus.crm.shared.Label(fullName(l), l.getCompanyName())));
	}

	/** Lead names by ID, for timelines and follow-up lists. */
	@Transactional(readOnly = true)
	public java.util.Map<UUID, String> leadNames(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return java.util.Map.of();
		}
		return this.leads.findAllById(ids)
			.stream()
			.collect(java.util.stream.Collectors.toMap(Lead::getId, LeadService::fullName));
	}

	// ---- changing ----

	@Transactional
	public Lead create(CurrentUser user, LeadInput in) {
		requireEmailFree(Emails.normalize(in.email()), null);
		Lead lead = new Lead(in.firstName().strip(), blankToEmpty(in.lastName()),
				in.source() == null ? Lead.Source.MANUAL : in.source());
		fill(lead, in);
		lead.setOwnerId(in.unassigned() ? null : ownerFor(user, in.ownerId()));
		this.leads.save(lead);
		this.history.save(new LeadStatusHistory(lead.getId(), null, Lead.Status.NEW, user.id()));
		this.audit.record(user.id(), "Lead", lead.getId(), AuditLog.Action.CREATE, null);
		if (lead.getOwnerId() != null) {
			this.events.publishEvent(new LeadAssigned(lead.getId(), fullName(lead), lead.getOwnerId(), user.id()));
		}
		return lead;
	}

	@Transactional
	public Lead update(CurrentUser user, UUID id, Integer version, LeadInput in) {
		Lead lead = get(user, id);
		lead.checkVersion(version);
		AuditService.Changes changes = AuditService.changes()
			.field("firstName", lead.getFirstName(), in.firstName() == null ? lead.getFirstName() : in.firstName().strip())
			.field("lastName", lead.getLastName(), in.lastName() == null ? lead.getLastName() : in.lastName().strip())
			.field("email", lead.getEmail(), in.email() == null ? lead.getEmail() : Emails.normalize(in.email()))
			.field("companyName", lead.getCompanyName(), in.companyName() == null ? lead.getCompanyName() : in.companyName().strip());
		if (in.email() != null) {
			requireEmailFree(Emails.normalize(in.email()), lead.getId());
		}
		if (lead.getStatus() == Lead.Status.CONVERTED) {
			throw new ApiException(ErrorCode.INVALID_STATE, "A converted lead cannot be edited.");
		}
		fill(lead, in);
		this.leads.save(lead);
		this.audit.record(user.id(), "Lead", id, AuditLog.Action.UPDATE, changes);
		return lead;
	}

	/** Gives a lead to someone, or to nobody. The controller only lets managers and admins call this. */
	@Transactional
	public Lead assign(CurrentUser user, UUID id, UUID ownerId) {
		Lead lead = get(user, id);
		UUID newOwner = (ownerId == null) ? null : this.users.requireCanAssignTo(user, ownerId);
		AuditService.Changes changes = AuditService.changes().field("ownerId", lead.getOwnerId(), newOwner);
		lead.setOwnerId(newOwner);
		this.leads.save(lead);
		this.audit.record(user.id(), "Lead", id, AuditLog.Action.UPDATE, changes);
		this.events.publishEvent(new LeadAssigned(id, fullName(lead), newOwner, user.id()));
		return lead;
	}

	/** Moves a lead between NEW, CONTACTED and QUALIFIED, or disqualifies it (a reason is needed). */
	@Transactional
	public Lead changeStatus(CurrentUser user, UUID id, Lead.Status status, String reason) {
		Lead lead = get(user, id);
		Lead.Status from = lead.getStatus();
		if (status == Lead.Status.CONVERTED) {
			throw new ApiException(ErrorCode.INVALID_STATE, "Use convert to turn a lead into a deal.");
		}
		if (from == status) {
			return lead;
		}
		try {
			if (status == Lead.Status.DISQUALIFIED) {
				lead.disqualify(reason);
			}
			else {
				lead.changeStatus(status);
			}
		}
		catch (IllegalStateException ex) {
			throw new ApiException(ErrorCode.INVALID_STATE, "The lead is already " + from.name().toLowerCase(Locale.ROOT) + ".");
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Give a reason for disqualifying the lead.");
		}
		this.leads.save(lead);
		this.history.save(new LeadStatusHistory(id, from, status, user.id()));
		this.audit.record(user.id(), "Lead", id, AuditLog.Action.UPDATE, AuditService.changes().field("status", from, status));
		this.events.publishEvent(new LeadStatusChanged(id, fullName(lead), from, status, user.id()));
		return lead;
	}

	@Transactional
	public void delete(CurrentUser user, Collection<UUID> ids) {
		for (UUID id : ids) {
			Lead lead = get(user, id);
			lead.softDelete(Instant.now());
			this.audit.record(user.id(), "Lead", id, AuditLog.Action.DELETE, null);
		}
	}

	/** Gives several leads to one person, or to nobody. All of them change, or none do. */
	@Transactional
	public int assign(CurrentUser user, Collection<UUID> ids, UUID ownerId) {
		Set<UUID> unique = new java.util.LinkedHashSet<>(ids);
		for (UUID id : unique) {
			assign(user, id, ownerId);
		}
		return unique.size();
	}

	/** Moves several leads to one status. If one lead cannot move, nothing changes and the message says why. */
	@Transactional
	public int changeStatus(CurrentUser user, Collection<UUID> ids, Lead.Status status, String reason) {
		Set<UUID> unique = new java.util.LinkedHashSet<>(ids);
		for (UUID id : unique) {
			changeStatus(user, id, status, reason);
		}
		return unique.size();
	}

	/** Adds and removes tags on several leads. */
	@Transactional
	public int tag(CurrentUser user, Collection<UUID> ids, Collection<UUID> add, Collection<UUID> remove) {
		Set<UUID> unique = new java.util.LinkedHashSet<>(ids);
		unique.forEach((id) -> get(user, id));
		this.tags.change(user, "lead", unique, add, remove);
		return unique.size();
	}

	/** Turns a lead into a company, a contact and a deal, all in one transaction: either everything is created or nothing is. */
	@Transactional
	public LeadConversion convert(CurrentUser user, UUID id, String dealName, Money amount, LocalDate expectedCloseDate) {
		Lead lead = get(user, id);
		if (lead.getStatus() == Lead.Status.CONVERTED || lead.getStatus() == Lead.Status.DISQUALIFIED) {
			throw new ApiException(ErrorCode.INVALID_STATE, "A " + lead.getStatus().name().toLowerCase(Locale.ROOT) + " lead cannot be converted.");
		}
		UUID ownerId = (lead.getOwnerId() != null) ? lead.getOwnerId() : user.id();
		this.users.requireCanAssignTo(user, ownerId);

		String companyName = (lead.getCompanyName() == null || lead.getCompanyName().isBlank()) ? fullName(lead) : lead.getCompanyName();
		Company company = this.contacts.findOrCreateCompany(user, companyName, ownerId);
		Contact contact = this.contacts.findOrCreateContact(user, new ContactInput(lead.getFirstName(),
				lead.getLastName(), lead.getEmail(), lead.getPhone(), lead.getJobTitle(), company.getId(), null, ownerId), ownerId);

		Money dealAmount = (amount != null) ? amount : (lead.getEstimatedValue() != null)
				? new Money(lead.getEstimatedValue(), lead.getCurrency()) : new Money(BigDecimal.ZERO, this.currencies.base().getCode());
		Deal deal = this.deals.create(user, new DealInput((dealName == null || dealName.isBlank()) ? companyName : dealName.strip(),
				company.getId(), contact.getId(), ownerId, null, dealAmount, expectedCloseDate, lead.getId(), lead.getSource().name()));

		Lead.Status from = lead.getStatus();
		lead.convert(company.getId(), contact.getId(), deal.getId(), Instant.now());
		this.leads.save(lead);
		this.history.save(new LeadStatusHistory(id, from, Lead.Status.CONVERTED, user.id()));
		this.audit.record(user.id(), "Lead", id, AuditLog.Action.CONVERT, null);
		this.events.publishEvent(new LeadConverted(id, fullName(lead), deal.getId(), company.getId(), contact.getId(), user.id()));
		return new LeadConversion(id, company.getId(), contact.getId(), deal.getId());
	}

	/** Imports leads that the browser read from a CSV file. */
	@Transactional
	public LeadImportResult importRows(CurrentUser user, List<LeadImportRow> rows) {
		if (rows.size() > MAX_IMPORT_ROWS) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "An import can hold at most " + MAX_IMPORT_ROWS + " rows.");
		}
		ImportJob job = new ImportJob(user.id());
		job.start();
		List<LeadImportRowError> errors = new ArrayList<>();
		List<Lead> ready = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		Set<String> taken = new HashSet<>(this.leads.findOpenEmails(rows.stream()
			.map((r) -> Emails.normalize(r.email()))
			.filter(java.util.Objects::nonNull)
			.toList()));
		int number = 0;
		for (LeadImportRow row : rows) {
			number++;
			String problem = check(row);
			String email = Emails.normalize(row.email());
			if (problem == null && (taken.contains(email) || !seen.add(email))) {
				problem = "This email is already used by an open lead.";
			}
			if (problem != null) {
				errors.add(new LeadImportRowError(number, problem));
				continue;
			}
			ready.add(leadFromRow(row));
		}
		this.leads.saveAll(ready);
		for (Lead lead : ready) {
			this.history.save(new LeadStatusHistory(lead.getId(), null, Lead.Status.NEW, user.id()));
			this.audit.record(user.id(), "Lead", lead.getId(), AuditLog.Action.CREATE, null);
		}
		job.finish(rows.size(), ready.size(), errors.size(), this.json.writeValueAsString(errors));
		this.importJobs.save(job);
		log.info("Leads imported: jobId={} total={} imported={} failed={} by={}", job.getId(), rows.size(), ready.size(), errors.size(), user.id());
		return new LeadImportResult(rows.size(), ready.size(), errors);
	}

	// ---- helpers ----

	private String check(LeadImportRow row) {
		if (row.name() == null || row.name().isBlank()) {
			return "The name is missing.";
		}
		String email = Emails.normalize(row.email());
		if (email == null || !EMAIL.matcher(email).matches() || email.length() > 254) {
			return "The email is missing or not valid.";
		}
		if (row.name().length() > 240) {
			return "The name is too long.";
		}
		BigDecimal value = parseValue(row.value());
		if (row.value() != null && !row.value().isBlank() && value == null) {
			return "The estimated value is not a valid amount.";
		}
		if (value != null) {
			try {
				this.currencies.validate(new Money(value, currencyOf(row)));
			}
			catch (ApiException ex) {
				return ex.getMessage();
			}
		}
		return null;
	}

	private Lead leadFromRow(LeadImportRow row) {
		String[] name = splitName(row.name());
		Lead lead = new Lead(name[0], name[1], parseSource(row.source()));
		lead.setEmail(row.email());
		lead.setCompanyName(blankToNull(row.company()));
		lead.setPhone(blankToNull(row.phone()));
		BigDecimal value = parseValue(row.value());
		if (value != null) {
			lead.setEstimatedValue(value);
		}
		lead.setCurrency(currencyOf(row));
		return lead;
	}

	private String currencyOf(LeadImportRow row) {
		return (row.currency() == null || row.currency().isBlank()) ? this.currencies.base().getCode()
				: row.currency().strip().toUpperCase(Locale.ROOT);
	}

	private static BigDecimal parseValue(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			BigDecimal amount = new BigDecimal(value.strip());
			return amount.signum() < 0 ? null : amount;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Lead.Source parseSource(String value) {
		if (value == null) {
			return Lead.Source.IMPORT;
		}
		try {
			return Lead.Source.valueOf(value.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			return Lead.Source.IMPORT;
		}
	}

	/** "Mary Jane Watson" becomes first name "Mary Jane" and last name "Watson". One word is a first name only. */
	public static String[] splitName(String fullName) {
		String name = fullName.strip();
		int space = name.lastIndexOf(' ');
		return (space < 0) ? new String[] { name, "" } : new String[] { name.substring(0, space).strip(), name.substring(space + 1) };
	}

	public static String fullName(Lead lead) {
		return (lead.getFirstName() + " " + lead.getLastName()).strip();
	}

	private void fill(Lead lead, LeadInput in) {
		if (in.firstName() != null) {
			lead.setFirstName(in.firstName().strip());
		}
		if (in.lastName() != null) {
			lead.setLastName(in.lastName().strip());
		}
		if (in.email() != null) {
			lead.setEmail(in.email());
		}
		if (in.phone() != null) {
			lead.setPhone(blankToNull(in.phone()));
		}
		if (in.companyName() != null) {
			lead.setCompanyName(blankToNull(in.companyName()));
		}
		if (in.jobTitle() != null) {
			lead.setJobTitle(blankToNull(in.jobTitle()));
		}
		if (in.source() != null) {
			lead.setSource(in.source());
		}
		if (in.estimatedValue() != null) {
			this.currencies.validate(in.estimatedValue());
			lead.setEstimatedValue(in.estimatedValue().amount());
			lead.setCurrency(in.estimatedValue().currency().toUpperCase(Locale.ROOT));
		}
	}

	/** The owner for a new lead: the person chosen, or the creator. */
	private UUID ownerFor(CurrentUser user, UUID requested) {
		return (requested == null) ? user.id() : this.users.requireCanAssignTo(user, requested);
	}

	/** Only one open lead may have an email. Checked here for a friendly message; the database enforces it too. */
	private void requireEmailFree(String email, UUID ownLeadId) {
		if (email == null) {
			return;
		}
		boolean taken = this.leads.findByEmail(email)
			.stream()
			.filter((other) -> !other.getId().equals(ownLeadId))
			.anyMatch((other) -> other.getStatus() == Lead.Status.NEW || other.getStatus() == Lead.Status.CONTACTED
					|| other.getStatus() == Lead.Status.QUALIFIED);
		if (taken) {
			throw new ApiException(ErrorCode.DUPLICATE, "A lead with this email already exists.");
		}
	}

	private boolean canSee(CurrentUser user, Lead lead) {
		return lead.getOwnerId() == null
				|| this.users.visibleOwnerIds(user).map((ids) -> ids.contains(lead.getOwnerId())).orElse(true);
	}

	/** Leads the user may see: those they own or their team owns, and the unassigned queue. */
	private Specification<Lead> visible(CurrentUser user) {
		Set<UUID> ids = this.users.visibleOwnerIds(user).orElse(null);
		return (root, q, cb) -> (ids == null) ? cb.conjunction()
				: cb.or(root.get("ownerId").in(ids), cb.isNull(root.get("ownerId")));
	}

	private static LeadFilter filterOf(LeadView view) {
		return new LeadFilter(null, null, null, null, false, view, null);
	}

	private static Specification<Lead> statusIn(Lead.Status... statuses) {
		return (root, q, cb) -> root.get("status").in((Object[]) statuses);
	}

	private static Specification<Lead> createdBetween(Instant from, Instant to) {
		return (root, q, cb) -> {
			List<Predicate> all = new ArrayList<>();
			if (from != null) {
				all.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (to != null) {
				all.add(cb.lessThan(root.get("createdAt"), to));
			}
			return cb.and(all.toArray(Predicate[]::new));
		};
	}

	private Specification<Lead> matching(LeadFilter f) {
		return (root, q, cb) -> {
			List<Predicate> all = new ArrayList<>();
			if (f.source() != null) {
				all.add(cb.equal(root.get("source"), f.source()));
			}
			if (f.status() != null) {
				all.add(cb.equal(root.get("status"), f.status()));
			}
			if (f.unassigned()) {
				all.add(cb.isNull(root.get("ownerId")));
			}
			else if (f.ownerId() != null) {
				all.add(cb.equal(root.get("ownerId"), f.ownerId()));
			}
			if (f.view() == LeadView.NEW_THIS_MONTH) {
				all.add(cb.greaterThanOrEqualTo(root.get("createdAt"), monthStart(0)));
			}
			else if (f.view() == LeadView.QUALIFIED) {
				all.add(cb.equal(root.get("status"), Lead.Status.QUALIFIED));
			}
			else if (f.view() == LeadView.UNASSIGNED) {
				// Only leads that are still being worked: a converted lead is not waiting for an owner.
				all.add(cb.isNull(root.get("ownerId")));
				all.add(root.get("status").in(Lead.Status.NEW, Lead.Status.CONTACTED, Lead.Status.QUALIFIED));
			}
			if (f.query() != null && !f.query().isBlank()) {
				String like = Search.pattern(f.query());
				all.add(cb.or(
						cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), like),
						cb.like(cb.lower(root.get("email")), like), cb.like(cb.lower(root.get("companyName")), like)));
			}
			return cb.and(all.toArray(Predicate[]::new));
		};
	}

	/** The first moment of this month (offset 0) or an earlier or later month, in UTC. */
	private static Instant monthStart(int offsetMonths) {
		return YearMonth.now(ZoneOffset.UTC).plusMonths(offsetMonths).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
	}

	/** Percent change from {@code before} to {@code now}, to one decimal. Null when there is nothing to compare with. */
	private static Double percentChange(long before, long now) {
		if (before <= 0) {
			return null;
		}
		return Math.round((now - before) * 1000.0 / before) / 10.0;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	private static String blankToEmpty(String value) {
		return (value == null) ? "" : value.strip();
	}

}
