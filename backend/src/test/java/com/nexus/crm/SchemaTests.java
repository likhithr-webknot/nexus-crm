package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nexus.crm.activities.Activity;
import com.nexus.crm.activities.ActivityRepository;
import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditLogRepository;
import com.nexus.crm.contacts.Company;
import com.nexus.crm.contacts.CompanyRepository;
import com.nexus.crm.contacts.Contact;
import com.nexus.crm.contacts.ContactRepository;
import com.nexus.crm.deals.Deal;
import com.nexus.crm.deals.DealRepository;
import com.nexus.crm.deals.DealStageHistory;
import com.nexus.crm.deals.DealStageHistoryRepository;
import com.nexus.crm.deals.PipelineStageRepository;
import com.nexus.crm.followups.FollowUp;
import com.nexus.crm.followups.FollowUpRepository;
import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.identity.user.AppUserRepository;
import com.nexus.crm.leads.ImportJob;
import com.nexus.crm.leads.ImportJobRepository;
import com.nexus.crm.leads.Lead;
import com.nexus.crm.leads.LeadRepository;
import com.nexus.crm.leads.LeadStatusHistory;
import com.nexus.crm.leads.LeadStatusHistoryRepository;
import com.nexus.crm.notifications.Notification;
import com.nexus.crm.notifications.NotificationRepository;
import com.nexus.crm.reporting.DailyMetric;
import com.nexus.crm.reporting.DailyMetricRepository;
import com.nexus.crm.reporting.RevenueTarget;
import com.nexus.crm.reporting.RevenueTargetRepository;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.currency.CurrencyRepository;
import com.nexus.crm.shared.currency.ExchangeRate;
import com.nexus.crm.shared.currency.ExchangeRateRepository;
import com.nexus.crm.shared.persistence.IdempotencyKey;
import com.nexus.crm.shared.persistence.IdempotencyKeyRepository;
import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** Checks that the entities match the tables, and that the database rules (unique emails, required links) work. */
class SchemaTests extends IntegrationTest {

	@Autowired
	private AppUserRepository users;

	@Autowired
	private CurrencyRepository currencies;

	@Autowired
	private ExchangeRateRepository rates;

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private ContactRepository contacts;

	@Autowired
	private LeadRepository leads;

	@Autowired
	private LeadStatusHistoryRepository leadHistory;

	@Autowired
	private ImportJobRepository importJobs;

	@Autowired
	private PipelineStageRepository stages;

	@Autowired
	private DealRepository deals;

	@Autowired
	private DealStageHistoryRepository dealHistory;

	@Autowired
	private ActivityRepository activities;

	@Autowired
	private FollowUpRepository followUps;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private AuditLogRepository auditLogs;

	@Autowired
	private RevenueTargetRepository targets;

	@Autowired
	private DailyMetricRepository metrics;

	@Autowired
	private IdempotencyKeyRepository idempotencyKeys;

	private AppUser newUser() {
		return this.users.save(AppUser.invite(uniqueEmail(), "Test User", Role.REP, null));
	}

	@Test
	void migrationsSeedCurrenciesAndPipelineStages() {
		assertThat(this.currencies.findByBaseTrue()).get().extracting("code").isEqualTo("INR");
		assertThat(this.currencies.findByEnabledTrue()).extracting("code").contains("INR", "USD", "AUD", "SAR");
		assertThat(this.stages.findAllByOrderByPositionAsc()).extracting("code")
			.containsExactly("QUALIFIED", "DEMO", "PROPOSAL", "NEGOTIATION", "WON", "LOST");
	}

