package com.nexus.crm.notifications;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable page);

	long countByRecipientIdAndReadAtIsNull(UUID recipientId);

	/** Marks some of a user's unread notifications as read. Other people's notifications are never touched. */
	@Modifying
	@Transactional
	@Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null and n.id in :ids")
	int markRead(@Param("recipientId") UUID recipientId, @Param("ids") Collection<UUID> ids, @Param("now") Instant now);

	@Modifying
	@Transactional
	@Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
	int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);

	@Modifying
	@Transactional
	@Query("delete from Notification n where n.createdAt < :before")
	int deleteOlderThan(@Param("before") Instant before);

}
