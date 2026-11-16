package com.nexus.crm.notifications;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.deals.PipelineStage;
import com.nexus.crm.deals.event.DealReassigned;
import com.nexus.crm.deals.event.DealStageChanged;
import com.nexus.crm.followups.event.FollowUpAssigned;
import com.nexus.crm.followups.event.FollowUpDue;
import com.nexus.crm.identity.team.TeamService;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.leads.event.LeadAssigned;

/** The messages in a user's bell menu. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final Duration KEEP_FOR = Duration.ofDays(90);

	private final NotificationRepository notifications;

	private final UserService users;

	private final TeamService teams;

	@Transactional(readOnly = true)
	public List<Notification> list(UUID userId, int size) {
		return this.notifications.findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.ofSize(size));
	}

	@Transactional(readOnly = true)
	public long unreadCount(UUID userId) {
		return this.notifications.countByRecipientIdAndReadAtIsNull(userId);
	}

	/** Marks the given notifications as read, or all of the user's when no IDs are given. */
	@Transactional
	public void markRead(UUID userId, Collection<UUID> ids) {
		if (ids == null || ids.isEmpty()) {
			this.notifications.markAllRead(userId, Instant.now());
		}
		else {
			this.notifications.markRead(userId, ids, Instant.now());
		}
	}

	@Transactional
	public void send(UUID recipientId, String type, String title, String link) {
		this.notifications.save(new Notification(recipientId, type, title, link));
	}

	@EventListener
	void onLeadAssigned(LeadAssigned e) {
		if (e.newOwnerId() != null && !e.newOwnerId().equals(e.actorId())) {
			send(e.newOwnerId(), "LEAD_ASSIGNED", "A lead was assigned to you: " + e.name(), "/leads");
		}
	}

	@EventListener
	void onDealReassigned(DealReassigned e) {
		if (!e.newOwnerId().equals(e.actorId())) {
			send(e.newOwnerId(), "DEAL_ASSIGNED", "A deal was assigned to you: " + e.name(), "/pipeline");
		}
	}

	/** A won deal is good news for the team: the admins and the owner's manager hear about it. */
	@EventListener
	void onDealStageChanged(DealStageChanged e) {
		if (!PipelineStage.WON.equals(e.toStage())) {
			return;
		}
		for (UUID leader : this.teams.leadersOf(e.ownerId())) {
			if (!leader.equals(e.actorId())) {
				send(leader, "DEAL_WON", "Deal won: " + e.name(), "/pipeline");
			}
		}
	}

	@EventListener
	void onFollowUpAssigned(FollowUpAssigned e) {
		send(e.assigneeId(), "FOLLOW_UP_ASSIGNED", "A follow-up was assigned to you: " + e.title(), "/calendar");
	}

	@EventListener
	void onFollowUpDue(FollowUpDue e) {
		send(e.assigneeId(), "FOLLOW_UP_DUE", "Follow-up due: " + e.title(), "/calendar");
	}

	/** Deletes notifications older than 90 days, every night. */
	@Scheduled(cron = "0 15 3 * * *", zone = "UTC")
	void purgeOld() {
		int removed = this.notifications.deleteOlderThan(Instant.now().minus(KEEP_FOR));
		log.info("Old notifications deleted: {}", removed);
	}

}
