package com.nexus.crm.notifications;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A message in one user's bell menu. Only that user can see it. */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	@Id
	private UUID id;

	@Column(name = "recipient_id", nullable = false, updatable = false)
	private UUID recipientId;

	/** For example "LEAD_ASSIGNED" or "FOLLOW_UP_DUE". */
	@Column(nullable = false, updatable = false)
	private String type;

	@Column(nullable = false, updatable = false)
	private String title;

	/** A path inside the app, for example "/deals/123". Never a full URL. */
	@Column(updatable = false)
	private String link;

	@Column(name = "read_at")
	private Instant readAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Notification(UUID recipientId, String type, String title, String link) {
		this.id = UUID.randomUUID();
		this.recipientId = recipientId;
		this.type = type;
		this.title = title;
		this.link = link;
		this.createdAt = Instant.now();
	}

	public boolean isRead() {
		return this.readAt != null;
	}

	public void markRead(Instant at) {
		if (this.readAt == null) {
			this.readAt = at;
		}
	}

}
