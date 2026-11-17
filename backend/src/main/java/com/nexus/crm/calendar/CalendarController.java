package com.nexus.crm.calendar;

import java.time.Duration;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.limits.RateLimit;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
class CalendarController {

	/** A link is 43 URL-safe characters. Anything else is refused before it reaches the database. */
	private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

	/** Calendar apps fetch every few minutes to hours. This stops guessing and runaway clients, not normal use. */
	private static final RateLimit PER_ADDRESS = new RateLimit(120, Duration.ofMinutes(1));

	private final CalendarFeedService feeds;

	record FeedStatus(boolean enabled) {
	}

	record FeedLink(String url, String webcalUrl) {
	}

	@GetMapping("/feed")
	FeedStatus status(@AuthenticationPrincipal CurrentUser user) {
		return new FeedStatus(this.feeds.hasFeed(user.id()));
	}

	@PostMapping("/feed")
	@ResponseStatus(HttpStatus.CREATED)
	FeedLink create(@AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		String token = this.feeds.createOrReplace(user);
		String url = ServletUriComponentsBuilder.fromContextPath(request).path("/api/v1/calendar/ics/" + token + ".ics").build().toUriString();
		return new FeedLink(url, url.replaceFirst("^https?://", "webcal://"));
	}

	@DeleteMapping("/feed")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revoke(@AuthenticationPrincipal CurrentUser user) {
		this.feeds.revoke(user);
	}

	/** The public feed. The link itself is the credential, so there is no sign-in, and a wrong link gets a plain 404. */
	@GetMapping("/ics/{token}.ics")
	ResponseEntity<String> feed(@PathVariable String token, HttpServletRequest request) {
		PER_ADDRESS.require(request.getRemoteAddr());
		if (!TOKEN.matcher(token).matches()) {
			return ResponseEntity.notFound().build();
		}
		return this.feeds.render(token)
			.map((body) -> ResponseEntity.ok()
				.contentType(new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8))
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"nexus-follow-ups.ics\"")
				.header("X-Robots-Tag", "noindex")
				.header("Referrer-Policy", "no-referrer")
				.body(body))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
