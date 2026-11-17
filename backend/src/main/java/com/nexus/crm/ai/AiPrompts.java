package com.nexus.crm.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the assistant asks the AI model, and the shape of answer it requires back. Kept apart from {@link AiService} so
 * the service reads as logic and these long texts can be edited on their own. Changing a prompt changes how the
 * assistant behaves, so read a prompt in full before editing it.
 */
final class AiPrompts {

	/** What the assistant may tell the app to do after an answer. Anything else is treated as "none". */
	static final List<String> ASSISTANT_ACTIONS = List.of("none", "leads", "contacts", "companies", "deals", "calendar",
			"communications", "campaigns", "products", "quotes", "automations", "reports", "create_lead", "create_contact",
			"create_company", "create_deal", "create_task", "create_activity", "create_note");

	static final Set<String> WORKFLOW_TRIGGERS = Set.of("Deal created", "Deal won", "Lead qualified");

	static final Set<String> WORKFLOW_ACTIONS = Set.of("Notify owner", "Create follow-up");

	static final Set<String> WORKFLOW_DELAYS = Set.of("none", "Wait 1 hour", "Wait 1 day");

	static final Set<String> WORKFLOW_CONDITIONS = Set.of("none", "Deal is won", "Lead is qualified");

	/** The most steps one generated workflow may have. */
	static final int MAX_WORKFLOW_ACTIONS = 4;

	/** How the assistant looks for new business. Used by the streamed and the one-shot answer. */
	static final String PROSPECTING = "When asked to find prospects, opportunities or companies to approach: if the user has not said what "
			+ "their organization sells, ask for that in one sentence and stop. Otherwise search the public web for organizations showing a current need "
			+ "for it, such as a tender or RFP, a hiring post, a funding or expansion announcement, or a stated problem. List at most 8, each with the "
			+ "company, the dated signal you found, why it fits, and a first step. Leave out companies already in the live CRM facts, and say so when "
			+ "a strong match is already a lead or company there. When asked about existing leads, use the live CRM facts, and for their companies you "
			+ "may search for the same kind of signal. Never invent a company, a signal or a contact detail. ";

	private AiPrompts() {
	}



	/** The general assistant. {@code liveFacts} is a summary of the user's own CRM records. */
	static String assist(String liveFacts, String request) {
		return "You are the Nexus CRM assistant. Answer only about the CRM, supplied live CRM facts, and public company or market research. "
				+ "Search the public web for research questions. Give a concise market assessment with competitors, demand, risks and evidence when asked. "
				+ "Do not invent records, market figures or expose secrets. If the facts are insufficient, say what is missing. "
				+ PROSPECTING
				+ "Return ONLY JSON with keys answer and action. action must be one of " + String.join(", ", ASSISTANT_ACTIONS) + ". "
				+ "Use create actions only when the user asks to add a record. The app will open a form for review; do not claim it was created. "
				+ "Use workspace actions when the user clearly wants to open that area.\nLive CRM facts:\n" + liveFacts
				+ "\nUser: " + request;
	}

	/** The same assistant for the live, word-by-word answer, which is plain text rather than JSON. */
	static String streamedAssist(String liveFacts, String request) {
		return "You are the Nexus CRM assistant. Answer only questions within the CRM and business research boundaries. "
				+ "Use the supplied live CRM facts for private records and never invent a record. "
				+ "For public company or market questions, search the web and assess market position, demand, competitors and risks using sourced facts. "
				+ "State uncertainty when evidence is thin; never guess private contact details or precise market numbers. "
				+ PROSPECTING
				+ "Do not claim you created or changed records; a user must review a form first. Write clear plain text.\nLive CRM facts:\n"
				+ liveFacts + "\nUser: " + request;
	}

	static String recordDraft(String kind, String request) {
		return "Extract only details that the user explicitly supplied for a new " + kind + " record. "
				+ "Never invent a name, phone, email, domain, company, job title or industry. Use an empty string for every missing field. "
				+ "For a contact, split a supplied full name into firstName and lastName only when both parts are clear. "
				+ "For a company, use name, domain and industry. User request: " + request;
	}

	static String email(String context) {
		return "Draft a concise professional customer email from the user's context. "
				+ "Return only the body text. Do not invent names, facts, commitments, attachments or contact details. "
				+ "Do not send it. Context: " + context;
	}

	static String workflow(String request) {
		return "Map this CRM workflow request to executable steps. "
				+ "Triggers: Deal created, Deal won, Lead qualified. Actions: Notify owner, Create follow-up. "
				+ "Include every requested supported action in order; a workflow can have more than one action. "
				+ "Optional delays: none, Wait 1 hour, Wait 1 day. Optional conditions: none, Deal is won, Lead is qualified. "
				+ "If the request needs an unsupported trigger or action, set trigger to none, actions to an empty list and explain in message. "
				+ "Do not reject a request just because it contains two supported actions. Request: " + request;
	}

	/** Public-web research on one CRM record. {@code subject} describes the record; companies also get a written summary. */
	static String research(String subject, boolean company) {
		String summary = company
				? "Also write summary: one paragraph of 90 to 130 words that helps a salesperson size up this organization. Say what it sells and to whom, "
						+ "then give the figures that matter most: latest annual revenue and its growth, profit or margin, headcount, number of clients or "
						+ "countries served, and large recent deals or strategic moves. State the fiscal year or date each figure applies to. Use only figures "
						+ "you found in the sources and leave out any you could not find; never estimate. Plain prose only: no markdown, lists, links or citations. "
				: "Set summary to null. ";
		return "Research this real CRM record on the public internet using web search: " + subject + ". "
				+ "Find the official organization website, a public logo image URL, and a publicly listed contact email if applicable. "
				+ "Never infer or guess an email address. If uncertain return null. Include source URLs for every claim. "
				+ summary
				+ "Return ONLY JSON with keys website, logoUrl, email, summary, sources (array of URL strings).";
	}


	/** The fields the model fills in for a new contact or company card. */
	static final List<String> RECORD_DRAFT_FIELDS = List.of("firstName", "lastName", "phone", "email", "jobTitle",
			"companyName", "name", "domain", "industry");

	static Map<String, Object> recordDraftFormat() {
		Map<String, Object> properties = new HashMap<>();
		RECORD_DRAFT_FIELDS.forEach((field) -> properties.put(field, Map.of("type", "string")));
		Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false, "properties", properties,
				"required", RECORD_DRAFT_FIELDS);
		return Map.of("type", "json_schema", "name", "crm_record_draft", "strict", true, "schema", schema);
	}

	static Map<String, Object> workflowFormat() {
		Map<String, Object> properties = Map.of(
				"trigger", choice(List.of("Deal created", "Deal won", "Lead qualified", "none")),
				"actions", Map.of("type", "array", "items", choice(List.of("Notify owner", "Create follow-up"))),
				"delay", choice(List.of("none", "Wait 1 hour", "Wait 1 day")),
				"condition", choice(List.of("none", "Deal is won", "Lead is qualified")),
				"message", Map.of("type", "string"));
		Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false, "properties", properties,
				"required", List.of("trigger", "actions", "delay", "condition", "message"));
		return Map.of("type", "json_schema", "name", "crm_workflow", "strict", true, "schema", schema);
	}

	/** A string that must be one of the listed values. */
	private static Map<String, Object> choice(List<String> values) {
		return Map.of("type", "string", "enum", values);
	}

}
