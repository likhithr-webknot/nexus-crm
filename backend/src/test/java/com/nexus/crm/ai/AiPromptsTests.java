package com.nexus.crm.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** The prompts steer the assistant, so the parts the rest of the app depends on are pinned here. */
class AiPromptsTests {

	@Test
	void theAssistantIsToldExactlyWhichActionsItMayReturn() {
		String prompt = AiPrompts.assist("FACTS", "REQUEST");

		assertThat(prompt).contains("action must be one of none, leads, contacts, companies, deals, calendar, communications, "
				+ "campaigns, products, quotes, automations, reports, create_lead, create_contact, create_company, create_deal, "
				+ "create_task, create_activity, create_note. Use create actions only when");
		assertThat(prompt).endsWith("Live CRM facts:\nFACTS\nUser: REQUEST");
	}

	@Test
	void theStreamedAnswerAndTheOneShotAnswerBothKnowHowToProspect() {
		assertThat(AiPrompts.assist("F", "R")).contains(AiPrompts.PROSPECTING);
		assertThat(AiPrompts.streamedAssist("F", "R")).contains(AiPrompts.PROSPECTING).endsWith("Live CRM facts:\nF\nUser: R");
	}

	@Test
	void aCompanyIsAskedForASummaryAndAContactIsNot() {
		assertThat(AiPrompts.research("Organization Acme", true)).contains("Also write summary: one paragraph of 90 to 130 words");
		assertThat(AiPrompts.research("Priya Shah", false)).contains("Set summary to null. ").doesNotContain("90 to 130 words");
		assertThat(AiPrompts.research("x", false)).endsWith("sources (array of URL strings).");
	}

	@Test
	void theRecordDraftPromptNamesTheKindAndEndsWithTheRequest() {
		assertThat(AiPrompts.recordDraft("contact", "Priya at Acme"))
			.startsWith("Extract only details that the user explicitly supplied for a new contact record. ")
			.endsWith("User request: Priya at Acme");
	}

	@Test
	@SuppressWarnings("unchecked")
	void theWorkflowAnswerShapeListsOnlySupportedChoices() {
		Map<String, Object> format = AiPrompts.workflowFormat();

		assertThat(format).containsEntry("name", "crm_workflow").containsEntry("strict", true);
		Map<String, Object> properties = (Map<String, Object>) ((Map<String, Object>) format.get("schema")).get("properties");
		assertThat(properties).containsOnlyKeys("trigger", "actions", "delay", "condition", "message");
		assertThat((Map<String, Object>) properties.get("trigger")).containsEntry("enum", java.util.List.of("Deal created", "Deal won", "Lead qualified", "none"));
	}

	@Test
	void theRecordDraftShapeRequiresEveryField() {
		Map<String, Object> format = AiPrompts.recordDraftFormat();

		@SuppressWarnings("unchecked")
		Map<String, Object> schema = (Map<String, Object>) format.get("schema");
		assertThat(schema.get("required")).isEqualTo(AiPrompts.RECORD_DRAFT_FIELDS);
		assertThat(schema).containsEntry("additionalProperties", false);
	}

}
