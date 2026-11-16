package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.nexus.crm.identity.team.TeamService;
import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/**
 * Lists are read with a fixed number of queries, however many rows they have.
 *
 * <p>
 * This is the safety net for the N+1 problem. Hibernate statistics are switched on for tests
 * ({@code application-test.yml}), so a test can count the SQL statements a call runs.
 */
class QueryShapeTests extends IntegrationTest {

	@Autowired
	private TeamService teams;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void listingTeamsRunsTheSameFewQueriesForOneTeamOrTwenty() {
		var statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		AppUser manager = invite(uniqueEmail(), Role.MANAGER);
		for (int i = 0; i < 20; i++) {
			this.teams.create("Team " + UUID.randomUUID(), manager.getId());
		}

		long before = statistics.getPrepareStatementCount();
		var list = this.teams.list();
		long queries = statistics.getPrepareStatementCount() - before;

		assertThat(list).hasSizeGreaterThanOrEqualTo(20);
		// One for the teams, one for the managers, one for the member counts. Not one or two more per team.
		assertThat(queries).isLessThanOrEqualTo(3);
	}

	@Test
	void entitiesRefereToEachOtherByIdNotByAssociation() {
		// Records are linked by UUID columns, so a module never reaches into another module's entities and
		// nothing is loaded lazily behind the code's back. Keep it so, or this test needs a decision.
		var withAssociations = this.entityManagerFactory.getMetamodel()
			.getEntities()
			.stream()
			.flatMap((entity) -> entity.getAttributes().stream().filter(Attribute::isAssociation).map((a) -> entity.getName() + "." + a.getName()))
			.toList();

		assertThat(withAssociations).isEmpty();
	}

}
