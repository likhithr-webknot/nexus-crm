package com.nexus.crm.mail;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.modules.ModuleRecord;
import com.nexus.crm.shared.CurrentUser;

@RestController
@RequestMapping("/api/v1/mail")
@RequiredArgsConstructor
class MailController {
	private final MailService mail;
	private final MailBannerService banner;
	@Value("${nexus.auth.frontend-url}") private String frontendUrl;

	@GetMapping("/status")
	MailService.Status status(@AuthenticationPrincipal CurrentUser user) { return mail.status(user.id()); }

	/** {@code feature} is mail (send), calendar (push follow-ups) or inbox (read mail from contacts). */
	@PostMapping("/connect")
	Map<String, String> connect(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "mail") String feature) {
		return Map.of("url", mail.connectionUrl(user, feature));
	}

	@GetMapping("/callback")
	ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state) {
		String feature = mail.callback(code, state);
		String landing = "mail".equals(feature) ? "/communications?mail=connected" : "/settings?section=" + ("calendar".equals(feature) ? "calendar" : "mail") + "&google=" + feature;
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(frontendUrl + landing)).build();
	}

	@PostMapping("/send/{id}")
	ModuleRecord send(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) { return mail.send(user, id); }

	@PostMapping("/mark-unsent/{id}")
	ModuleRecord markUnsent(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) { return mail.markUnsent(user, id); }

	@GetMapping("/banner")
	MailBannerService.Banner banner(@AuthenticationPrincipal CurrentUser user) { return banner.status(user.id()); }

	/** kind is "banner" (a finished image, the default) or "logo" (laid out with each sender's name). */
	record BannerInput(String imageBase64, String kind, String companyName) { }
	@PostMapping("/banner")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void uploadBanner(@AuthenticationPrincipal CurrentUser user, @org.springframework.web.bind.annotation.RequestBody BannerInput input) {
		banner.upload(user.id(), input.imageBase64(), input.kind(), input.companyName());
	}

	@DeleteMapping("/banner")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeBanner(@AuthenticationPrincipal CurrentUser user) { banner.remove(user.id()); }
}
