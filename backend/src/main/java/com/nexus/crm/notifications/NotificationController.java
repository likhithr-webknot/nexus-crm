package com.nexus.crm.notifications;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.CurrentUser;

/** A user sees only their own notifications. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
class NotificationController {

	record NotificationResponse(UUID id, String type, String title, String link, boolean read, Instant createdAt) {
	}

	/** The IDs to mark as read. Leave it out or empty to mark everything. */
	record ReadRequest(List<UUID> ids) {
	}

	private final NotificationService notifications;

	@GetMapping
	List<NotificationResponse> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(defaultValue = "20") int size) {
		return this.notifications.list(user.id(), Math.max(1, Math.min(size, 50)))
			.stream()
			.map((n) -> new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getLink(), n.isRead(), n.getCreatedAt()))
			.toList();
	}

	@GetMapping("/unread-count")
	Map<String, Long> unreadCount(@AuthenticationPrincipal CurrentUser user) {
		return Map.of("count", this.notifications.unreadCount(user.id()));
	}

	@PostMapping("/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@AuthenticationPrincipal CurrentUser user, @RequestBody(required = false) ReadRequest request) {
		this.notifications.markRead(user.id(), request == null ? null : request.ids());
	}

}
