package com.nexus.crm.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
class AiController {

	private final AiService ai;

	private final JdbcClient jdbc;

	private final AuditService audit;

	record Prompt(String text) {
	}

	record Apply(String field) {
	}

	@GetMapping("/status")
	AiService.Status status() {
		return ai.status();
	}

	@PostMapping("/assist")
	AiService.Answer assist(@AuthenticationPrincipal CurrentUser user, @RequestBody Prompt input) {
		return ai.assist(user, input.text());
	}

	@PostMapping(value = "/assist/stream", produces = "text/event-stream")
	SseEmitter stream(@AuthenticationPrincipal CurrentUser user, @RequestBody Prompt input) {
		return ai.streamAssist(user, input.text());
	}

	@PostMapping("/draft-email")
	Map<String, String> draftEmail(@AuthenticationPrincipal CurrentUser user, @RequestBody Prompt input) {
		return Map.of("body", ai.draftEmail(user, input.text()));
	}

	@PostMapping("/workflow")
	AiService.Workflow workflow(@AuthenticationPrincipal CurrentUser user, @RequestBody Prompt input) {
		return ai.workflow(user, input.text());
	}

	@PostMapping("/record-draft/{kind}")
	AiService.RecordDraft recordDraft(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind,
			@RequestBody Prompt input) {
		return ai.recordDraft(user, kind, input.text());
	}

	@GetMapping("/research/{kind}/{id}")
	ResponseEntity<AiService.Research> research(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind,
			@PathVariable UUID id) {
		AiService.Research saved = ai.savedResearch(user, kind, id);
		return saved == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(saved);
	}

	@PostMapping("/research/{kind}/{id}")
	AiService.Research refreshResearch(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind,
			@PathVariable UUID id) {
		return ai.research(user, kind, id);
	}

	/**
	 * Saves one researched value on the record. The research comes from web pages anyone
	 * can write, so a value is only accepted when it belongs to the company's own site: a
	 * logo hosted elsewhere would be loaded in every user's browser (and show their
	 * address to that host), and an email on another domain could send our mail to a
	 * stranger.
	 */
	@PostMapping("/research/{kind}/{id}/apply")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void apply(@AuthenticationPrincipal CurrentUser user, @PathVariable String kind, @PathVariable UUID id,
			@RequestBody Apply input) {
		AiService.Research research = ai.savedResearch(user, kind, id);
		if (research == null || research.sources().isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_STATE, "Research this record first.");
		}
		String site = companySite(kind, id);
		int changed;
		if ("company".equals(kind) && "website".equals(input.field()) && research.website() != null) {
			boolean ownSite = site != null ? belongsTo(research.website(), site)
					: citedAmong(research.website(), research.sources());
			if (!ownSite) {
				throw refused("The researched website does not match this company's domain, so it was not saved.");
			}
			changed = jdbc.sql(
					"update company set website_url=:value,updated_at=now(),updated_by=:user,version=version+1 where id=:id and deleted_at is null")
				.param("value", research.website())
				.param("user", user.id())
				.param("id", id)
				.update();
		}
		else if ("company".equals(kind) && "logo".equals(input.field()) && research.logoUrl() != null) {
			if (site == null || !belongsTo(research.logoUrl(), site)) {
				throw refused("The logo is hosted on another site than this company's, so it was not saved.");
			}
			changed = jdbc.sql(
					"update company set logo_url=:value,updated_at=now(),updated_by=:user,version=version+1 where id=:id and deleted_at is null")
				.param("value", research.logoUrl())
				.param("user", user.id())
				.param("id", id)
				.update();
		}
		else if ("contact".equals(kind) && "email".equals(input.field()) && research.email() != null) {
			String domain = research.email().substring(research.email().indexOf('@') + 1);
			if (site == null || !belongsTo("https://" + domain, site)) {
				throw refused("The email is not on this company's domain, so it was not saved.");
			}
			changed = jdbc
				.sql("update contact set email=:value,updated_at=now(),updated_by=:user,version=version+1 "
						+ "where id=:id and deleted_at is null and email is null")
				.param("value", research.email())
				.param("user", user.id())
				.param("id", id)
				.update();
		}
		else {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a researched field.");
		}
		if (changed == 0) {
			throw new ApiException(ErrorCode.INVALID_STATE, "The record changed or already has an email.");
		}
		audit.record(user.id(), "company".equals(kind) ? "Company" : "Contact", id, AuditLog.Action.UPDATE, null);
	}

	/**
	 * The company's own domain, from what we already hold (its domain, else its saved
	 * website). Null when we hold neither.
	 */
	private String companySite(String kind, UUID id) {
		String sql = "company".equals(kind) ? "select domain, website_url from company where id = :id"
				: "select c.domain, c.website_url from contact t join company c on c.id = t.company_id where t.id = :id";
		return jdbc.sql(sql).param("id", id).query((rs, n) -> {
			String domain = rs.getString("domain");
			return (domain != null && !domain.isBlank()) ? hostOf("https://" + domain.strip())
					: hostOf(rs.getString("website_url"));
		}).optional().orElse(null);
	}

	/**
	 * True when the address is on the site, or on a subdomain of it ("cdn.acme.com"
	 * belongs to "acme.com").
	 */
	static boolean belongsTo(String url, String site) {
		String host = hostOf(url);
		return host != null && (host.equals(site) || host.endsWith("." + site));
	}

	private static boolean citedAmong(String url, List<String> sources) {
		String host = hostOf(url);
		return host != null && sources.stream().anyMatch((source) -> host.equals(hostOf(source)));
	}

	/** The host in lower case without "www.", or null if there is none. */
	static String hostOf(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		try {
			String host = java.net.URI.create(url.strip()).getHost();
			if (host == null) {
				return null;
			}
			host = host.toLowerCase(java.util.Locale.ROOT);
			return host.startsWith("www.") ? host.substring(4) : host;
		}
		catch (IllegalArgumentException notAnAddress) {
			return null;
		}
	}

	private ApiException refused(String message) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, message);
	}

}
