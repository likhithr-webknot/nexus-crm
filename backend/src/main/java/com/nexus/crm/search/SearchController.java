package com.nexus.crm.search;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.contacts.ContactService;
import com.nexus.crm.deals.DealService;
import com.nexus.crm.leads.LeadService;
import com.nexus.crm.shared.CurrentUser;

/** The search box in the top bar. */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
class SearchController {

	private static final int PER_TYPE = 5;

	private static final int MIN_QUERY_LENGTH = 2;

	/** {@code href} is a path in the frontend. */
	record Hit(String type, UUID id, String title, String subtitle, String href) {
	}

	private final LeadService leads;

	private final ContactService contacts;

	private final DealService deals;

	@GetMapping
	List<Hit> search(@AuthenticationPrincipal CurrentUser user, @RequestParam String q) {
		String query = q.strip();
		if (query.length() < MIN_QUERY_LENGTH) {
			return List.of();
		}
		List<Hit> hits = new ArrayList<>();
		this.leads.list(user, new LeadService.LeadFilter(query, null, null, null, false, null), 1, PER_TYPE, null, false)
			.forEach((l) -> hits.add(new Hit("Lead", l.getId(), LeadService.fullName(l),
					String.join(" · ", nonBlank(l.getCompanyName(), l.getEmail())), "/leads")));
		this.contacts.list(user, query, 1, PER_TYPE)
			.forEach((c) -> hits.add(new Hit("Contact", c.getId(), c.getFirstName() + " " + c.getLastName(),
					String.join(" · ", nonBlank(c.getJobTitle(), c.getEmail())), "/contacts")));
		this.contacts.listCompanies(user, query, 1, PER_TYPE)
			.forEach((c) -> hits.add(new Hit("Company", c.getId(), c.getName(), c.getDomain() == null ? "" : c.getDomain(), "/contacts")));
		this.deals.list(user, new DealService.DealFilter(query, null, null, null, null, null), 1, PER_TYPE)
			.forEach((d) -> hits.add(new Hit("Deal", d.getId(), d.getName(), d.getStageCode(), "/pipeline")));
		return hits;
	}

	private static List<String> nonBlank(String... parts) {
		return java.util.Arrays.stream(parts).filter((p) -> p != null && !p.isBlank()).toList();
	}

}
