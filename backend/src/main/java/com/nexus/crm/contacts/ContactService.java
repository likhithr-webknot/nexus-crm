package com.nexus.crm.contacts;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.contacts.dto.CompanyInput;
import com.nexus.crm.contacts.dto.ContactInput;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Emails;
import com.nexus.crm.shared.Search;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/** Contacts and the companies they work for. */
@Service
@RequiredArgsConstructor
public class ContactService {

	private final ContactRepository contacts;

	private final CompanyRepository companies;

	private final UserService users;

	private final AuditService audit;

	private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

	// ---- contacts ----

	@Transactional(readOnly = true)
	public Page<Contact> list(CurrentUser user, String query, int page, int size) {
		Specification<Contact> spec = ownedByVisible(user, Contact.class).and(matching(query));
		return this.contacts.findAll(spec,
				PageRequest.of(Math.max(0, page - 1), size, Sort.by("firstName", "lastName", "id")));
	}

	@Transactional(readOnly = true)
	public Contact get(CurrentUser user, UUID id) {
		return this.contacts.findById(id)
			.filter((contact) -> canSee(user, contact.getOwnerId()))
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Contact not found."));
	}

	@Transactional
	public Contact create(CurrentUser user, ContactInput in) {
		UUID ownerId = (in.ownerId() == null) ? user.id() : this.users.requireCanAssignTo(user, in.ownerId());
		Contact contact = new Contact(in.firstName().strip(), in.lastName().strip(), ownerId);
		contact.setEmail(in.email());
		contact.setPhone(blankToNull(in.phone()));
		contact.setJobTitle(blankToNull(in.jobTitle()));
		contact.setCompanyId(resolveCompany(user, in, ownerId));
		requireUniqueEmail(contact);
		this.contacts.save(contact);
		this.audit.record(user.id(), "Contact", contact.getId(), AuditLog.Action.CREATE, null);
		return contact;
	}

	@Transactional
	public Contact update(CurrentUser user, UUID id, Integer version, ContactInput in) {
		Contact contact = get(user, id);
		contact.checkVersion(version);
		AuditService.Changes changes = AuditService.changes()
			.field("firstName", contact.getFirstName(), in.firstName() == null ? contact.getFirstName() : in.firstName().strip())
			.field("lastName", contact.getLastName(), in.lastName() == null ? contact.getLastName() : in.lastName().strip())
			.field("email", contact.getEmail(), in.email() == null ? contact.getEmail() : Emails.normalize(in.email()))
			.field("ownerId", contact.getOwnerId(), in.ownerId() == null ? contact.getOwnerId() : in.ownerId());
		if (in.firstName() != null) {
			contact.setFirstName(in.firstName().strip());
		}
		if (in.lastName() != null) {
			contact.setLastName(in.lastName().strip());
		}
		if (in.email() != null) {
			contact.setEmail(in.email());
			requireUniqueEmail(contact);
		}
		if (in.phone() != null) {
			contact.setPhone(blankToNull(in.phone()));
		}
		if (in.jobTitle() != null) {
			contact.setJobTitle(blankToNull(in.jobTitle()));
		}
		if (in.ownerId() != null) {
			contact.setOwnerId(this.users.requireCanAssignTo(user, in.ownerId()));
		}
		if (in.companyId() != null || in.companyName() != null) {
			contact.setCompanyId(resolveCompany(user, in, contact.getOwnerId()));
		}
		this.contacts.save(contact);
		this.audit.record(user.id(), "Contact", id, AuditLog.Action.UPDATE, changes);
		return contact;
	}

	@Transactional
	public void delete(CurrentUser user, UUID id) {
		Contact contact = get(user, id);
		contact.softDelete(java.time.Instant.now());
		this.audit.record(user.id(), "Contact", id, AuditLog.Action.DELETE, null);
	}

	// ---- companies ----

