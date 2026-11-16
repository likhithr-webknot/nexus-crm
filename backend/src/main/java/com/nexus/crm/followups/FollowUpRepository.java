package com.nexus.crm.followups;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID>, JpaSpecificationExecutor<FollowUp> {

	List<FollowUp> findByAssigneeIdAndStatusOrderByDueAtAsc(UUID assigneeId, FollowUp.Status status);

	/** Open follow-ups that are due and have not been reminded yet. For the reminder job. */
	List<FollowUp> findByStatusAndRemindedAtIsNullAndDueAtLessThanEqual(FollowUp.Status status, Instant now);

}
