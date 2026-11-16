package com.nexus.crm.contacts;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
import com.nexus.crm.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class ContactController {

	record Ref(UUID id, String name) {
	}

	record ContactResponse(UUID id, String firstName, String lastName, String name, String jobTitle, String email,
			String phone, Ref company, Ref owner, Instant createdAt, Instant updatedAt, Integer version) {
	}

	record CompanyResponse(UUID id, String name, String domain, String industry, Ref owner, Instant createdAt,
			Instant updatedAt, Integer version) {
	}

	record CreateContactRequest(@NotBlank @Size(max = 120) String firstName, @NotBlank @Size(max = 120) String lastName,
			@Email @Size(max = 254) String email, @Size(max = 40) String phone, @Size(max = 120) String jobTitle,
			UUID companyId, @Size(max = 160) String companyName, UUID ownerId) {
	}

	/** Every field is optional. A field that is left out is not changed. */
	record UpdateContactRequest(Integer version, @Size(max = 120) String firstName, @Size(max = 120) String lastName,
			@Email @Size(max = 254) String email, @Size(max = 40) String phone, @Size(max = 120) String jobTitle,
			UUID companyId, @Size(max = 160) String companyName, UUID ownerId) {
	}

	record CreateCompanyRequest(@NotBlank @Size(max = 160) String name, @Size(max = 160) String domain,
			@Size(max = 120) String industry, UUID ownerId) {
	}

	record UpdateCompanyRequest(Integer version, @Size(max = 160) String name, @Size(max = 160) String domain,
			@Size(max = 120) String industry, UUID ownerId) {
	}

	private final ContactService contacts;

	private final UserService users;

	@GetMapping("/contacts")
	PageResponse<ContactResponse> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int size) {
		Page<Contact> found = this.contacts.list(user, q, page, PageResponse.clampSize(size));
		return PageResponse.of(found, contactMapper(found.getContent()));
	}

	@GetMapping("/contacts/{id}")
	ContactResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		Contact contact = this.contacts.get(user, id);
		return contactMapper(List.of(contact)).apply(contact);
	}

	@PostMapping("/contacts")
	@ResponseStatus(HttpStatus.CREATED)
	ContactResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateContactRequest r) {
		Contact contact = this.contacts.create(user, new ContactService.ContactInput(r.firstName(), r.lastName(),
				r.email(), r.phone(), r.jobTitle(), r.companyId(), r.companyName(), r.ownerId()));
		return contactMapper(List.of(contact)).apply(contact);
	}

	@PatchMapping("/contacts/{id}")
	ContactResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateContactRequest r) {
		Contact contact = this.contacts.update(user, id, r.version(), new ContactService.ContactInput(r.firstName(),
				r.lastName(), r.email(), r.phone(), r.jobTitle(), r.companyId(), r.companyName(), r.ownerId()));
		return contactMapper(List.of(contact)).apply(contact);
	}

	@DeleteMapping("/contacts/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		this.contacts.delete(user, id);
	}

	@GetMapping("/companies")
	PageResponse<CompanyResponse> companies(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int size) {
		Page<Company> found = this.contacts.listCompanies(user, q, page, PageResponse.clampSize(size));
		return PageResponse.of(found, companyMapper(found.getContent()));
	}

	@GetMapping("/companies/{id}")
	CompanyResponse company(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		Company company = this.contacts.getCompany(user, id);
		return companyMapper(List.of(company)).apply(company);
	}

	@PostMapping("/companies")
	@ResponseStatus(HttpStatus.CREATED)
	CompanyResponse createCompany(@AuthenticationPrincipal CurrentUser user,
			@Valid @RequestBody CreateCompanyRequest r) {
		Company company = this.contacts.createCompany(user,
				new ContactService.CompanyInput(r.name(), r.domain(), r.industry(), r.ownerId()));
		return companyMapper(List.of(company)).apply(company);
	}

	@PatchMapping("/companies/{id}")
	CompanyResponse updateCompany(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateCompanyRequest r) {
		Company company = this.contacts.updateCompany(user, id, r.version(),
				new ContactService.CompanyInput(r.name(), r.domain(), r.industry(), r.ownerId()));
		return companyMapper(List.of(company)).apply(company);
	}

	/** Looks up company and owner names once for the whole list, then builds each response. */
	private java.util.function.Function<Contact, ContactResponse> contactMapper(Collection<Contact> list) {
		Map<UUID, String> companyNames = this.contacts
			.companyNames(list.stream().map(Contact::getCompanyId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
		Map<UUID, String> ownerNames = this.users.displayNames(list.stream().map(Contact::getOwnerId).collect(Collectors.toSet()));
		return (c) -> new ContactResponse(c.getId(), c.getFirstName(), c.getLastName(),
				c.getFirstName() + " " + c.getLastName(), c.getJobTitle(), c.getEmail(), c.getPhone(),
				(c.getCompanyId() == null) ? null : new Ref(c.getCompanyId(), companyNames.get(c.getCompanyId())),
				new Ref(c.getOwnerId(), ownerNames.get(c.getOwnerId())), c.getCreatedAt(), c.getUpdatedAt(),
				c.getVersion());
	}

	private java.util.function.Function<Company, CompanyResponse> companyMapper(Collection<Company> list) {
		Map<UUID, String> ownerNames = this.users.displayNames(list.stream().map(Company::getOwnerId).collect(Collectors.toSet()));
		return (c) -> new CompanyResponse(c.getId(), c.getName(), c.getDomain(), c.getIndustry(),
				new Ref(c.getOwnerId(), ownerNames.get(c.getOwnerId())), c.getCreatedAt(), c.getUpdatedAt(),
				c.getVersion());
	}

}