	@Transactional(readOnly = true)
	public Page<Company> listCompanies(CurrentUser user, String query, int page, int size) {
		Specification<Company> spec = ownedByVisible(user, Company.class).and((root, q, cb) -> {
			if (query == null || query.isBlank()) {
				return cb.conjunction();
			}
			String like = Search.pattern(query);
			return cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("domain")), like));
		});
		return this.companies.findAll(spec, PageRequest.of(Math.max(0, page - 1), size, Sort.by("name", "id")));
	}

	@Transactional(readOnly = true)
	public Company getCompany(CurrentUser user, UUID id) {
		return this.companies.findById(id)
			.filter((company) -> canSee(user, company.getOwnerId()))
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Company not found."));
	}

	@Transactional
	public Company createCompany(CurrentUser user, CompanyInput in) {
		UUID ownerId = (in.ownerId() == null) ? user.id() : this.users.requireCanAssignTo(user, in.ownerId());
		Company company = new Company(in.name().strip(), ownerId);
		company.setDomain(in.domain());
		company.setIndustry(blankToNull(in.industry()));
		requireUniqueDomain(company);
		this.companies.save(company);
		this.audit.record(user.id(), "Company", company.getId(), AuditLog.Action.CREATE, null);
		return company;
	}

	@Transactional
	public Company updateCompany(CurrentUser user, UUID id, Integer version, CompanyInput in) {
		Company company = getCompany(user, id);
		company.checkVersion(version);
		AuditService.Changes changes = AuditService.changes()
			.field("name", company.getName(), in.name() == null ? company.getName() : in.name().strip())
			.field("domain", company.getDomain(), in.domain() == null ? company.getDomain() : Emails.normalize(in.domain()));
		if (in.name() != null) {
			company.setName(in.name().strip());
		}
		if (in.domain() != null) {
			company.setDomain(in.domain());
			requireUniqueDomain(company);
		}
		if (in.industry() != null) {
			company.setIndustry(blankToNull(in.industry()));
		}
		if (in.ownerId() != null) {
			company.setOwnerId(this.users.requireCanAssignTo(user, in.ownerId()));
		}
		this.companies.save(company);
		this.audit.record(user.id(), "Company", id, AuditLog.Action.UPDATE, changes);
		return company;
	}

	/** Deletes a company. One that still has contacts or deals is refused, so nothing is left pointing at it. */
	@Transactional
	public void deleteCompany(CurrentUser user, UUID id) {
		Company company = getCompany(user, id);
		long contactCount = this.contacts.countByCompanyId(id);
		long dealCount = this.jdbc.sql("select count(*) from deal where company_id = :id and deleted_at is null")
			.param("id", id)
			.query(Long.class)
			.single();
		if (contactCount > 0 || dealCount > 0) {
			throw new ApiException(ErrorCode.INVALID_STATE,
					"This company still has " + contactCount + " contact(s) and " + dealCount + " deal(s). Remove or move them first.");
		}
		company.softDelete(java.time.Instant.now());
		this.audit.record(user.id(), "Company", id, AuditLog.Action.DELETE, null);
	}

	/** Finds the owner's company with this name, or creates it. */
	@Transactional
	public Company findOrCreateCompany(CurrentUser user, String name, UUID ownerId) {
		return this.companies.findByNameIgnoreCaseAndOwnerId(name.strip(), ownerId)
			.stream()
			.findFirst()
			.orElseGet(() -> {
				Company company = this.companies.save(new Company(name.strip(), ownerId));
				this.audit.record(user.id(), "Company", company.getId(), AuditLog.Action.CREATE, null);
				return company;
			});
	}

	/** Company names by ID, for lists that show "Apex Dynamics" instead of an ID. */
	@Transactional(readOnly = true)
	public Map<UUID, String> companyNames(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return this.companies.findByIdIn(ids).stream().collect(Collectors.toMap(Company::getId, Company::getName));
	}

	/** The contact with this email, or a new one. */
	@Transactional
	public Contact findOrCreateContact(CurrentUser user, ContactInput in, UUID ownerId) {
		String email = Emails.normalize(in.email());
		if (email != null) {
			var existing = this.contacts.findByEmail(email);
			if (existing.isPresent()) {
				return get(user, existing.get().getId());
			}
		}
		return create(user, new ContactInput(in.firstName(), in.lastName(), in.email(), in.phone(), in.jobTitle(),
				in.companyId(), null, ownerId));
	}

	@Transactional(readOnly = true)
	public Map<UUID, String> contactNames(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return this.contacts.findAllById(ids)
			.stream()
			.collect(Collectors.toMap(Contact::getId, (c) -> c.getFirstName() + " " + c.getLastName()));
	}

	/** Contact names with their company, for timelines and follow-up lists. */
	@Transactional(readOnly = true)
	public Map<UUID, com.nexus.crm.shared.Label> contactSummaries(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		List<Contact> found = this.contacts.findAllById(ids);
		Map<UUID, String> companyNames = companyNames(found.stream().map(Contact::getCompanyId)
			.filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
		return found.stream().collect(Collectors.toMap(Contact::getId, (c) -> new com.nexus.crm.shared.Label(
				c.getFirstName() + " " + c.getLastName(), companyNames.get(c.getCompanyId()))));
	}

	/** Checks that the user can see this contact. Used before linking it to a deal. */
	@Transactional(readOnly = true)
	public Contact requireVisibleContact(CurrentUser user, UUID id) {
		return get(user, id);
	}

	// ---- helpers ----

	/** The company a contact belongs to: an existing one by ID, or one found or made from a name. */
	private UUID resolveCompany(CurrentUser user, ContactInput in, UUID ownerId) {
		if (in.companyId() != null) {
			return getCompany(user, in.companyId()).getId();
		}
		if (in.companyName() != null && !in.companyName().isBlank()) {
			return findOrCreateCompany(user, in.companyName(), ownerId).getId();
		}
		return null;
	}

	private void requireUniqueEmail(Contact contact) {
		if (contact.getEmail() == null) {
			return;
		}
		this.contacts.findByEmail(contact.getEmail())
			.filter((other) -> !other.getId().equals(contact.getId()))
			.ifPresent((other) -> {
				throw new ApiException(ErrorCode.DUPLICATE, "A contact with this email already exists.");
			});
	}

	private void requireUniqueDomain(Company company) {
		if (company.getDomain() == null) {
			return;
		}
		this.companies.findByDomain(company.getDomain())
			.filter((other) -> !other.getId().equals(company.getId()))
			.ifPresent((other) -> {
				throw new ApiException(ErrorCode.DUPLICATE, "A company with this domain already exists.");
			});
	}

	private boolean canSee(CurrentUser user, UUID ownerId) {
		return this.users.visibleOwnerIds(user).map((ids) -> ids.contains(ownerId)).orElse(true);
	}

	/** Limits a query to the records the user may see. */
	private <T> Specification<T> ownedByVisible(CurrentUser user, Class<T> type) {
		Set<UUID> ids = this.users.visibleOwnerIds(user).orElse(null);
		return (root, query, cb) -> (ids == null) ? cb.conjunction() : root.get("ownerId").in(ids);
	}

	private Specification<Contact> matching(String query) {
		return (root, q, cb) -> {
			if (query == null || query.isBlank()) {
				return cb.conjunction();
			}
			String like = Search.pattern(query);
			var companyIds = q.subquery(UUID.class);
			var company = companyIds.from(Company.class);
			companyIds.select(company.get("id")).where(cb.like(cb.lower(company.get("name")), like));
			Predicate name = cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), like);
			return cb.or(name, cb.like(cb.lower(root.get("email")), like),
					cb.like(cb.lower(root.get("jobTitle")), like), root.get("companyId").in(companyIds));
		};
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