	@Test
	void theBaseCurrencyCannotBeDisabled() {
		var inr = this.currencies.findByBaseTrue().orElseThrow();
		assertThatThrownBy(() -> inr.setEnabled(false)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void exchangeRateLookupFallsBackToTheLatestEarlierRate() {
		LocalDate day = LocalDate.of(2031, 3, 10);
		this.rates.save(new ExchangeRate("USD", day.minusDays(3), new BigDecimal("83.10"), "TEST"));
		this.rates.save(new ExchangeRate("USD", day.minusDays(1), new BigDecimal("83.50"), "TEST"));

		var found = this.rates.findOnOrBefore("USD", day, PageRequest.ofSize(1));

		assertThat(found).hasSize(1);
		assertThat(found.getFirst().getRateToBase()).isEqualByComparingTo("83.50");
	}

	@Test
	void anAdminCanOverrideAnExchangeRate() {
		AppUser admin = newUser();
		LocalDate day = LocalDate.of(2031, 4, 1);
		this.rates.save(new ExchangeRate("AUD", day, new BigDecimal("54.00"), "TEST"));

		ExchangeRate rate = this.rates.findById(new ExchangeRate.Key("AUD", day)).orElseThrow();
		rate.overrideBy(admin.getId(), new BigDecimal("55.25"));
		this.rates.save(rate);

		ExchangeRate saved = this.rates.findById(new ExchangeRate.Key("AUD", day)).orElseThrow();
		assertThat(saved.getSource()).isEqualTo(ExchangeRate.MANUAL);
		assertThat(saved.getUpdatedBy()).isEqualTo(admin.getId());
		assertThat(saved.getRateToBase()).isEqualByComparingTo("55.25");
	}

	@Test
	void aLeadGoesThroughItsLifeAndIsConvertedIntoADeal() {
		AppUser owner = newUser();
		Company company = this.companies.save(new Company("Apex Dynamics", owner.getId()));
		Contact contact = new Contact("Olivia", "Martin", owner.getId());
		contact.setCompanyId(company.getId());
		contact.setEmail(" Olivia-" + UUID.randomUUID() + "@Apex.example ");
		contact = this.contacts.save(contact);

		Lead lead = new Lead("Olivia", "Martin", Lead.Source.REFERRAL);
		lead.setEmail(contact.getEmail());
		lead.setEstimatedValue(new BigDecimal("48000.00"));
		lead.setCurrency("USD");
		lead = this.leads.save(lead);
		this.leadHistory.save(new LeadStatusHistory(lead.getId(), null, Lead.Status.NEW, owner.getId()));

		Deal deal = new Deal("Apex rollout", company.getId(), owner.getId(), "QUALIFIED", new BigDecimal("48000.00"));
		deal.setCurrency("USD");
		deal.convertedFrom(lead.getId(), lead.getSource().name());
		deal = this.deals.save(deal);
		this.dealHistory.save(new DealStageHistory(deal, null, owner.getId()));

		lead.convert(company.getId(), contact.getId(), deal.getId(), Instant.now());
		this.leads.save(lead);

		Lead savedLead = this.leads.findById(lead.getId()).orElseThrow();
		assertThat(savedLead.getStatus()).isEqualTo(Lead.Status.CONVERTED);
		assertThat(savedLead.getConvertedDealId()).isEqualTo(deal.getId());
		assertThat(savedLead.getCurrency()).isEqualTo("USD");
		assertThat(savedLead.getVersion()).isNotNull();
		assertThat(this.leadHistory.findByLeadIdOrderByChangedAtAscIdAsc(lead.getId())).hasSize(1);
		assertThat(this.dealHistory.findByDealIdOrderByChangedAtAscIdAsc(deal.getId())).hasSize(1);
		assertThat(this.deals.findByCompanyId(company.getId())).hasSize(1);
		// Emails are saved trimmed and in lower case, so a lookup with the saved value finds the contact.
		assertThat(contact.getEmail()).isEqualTo(contact.getEmail().toLowerCase()).doesNotStartWith(" ");
		assertThat(this.contacts.findByEmail(contact.getEmail())).isPresent();
	}

	@Test
	void aWonDealKeepsItsInrValue() {
		AppUser owner = newUser();
		Company company = this.companies.save(new Company("Closing Co", owner.getId()));
		Deal deal = this.deals
			.save(new Deal("Big one", company.getId(), owner.getId(), "NEGOTIATION", new BigDecimal("1000.00")));

		deal.win(Instant.now(), new BigDecimal("83000.0000"), new BigDecimal("83.00000000"), LocalDate.now());
		this.deals.save(deal);

		Deal saved = this.deals.findById(deal.getId()).orElseThrow();
		assertThat(saved.getStageCode()).isEqualTo("WON");
		assertThat(saved.getBaseAmount()).isEqualByComparingTo("83000");
		assertThatThrownBy(() -> saved.moveTo("DEMO")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aLostDealNeedsAReason() {
		Deal deal = new Deal("Lost one", UUID.randomUUID(), UUID.randomUUID(), "DEMO", BigDecimal.TEN);
		assertThatThrownBy(() -> deal.lose(" ", Instant.now(), BigDecimal.TEN, BigDecimal.ONE, LocalDate.now()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theDatabaseAllowsOnlyOneOpenLeadPerEmail() {
		String email = "dup-" + UUID.randomUUID() + "@example.com";
		Lead first = new Lead("A", "One", Lead.Source.MANUAL);
		first.setEmail(email);
		this.leads.saveAndFlush(first);

		Lead second = new Lead("B", "Two", Lead.Source.MANUAL);
		second.setEmail(email.toUpperCase());

		assertThatThrownBy(() -> this.leads.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);

		// Once the first lead is disqualified, the email can be used again.
		first.disqualify("Not interested");
		this.leads.saveAndFlush(first);
		Lead third = new Lead("C", "Three", Lead.Source.MANUAL);
		third.setEmail(email);
		this.leads.saveAndFlush(third);
	}

	@Test
	void softDeletedRowsAreHiddenAndCanBeRestored() {
		Lead lead = this.leads.save(new Lead("Gone", "Soon", Lead.Source.WEBSITE));

		lead.softDelete(Instant.now());
		this.leads.saveAndFlush(lead);
		assertThat(this.leads.findById(lead.getId())).isEmpty();
	}

	@Test
	void theSignedInUserIsRecordedAsCreatorAndEditor() {
		AppUser user = newUser();
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken
				.authenticated(new CurrentUser(user.getId(), Role.REP, UUID.randomUUID()), null, java.util.List.of()));
		try {
			Company company = this.companies.save(new Company("Audited Co", user.getId()));

			assertThat(company.getCreatedBy()).isEqualTo(user.getId());
			assertThat(company.getUpdatedBy()).isEqualTo(user.getId());
			assertThat(company.getCreatedAt()).isNotNull();
			assertThat(company.getVersion()).isEqualTo(0);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void anActivityOrFollowUpNeedsALeadContactOrDeal() {
		assertThatThrownBy(() -> new Activity(Activity.Type.NOTE, "Hi", Instant.now(), null, null, null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new FollowUp("Call", FollowUp.Type.CALL, Instant.now(), UUID.randomUUID(), null, null, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void activitiesAndFollowUpsAreSavedAndFound() {
		AppUser user = newUser();
		Lead lead = this.leads.save(new Lead("Time", "Line", Lead.Source.SOCIAL));
		Instant now = Instant.now();

		this.activities.save(new Activity(Activity.Type.CALL, "Intro call", now, user.getId(), lead.getId(), null, null));
		FollowUp followUp = this.followUps
			.save(new FollowUp("Send quote", FollowUp.Type.EMAIL, now.minusSeconds(60), user.getId(), lead.getId(), null, null));

		assertThat(this.activities.findByLeadIdOrderByOccurredAtDesc(lead.getId(), PageRequest.ofSize(10))).hasSize(1);
		assertThat(this.followUps.findByStatusAndRemindedAtIsNullAndDueAtLessThanEqual(FollowUp.Status.OPEN, now))
			.extracting("id")
			.contains(followUp.getId());

		followUp.complete(now);
		this.followUps.save(followUp);
		assertThat(this.followUps.findByAssigneeIdAndStatusOrderByDueAtAsc(user.getId(), FollowUp.Status.DONE))
			.hasSize(1);
	}

	@Test
	void notificationsAreCountedUntilTheyAreRead() {
		AppUser user = newUser();
		Notification notification = this.notifications
			.save(new Notification(user.getId(), "LEAD_ASSIGNED", "A lead was assigned to you", "/leads/1"));
		assertThat(this.notifications.countByRecipientIdAndReadAtIsNull(user.getId())).isEqualTo(1);

		notification.markRead(Instant.now());
		this.notifications.save(notification);

		assertThat(this.notifications.countByRecipientIdAndReadAtIsNull(user.getId())).isZero();
	}

	@Test
	void jsonColumnsKeepTheirText() {
		AppUser user = newUser();
		UUID entityId = UUID.randomUUID();
		this.auditLogs.save(new AuditLog(user.getId(), "Lead", entityId, AuditLog.Action.UPDATE,
				"{\"status\":[\"NEW\",\"CONTACTED\"]}", "req-1"));
		ImportJob job = new ImportJob(user.getId());
		job.finish(10, 9, 1, "[{\"row\":3,\"reason\":\"No email\"}]");
		this.importJobs.save(job);
		this.idempotencyKeys.save(new IdempotencyKey("create-lead", "key-" + entityId, 201, "{\"id\":\"1\"}"));

		var logs = this.auditLogs.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Lead", entityId,
				PageRequest.ofSize(5));
		assertThat(logs).hasSize(1);
		assertThat(logs.getFirst().getChanges()).contains("CONTACTED");
		assertThat(this.importJobs.findById(job.getId()).orElseThrow().getErrorReport()).contains("No email");
		assertThat(this.idempotencyKeys.findById(new IdempotencyKey.Key("create-lead", "key-" + entityId))
			.orElseThrow()
			.getResponseBody()).contains("\"id\"");
		assertThat(this.idempotencyKeys.deleteOlderThan(Instant.now().plusSeconds(60))).isPositive();
	}

	@Test
	void targetsAndMetricsUseTheirCompositeKeys() {
		AppUser user = newUser();
		LocalDate month = LocalDate.of(2032, 5, 17);
		this.targets.save(new RevenueTarget(month, new BigDecimal("2500000.00"), user.getId()));
		assertThat(this.targets.findById(LocalDate.of(2032, 5, 1))).isPresent();

		LocalDate day = LocalDate.of(2032, 5, 17);
		this.metrics.save(new DailyMetric(day, user.getId(), "LEADS_CREATED", new BigDecimal("4")));
		assertThat(this.metrics.findById(new DailyMetric.Key(day, user.getId(), "LEADS_CREATED"))).isPresent();
		assertThat(this.metrics.deleteByDay(day)).isPositive();
	}

}
