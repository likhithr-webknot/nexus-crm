package com.nexus.crm.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.mail.MailService;
import com.nexus.crm.notifications.NotificationService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

@Service
@RequiredArgsConstructor
public class AiService {

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final UserService users;

	private final AiKeyService keys;

	private final NotificationService notifications;

	private final MailService mail;

	private final TransactionTemplate transactions;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	@Value("${NEXUS_AI_MODEL:gpt-6-luna}")
	private String model;

	@Value("${NEXUS_AI_MONTHLY_REQUEST_LIMIT:0}")
	private int monthlyRequestLimit;

	/**
	 * How the assistant looks for new business. Shared by the streamed and the one-shot
	 * answer.
	 */
	private enum Refusal {

		NONE, HOUR, MONTH

	}

	/** How many AI requests one user may make in any hour. */
	private static final int HOURLY_LIMIT = 30;

	public record Status(boolean configured, String keyName, String message) {
	}

	public record Answer(String answer, String action) {
	}

	public record Workflow(boolean supported, String message, List<Map<String, Object>> nodes,
			List<Map<String, Object>> edges) {
	}

	/** summary is null for contacts and for research saved before it existed. */
	public record Research(String website, String logoUrl, String email, String summary, List<String> sources,
			Instant researchedAt) {
	}

	public record RecordDraft(String firstName, String lastName, String phone, String email, String jobTitle,
			String companyName, String name, String domain, String industry) {
	}

	public RecordDraft recordDraft(CurrentUser user, String kind, String message) {
		if (!Set.of("contact", "company").contains(kind)) {
			throw new ApiException(ErrorCode.NOT_FOUND, "Assistant card is unavailable.");
		}
		String result = callResult(user, "record_draft", AiPrompts.recordDraft(kind, limit(message, 2000)), false,
				AiPrompts.recordDraftFormat())
			.text();
		try {
			return json.readValue(result, RecordDraft.class);
		}
		catch (RuntimeException error) {
			throw new ApiException(ErrorCode.INVALID_STATE,
					"The assistant could not prepare this record. Fill the card manually.");
		}
	}

	public Status status() {
		AiKeyService.KeyStatus key = keys.status();
		return new Status(key.configured(), key.keyName(),
				key.configured() ? "AI is available." : "AI API key has not been set.");
	}

