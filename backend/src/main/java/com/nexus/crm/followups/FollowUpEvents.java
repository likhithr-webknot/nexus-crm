package com.nexus.crm.followups;

import java.util.UUID;

/** Things that happen to follow-ups. The notifications module listens for them. */
public final class FollowUpEvents {

	private FollowUpEvents() {
	}

	/** A follow-up was given to someone other than the person who made it. */
	public record Assigned(UUID followUpId, String title, UUID assigneeId, UUID actorId) {
	}

	/** A follow-up reached its due time. */
	public record Due(UUID followUpId, String title, UUID assigneeId) {
	}

}