	public Answer assist(CurrentUser user, String message) {
		String request = limit(message, 2000);
		boolean web = researchIntent(request);
		ProviderAnswer result = callResult(user, "assist", AiPrompts.assist(snapshot(user), request), web);
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> data = json.readValue(result.text(), Map.class);
			String answer = String.valueOf(data.getOrDefault("answer", ""));
			if (web && !result.sources().isEmpty()) {
				answer += "\n\nPublic sources:\n" + String.join("\n", result.sources());
			}
			String action = String.valueOf(data.getOrDefault("action", "none"));
			if (!AiPrompts.ASSISTANT_ACTIONS.contains(action)) {
				action = "none";
			}
			return new Answer(answer, action);
		}
		catch (RuntimeException error) {
			return new Answer(result.text(), "none");
		}
	}

	public String draftEmail(CurrentUser user, String context) {
		return call(user, "email", AiPrompts.email(limit(context, 3000)), false);
	}

	/** Turns a plain-language request into workflow steps, but only steps the CRM can really run. */
	public Workflow workflow(CurrentUser user, String description) {
		String output = callResult(user, "workflow", AiPrompts.workflow(limit(description, 2000)), false,
				AiPrompts.workflowFormat())
			.text();
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> data = json.readValue(output, Map.class);
			String trigger = String.valueOf(data.getOrDefault("trigger", "none"));
			List<String> actions = data.get("actions") instanceof List<?> list
					? list.stream().map(String::valueOf).toList() : List.of();
			String delay = String.valueOf(data.getOrDefault("delay", "none"));
			String condition = String.valueOf(data.getOrDefault("condition", "none"));
			String message = String.valueOf(data.getOrDefault("message", ""));

			boolean supported = AiPrompts.WORKFLOW_TRIGGERS.contains(trigger) && !actions.isEmpty()
					&& actions.size() <= AiPrompts.MAX_WORKFLOW_ACTIONS && AiPrompts.WORKFLOW_ACTIONS.containsAll(actions)
					&& AiPrompts.WORKFLOW_DELAYS.contains(delay) && AiPrompts.WORKFLOW_CONDITIONS.contains(condition);
			if (!supported) {
				return new Workflow(false,
						message.isBlank() ? "This request needs steps the CRM does not support yet." : message,
						List.of(), List.of());
			}

			List<Map<String, Object>> nodes = new ArrayList<>();
			List<Map<String, Object>> edges = new ArrayList<>();
			add(nodes, edges, "Trigger", trigger);
			if (!"none".equals(condition)) {
				add(nodes, edges, "Condition", condition);
			}
			if (!"none".equals(delay)) {
				add(nodes, edges, "Delay", delay);
			}
			for (String action : actions) {
				add(nodes, edges, "Action", action);
			}
			return new Workflow(true, message, nodes, edges);
		}
		catch (RuntimeException error) {
			throw new ApiException(ErrorCode.INVALID_STATE,
					"The AI workflow response could not be used. Try describing it more simply.");
		}
	}

	public Research research(CurrentUser user, String kind, UUID id) {
		String subject = subject(user, kind, id);
		ProviderAnswer result = callResult(user, "research", AiPrompts.research(subject, "company".equals(kind)), true);
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> data = json.readValue(result.text(), Map.class);
			List<String> sources = result.sources();
			Research research = new Research(safeUrl(data.get("website")), safeUrl(data.get("logoUrl")),
					safeEmail(data.get("email")), "company".equals(kind) ? summary(data.get("summary")) : null, sources,
					Instant.now());
			// Nothing is kept when the search cited no source: the answer would be
			// unverified.
			if (sources.isEmpty()) {
				research = new Research(null, null, null, null, List.of(), Instant.now());
			}
			jdbc.sql("insert into ai_research (kind,record_id,result) values (:kind,:id,cast(:result as jsonb)) "
					+ "on conflict (kind,record_id) do update set result=excluded.result,researched_at=now()")
				.param("kind", kind)
				.param("id", id)
				.param("result", json.writeValueAsString(research))
				.update();
			return research;
		}
		catch (RuntimeException error) {
			throw new ApiException(ErrorCode.INVALID_STATE, "Research could not be verified. Try again later.");
		}
	}

	public Research savedResearch(CurrentUser user, String kind, UUID id) {
		subject(user, kind, id);
		return jdbc.sql("select result::text from ai_research where kind=:kind and record_id=:id")
			.param("kind", kind)
			.param("id", id)
			.query(String.class)
			.optional()
			.map(value -> json.readValue(value, Research.class))
			.orElse(null);
	}

	private String subject(CurrentUser user, String kind, UUID id) {
		if ("company".equals(kind)) {
			var company = jdbc.sql("select name,domain,owner_id from company where id=:id and deleted_at is null")
				.param("id", id)
				.query((rs, n) -> Map.of("name", rs.getString("name"), "domain",
						rs.getString("domain") == null ? "" : rs.getString("domain"), "owner",
						rs.getObject("owner_id", UUID.class)))
				.optional()
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Company not found."));
			requireVisible(user, (UUID) company.get("owner"));
			return "Organization " + company.get("name") + ", domain " + company.get("domain");
		}
		if ("contact".equals(kind)) {
			var contact = jdbc
				.sql("select first_name,last_name,company_id,owner_id from contact where id=:id and deleted_at is null")
				.param("id", id)
				.query((rs, n) -> Map.of("name", rs.getString("first_name") + " " + rs.getString("last_name"), "owner",
						rs.getObject("owner_id", UUID.class), "company",
						rs.getObject("company_id", UUID.class) == null ? ""
								: rs.getObject("company_id", UUID.class).toString()))
				.optional()
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Contact not found."));
			requireVisible(user, (UUID) contact.get("owner"));
			String company = "";
			if (!"".equals(contact.get("company"))) {
				company = jdbc.sql("select name from company where id=:id")
					.param("id", UUID.fromString((String) contact.get("company")))
					.query(String.class)
					.optional()
					.orElse("");
			}
			return "Contact " + contact.get("name") + " at " + company;
		}
		throw new ApiException(ErrorCode.NOT_FOUND, "Research target not found.");
	}

	private void requireVisible(CurrentUser user, UUID owner) {
		if (users.visibleOwnerIds(user).map(ids -> !ids.contains(owner)).orElse(false)) {
			throw new ApiException(ErrorCode.NOT_FOUND, "Record not found.");
		}
	}

	private String snapshot(CurrentUser user) {
		var visible = users.visibleOwnerIds(user);
		String scope = visible.isPresent() ? "owner_id in (:ids)" : "true";
		List<UUID> ids = visible.map(s -> s.isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(s)).orElse(List.of());
		StringBuilder out = new StringBuilder();
		for (String table : List.of("lead", "contact", "company", "deal")) {
			var query = jdbc.sql("select count(*) from " + table + " where deleted_at is null and " + scope);
			if (visible.isPresent()) {
				query = query.param("ids", ids);
			}
			out.append(table).append(" count: ").append(query.query(Long.class).single()).append(". ");
			// One line per record with what a salesperson would ask about. No emails or
			// phone numbers leave the CRM.
			String line = switch (table) {
				case "lead" ->
					"concat_ws(' ', first_name, last_name) || coalesce(' at ' || company_name, '') || ' (' || status || ', source ' || source"
							+ " || coalesce(', ' || job_title, '') || coalesce(', value ' || trunc(estimated_value) || ' ' || currency, '') || ')'";
				case "deal" ->
					"name || ' (' || stage_code || ', ' || trunc(amount) || ' ' || currency || coalesce(', closes ' || expected_close_date, '') || ')'";
				case "company" -> "name || coalesce(' (' || industry || ')', '')";
				default -> "concat_ws(' ', first_name, last_name) || coalesce(' (' || job_title || ')', '')";
			};
			var recent = jdbc.sql("select " + line + " from " + table + " where deleted_at is null and " + scope
					+ " order by updated_at desc limit " + ("contact".equals(table) ? 20 : 40));
			if (visible.isPresent()) {
				recent = recent.param("ids", ids);
			}
			out.append("Recent ").append(table).append(": ").append(recent.query(String.class).list()).append(". ");
		}
		for (String kind : List.of("communications", "campaigns", "automations", "products", "quotes")) {
			String moduleScope = "products".equals(kind) ? "true" : scope;
			var count = jdbc
				.sql("select count(*) from crm_module_record where kind=:kind and deleted_at is null and "
						+ moduleScope)
				.param("kind", kind);
			if (visible.isPresent() && !"products".equals(kind)) {
				count = count.param("ids", ids);
			}
			out.append(kind).append(" count: ").append(count.query(Long.class).single()).append(". ");
		}
		return out.toString();
	}

	private void add(List<Map<String, Object>> nodes, List<Map<String, Object>> edges, String type, String label) {
		String id = UUID.randomUUID().toString();
		if (!nodes.isEmpty()) {
			edges.add(Map.of("id", UUID.randomUUID().toString(), "source", nodes.getLast().get("id"), "target", id,
					"label", "Condition".equals(nodes.getLast().get("type")) ? "Yes" : ""));
		}
		nodes.add(Map.of("id", id, "type", type, "label", label, "x", 80 + nodes.size() * 250, "y", 190));
	}

	private record ProviderAnswer(String text, List<String> sources) {
	}

	private String call(CurrentUser user, String kind, String prompt, boolean web) {
		return callResult(user, kind, prompt, web).text();
	}

	private ProviderAnswer callResult(CurrentUser user, String kind, String prompt, boolean web) {
		return callResult(user, kind, prompt, web, null);
	}

	private ProviderAnswer callResult(CurrentUser user, String kind, String prompt, boolean web,
			Map<String, Object> format) {
		AiKeyService.Credentials credential = activeCredential();
		UUID requestId = reserve(user, kind);
		boolean answered = false;
		try {
			ProviderAnswer answer = askProvider(credential, requestId, prompt, web, format);
			answered = true;
			return answer;
		}
		finally {
			// No answer, no charge against the user's hourly limit.
			if (!answered) {
				release(requestId);
			}
		}
	}

	private AiKeyService.Credentials activeCredential() {
		AiKeyService.Credentials credential = keys.active();
		if (credential.key().isBlank()) {
			throw new ApiException(ErrorCode.INVALID_STATE, "AI API key has not been set.");
		}
		return credential;
	}

	private ProviderAnswer askProvider(AiKeyService.Credentials credential, UUID requestId, String prompt, boolean web,
			Map<String, Object> format) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
				.timeout(Duration.ofSeconds(90))
				.header("Authorization", "Bearer " + credential.key())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requestBody(prompt, web, format))))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw refusal(credential, response);
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> data = json.readValue(response.body(), Map.class);
			String text = outputText(data);
			if (text.isBlank()) {
				throw new ApiException(ErrorCode.INVALID_STATE, "AI returned no answer. Try again.");
			}
			recordUsage(credential, requestId, data);
			return new ProviderAnswer(text, web ? citedUrls(data) : List.of());
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new ApiException(ErrorCode.INVALID_STATE, "AI request was interrupted.");
		}
		catch (java.io.IOException error) {
			throw new ApiException(ErrorCode.INVALID_STATE, "AI is unreachable. Try again later.");
		}
	}

	private Map<String, Object> requestBody(String prompt, boolean web, Map<String, Object> format) {
		Map<String, Object> body = new java.util.HashMap<>(Map.of("model", model, "input", prompt, "store", false));
		if (web) {
			body.put("tools", List.of(Map.of("type", "web_search")));
			body.put("tool_choice", "required");
			body.put("include", List.of("web_search_call.action.sources"));
		}
		if (format != null) {
			body.put("text", Map.of("format", format));
		}
		return body;
	}

	/**
	 * The error to show when OpenAI says no. A used-up billing quota also alerts the
	 * admins.
	 */
	private ApiException refusal(AiKeyService.Credentials credential, HttpResponse<String> response) {
		int status = response.statusCode();
		if (status == 429 && response.body().contains("insufficient_quota")) {
			alert(credential.name(), "billing_quota",
					"AI key " + credential.name() + " has reached its billing quota. Please recharge it.");
			return new ApiException(ErrorCode.INVALID_STATE,
					"AI billing quota was reached. An admin has been alerted.");
		}
		return new ApiException(ErrorCode.INVALID_STATE,
				status == 429 ? "AI is busy. Try again shortly." : "AI is unavailable. Try again later.");
	}

	/**
	 * Saves the token counts on the request row, and warns the admins when the monthly
	 * allowance is nearly used.
	 */
	@SuppressWarnings("unchecked")
	private void recordUsage(AiKeyService.Credentials credential, UUID requestId, Map<String, Object> data) {
		Map<String, Object> usage = data.get("usage") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
		jdbc.sql("update ai_request set input_tokens=:input, output_tokens=:output where id=:id")
			.param("input", number(usage.get("input_tokens")))
			.param("output", number(usage.get("output_tokens")))
			.param("id", requestId)
			.update();
		if (monthlyRequestLimit <= 0) {
			return;
		}
		long thisMonth = jdbc.sql("select count(*) from ai_request where created_at >= date_trunc('month', now())")
			.query(Long.class)
			.single();
		if (thisMonth >= Math.ceil(monthlyRequestLimit * .8)) {
			alert(credential.name(), "request_budget", "AI key " + credential.name()
					+ " has used at least 80% of the configured monthly request allowance.");
		}
	}

	/**
	 * Takes one of the user's hourly slots, or refuses. The count and the insert run
	 * while holding a lock for this user, so requests arriving together cannot all see
	 * "29 used" and all go ahead. The lock is released when the transaction ends.
	 */
	UUID reserve(CurrentUser user, String kind) {
		UUID id = UUID.randomUUID();
		Refusal taken = transactions.execute(tx -> {
			jdbc.sql("select count(*) from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) lock")
				.param("key", "ai-request:" + user.id())
				.query(Long.class)
				.single();
			long lastHour = jdbc
				.sql("select count(*) from ai_request where user_id = :user and created_at > now() - interval '1 hour'")
				.param("user", user.id())
				.query(Long.class)
				.single();
			if (lastHour >= HOURLY_LIMIT) {
				return Refusal.HOUR;
			}
			if (monthlyRequestLimit > 0) {
				// The whole team shares the monthly allowance, so take a second lock for
				// the count. Always in this order, so locks cannot deadlock.
				jdbc.sql(
						"select count(*) from (select pg_advisory_xact_lock(hashtextextended('ai-request:month', 0))) lock")
					.query(Long.class)
					.single();
				long thisMonth = jdbc
					.sql("select count(*) from ai_request where created_at >= date_trunc('month', now())")
					.query(Long.class)
					.single();
				if (thisMonth >= monthlyRequestLimit) {
					return Refusal.MONTH;
				}
			}
			jdbc.sql("insert into ai_request (id, user_id, kind) values (:id, :user, :kind)")
				.param("id", id)
				.param("user", user.id())
				.param("kind", kind)
				.update();
			return Refusal.NONE;
		});
		if (taken == Refusal.HOUR) {
			throw new ApiException(ErrorCode.INVALID_STATE, "AI request limit reached for this hour. Try later.");
		}
		if (taken == Refusal.MONTH) {
			throw new ApiException(ErrorCode.INVALID_STATE, "The monthly AI allowance has been used. Ask an admin.");
		}
		return id;
	}

	/**
	 * Gives a slot back. If this fails the slot stays used for an hour, which is
	 * harmless.
	 */
	void release(UUID requestId) {
		try {
			jdbc.sql("delete from ai_request where id = :id").param("id", requestId).update();
		}
		catch (RuntimeException ignored) {
			// Nothing to do.
		}
	}

	public SseEmitter streamAssist(CurrentUser user, String message) {
		String request = limit(message, 2000);
		AiKeyService.Credentials credential = activeCredential();
		boolean web = researchIntent(request);
		String prompt = AiPrompts.streamedAssist(snapshot(user), request);
		UUID requestId = reserve(user, web ? "market_stream" : "assist_stream");
		SseEmitter emitter = new SseEmitter(120_000L);
		Thread.ofVirtual()
			.name("nexus-ai-stream")
			.start(() -> streamProvider(emitter, credential, prompt, web, request, requestId));
		return emitter;
	}

	@SuppressWarnings("unchecked")
	private void streamProvider(SseEmitter emitter, AiKeyService.Credentials credential, String prompt, boolean web,
			String request, UUID requestId) {
		try {
			sendEvent(emitter, "start", Map.of("researching", web));
			Map<String, Object> body = web
					? Map.of("model", model, "input", prompt, "tools", List.of(Map.of("type", "web_search")),
							"tool_choice", "required", "include", List.of("web_search_call.action.sources"),
							"reasoning", Map.of("effort", "low"), "max_output_tokens", 2500, "store", false, "stream",
							true)
					: Map.of("model", model, "input", prompt, "reasoning", Map.of("effort", "low"), "max_output_tokens",
							1500, "store", false, "stream", true);
			HttpRequest provider = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
				.timeout(Duration.ofSeconds(100))
				.header("Authorization", "Bearer " + credential.key())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
				.build();
			HttpResponse<Stream<String>> response = http.send(provider, HttpResponse.BodyHandlers.ofLines());
			try (Stream<String> lines = response.body()) {
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					String error = lines.limit(20).reduce("", (a, b) -> a + b);
					if (response.statusCode() == 429 && error.contains("insufficient_quota")) {
						alert(credential.name(), "billing_quota",
								"AI key " + credential.name() + " has reached its billing quota. Please recharge it.");
						sendEvent(emitter, "error",
								Map.of("message", "AI billing quota was reached. An admin has been alerted."));
					}
					else {
						sendEvent(emitter, "error", Map.of("message", "AI is unavailable. Try again later."));
					}
					release(requestId);
					emitter.complete();
					return;
				}
				boolean completed = false;
				for (String line : (Iterable<String>) lines::iterator) {
					if (!line.startsWith("data: ")) {
						continue;
					}
					String payload = line.substring(6);
					if ("[DONE]".equals(payload)) {
						break;
					}
					Map<String, Object> event = json.readValue(payload, Map.class);
					String type = String.valueOf(event.get("type"));
					if ("response.output_text.delta".equals(type)) {
						String delta = String.valueOf(event.getOrDefault("delta", ""));
						if (!delta.isEmpty()) {
							sendEvent(emitter, "delta", Map.of("text", delta));
						}
					}
					else if ("response.completed".equals(type) && event.get("response") instanceof Map<?, ?> finished) {
						Map<String, Object> result = (Map<String, Object>) finished;
						Map<String, Object> usage = result.get("usage") instanceof Map<?, ?> values
								? (Map<String, Object>) values : Map.of();
						jdbc.sql("update ai_request set input_tokens=:input,output_tokens=:output where id=:id")
							.param("input", number(usage.get("input_tokens")))
							.param("output", number(usage.get("output_tokens")))
							.param("id", requestId)
							.update();
						if (monthlyRequestLimit > 0) {
							long monthCount = jdbc
								.sql("select count(*) from ai_request where created_at>=date_trunc('month',now())")
								.query(Long.class)
								.single();
							if (monthCount >= Math.ceil(monthlyRequestLimit * .8)) {
								alert(credential.name(), "request_budget", "AI key " + credential.name()
										+ " has used at least 80% of the configured monthly request allowance.");
							}
						}
						List<String> sources = web ? citedUrls(result) : List.of();
						if (!sources.isEmpty()) {
							sendEvent(emitter, "sources", Map.of("urls", sources));
						}
						sendEvent(emitter, "done", Map.of("action", suggestedAction(request)));
						completed = true;
					}
					else if ("error".equals(type) || "response.failed".equals(type)) {
						sendEvent(emitter, "error", Map.of("message", "AI could not complete the answer. Try again."));
						break;
					}
				}
				if (!completed) {
					sendEvent(emitter, "error", Map.of("message", "AI stopped before finishing. Try again."));
				}
			}
			emitter.complete();
		}
		catch (Exception error) {
			try {
				sendEvent(emitter, "error", Map.of("message", "AI stream was interrupted. Try again."));
			}
			catch (Exception ignored) {
			}
			emitter.complete();
		}
	}

	private void sendEvent(SseEmitter emitter, String name, Object data) throws java.io.IOException {
		emitter.send(SseEmitter.event().name(name).data(data));
	}

	private String suggestedAction(String request) {
		String lower = request.toLowerCase(java.util.Locale.ROOT);
		boolean create = lower.matches("(?s).*(add|create|new)\\s+.*");
		if (create) {
			for (String kind : List.of("lead", "contact", "company", "deal", "task", "activity", "note")) {
				if (lower.contains(kind)) {
					return "create_" + kind;
				}
			}
		}
		if (lower.matches("(?s).*(open|show|go to)\\s+.*")) {
			for (String kind : List.of("leads", "contacts", "companies", "deals", "calendar", "communications",
					"campaigns", "products", "quotes", "automations", "reports"))
				if (lower.contains(kind)) {
					return kind;
				}
		}
		return "none";
	}

	private boolean researchIntent(String request) {
		return request.toLowerCase(java.util.Locale.ROOT)
			.matches("(?s).*(research|market|competitor|industry|website|latest|look up|tell me about|assess"
					+ "|prospect|opportunit|looking for|who needs|find (companies|leads|customers|clients|businesses)|potential (customers|clients|leads|buyers)).*");
	}

	@SuppressWarnings("unchecked")
	private String outputText(Map<String, Object> data) {
		StringBuilder text = new StringBuilder();
		if (!(data.get("output") instanceof List<?> output)) {
			return "";
		}
		for (Object item : output) {
			if (item instanceof Map<?, ?> message && message.get("content") instanceof List<?> content) {
				for (Object part : content) {
					if (part instanceof Map<?, ?> entry && "output_text".equals(entry.get("type"))) {
						text.append(String.valueOf(entry.get("text")));
					}
				}
			}
		}
		return text.toString();
	}

	private List<String> citedUrls(Map<String, Object> data) {
		if (!(data.get("output") instanceof List<?> output)) {
			return List.of();
		}
		List<String> urls = new ArrayList<>();
		for (Object item : output) {
			if (item instanceof Map<?, ?> message && message.get("content") instanceof List<?> content) {
				for (Object part : content) {
					if (part instanceof Map<?, ?> entry && entry.get("annotations") instanceof List<?> annotations) {
						for (Object annotation : annotations) {
							if (annotation instanceof Map<?, ?> cite && "url_citation".equals(cite.get("type"))) {
								String url = String.valueOf(cite.get("url"));
								if (https(url) && !urls.contains(url) && urls.size() < 8) {
									urls.add(url);
								}
							}
						}
					}
				}
			}
		}
		for (Object item : output) {
			for (Object source : sourcesOf(item)) {
				if (source instanceof Map<?, ?> entry) {
					String url = String.valueOf(entry.get("url"));
					if (https(url) && !urls.contains(url) && urls.size() < 8) {
						urls.add(url);
					}
				}
			}
		}
		return urls;
	}

	/** The sources a web-search step of the provider's output cites. Any other kind of step cites none. */
	private static List<?> sourcesOf(Object item) {
		if (item instanceof Map<?, ?> call && "web_search_call".equals(call.get("type"))
				&& call.get("action") instanceof Map<?, ?> action && action.get("sources") instanceof List<?> sources) {
			return sources;
		}
		return List.of();
	}

	private void alert(String keyName, String type, String message) {
		String period = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC).format(Instant.now());
		int created = jdbc.sql(
				"insert into ai_alert (id,key_name,alert_type,period) values (:id,:name,:type,:period) on conflict do nothing")
			.param("id", UUID.randomUUID())
			.param("name", keyName)
			.param("type", type)
			.param("period", period)
			.update();
		if (created == 0) {
			return;
		}
		List<Map<String, Object>> admins = jdbc
			.sql("select id,email from app_user where role='ADMIN' and status='ACTIVE'")
			.query((rs, n) -> Map.<String, Object>of("id", rs.getObject("id", UUID.class), "email",
					rs.getString("email")))
			.list();
		for (var admin : admins) {
			UUID id = (UUID) admin.get("id");
			notifications.send(id, "AI_QUOTA", message, "/settings");
			try {
				mail.sendSystemAlert(id, (String) admin.get("email"), "Nexus CRM AI quota alert", message);
			}
			catch (RuntimeException ignored) {
				/* In-app alert remains available when mailbox is not connected. */ }
		}
	}

	private String safeUrl(Object value) {
		String url = String.valueOf(value);
		return https(url) && url.length() <= 1000 ? url : null;
	}

	private boolean https(String url) {
		try {
			URI uri = URI.create(url);
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
		}
		catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * The summary as plain text: links and citation marks removed, at most 1200
	 * characters.
	 */
	private String summary(Object value) {
		if (!(value instanceof String raw)) {
			return null;
		}
		String text = raw.replaceAll("\\(?\\[([^\\]]*)\\]\\([^)]*\\)\\)?", "")
			.replaceAll("https?://\\S+", "")
			.replaceAll("\\s+", " ")
			.replaceAll(" ([.,;:])", "$1")
			.strip();
		if (text.isEmpty()) {
			return null;
		}
		return text.length() <= 1200 ? text : text.substring(0, 1200).strip() + "…";
	}

	private String safeEmail(Object value) {
		String email = String.valueOf(value);
		return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") && email.length() <= 254 ? email : null;
	}

	private String limit(String value, int max) {
		if (value == null || value.isBlank() || value.length() > max) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Enter a shorter, nonempty request.");
		}
		return value.strip();
	}

	private int number(Object value) {
		return value instanceof Number n ? n.intValue() : 0;
	}

}
