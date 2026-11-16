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
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  @Value("${NEXUS_AI_MODEL:gpt-6-luna}") private String model;
  @Value("${NEXUS_AI_MONTHLY_REQUEST_LIMIT:0}") private int monthlyRequestLimit;

  /** How the assistant looks for new business. Shared by the streamed and the one-shot answer. */
  private static final String PROSPECTING = "When asked to find prospects, opportunities or companies to approach: if the user has not said what "
    + "their organization sells, ask for that in one sentence and stop. Otherwise search the public web for organizations showing a current need "
    + "for it, such as a tender or RFP, a hiring post, a funding or expansion announcement, or a stated problem. List at most 8, each with the "
    + "company, the dated signal you found, why it fits, and a first step. Leave out companies already in the live CRM facts, and say so when "
    + "a strong match is already a lead or company there. When asked about existing leads, use the live CRM facts, and for their companies you "
    + "may search for the same kind of signal. Never invent a company, a signal or a contact detail. ";

  public record Status(boolean configured, String keyName, String message) { }
  public record Answer(String answer, String action) { }
  public record Workflow(boolean supported, String message, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) { }
  /** summary is null for contacts and for research saved before it existed. */
  public record Research(String website, String logoUrl, String email, String summary, List<String> sources, Instant researchedAt) { }
  public record RecordDraft(String firstName, String lastName, String phone, String email, String jobTitle,
      String companyName, String name, String domain, String industry) { }

  public RecordDraft recordDraft(CurrentUser user, String kind, String message) {
    if (!Set.of("contact", "company").contains(kind)) throw new ApiException(ErrorCode.NOT_FOUND, "Assistant card is unavailable.");
    List<String> fields = List.of("firstName", "lastName", "phone", "email", "jobTitle", "companyName", "name", "domain", "industry");
    Map<String, Object> properties = new java.util.HashMap<>();
    fields.forEach(field -> properties.put(field, Map.of("type", "string")));
    Map<String, Object> format = Map.of("type", "json_schema", "name", "crm_record_draft", "strict", true,
      "schema", Map.of("type", "object", "additionalProperties", false, "properties", properties, "required", fields));
    String result = callResult(user, "record_draft", "Extract only details that the user explicitly supplied for a new " + kind + " record. "
      + "Never invent a name, phone, email, domain, company, job title or industry. Use an empty string for every missing field. "
      + "For a contact, split a supplied full name into firstName and lastName only when both parts are clear. "
      + "For a company, use name, domain and industry. User request: " + limit(message, 2000), false, format).text();
    try { return json.readValue(result, RecordDraft.class); }
    catch (RuntimeException error) { throw new ApiException(ErrorCode.INVALID_STATE, "The assistant could not prepare this record. Fill the card manually."); }
  }

  public Status status() {
    AiKeyService.KeyStatus key = keys.status();
    return new Status(key.configured(), key.keyName(), key.configured() ? "AI is available." : "AI API key has not been set.");
  }

  public Answer assist(CurrentUser user, String message) {
    String request = limit(message, 2000);
    String context = snapshot(user);
    boolean web = researchIntent(request);
    ProviderAnswer result = callResult(user, "assist", "You are the Nexus CRM assistant. Answer only about the CRM, supplied live CRM facts, and public company or market research. "
      + "Search the public web for research questions. Give a concise market assessment with competitors, demand, risks and evidence when asked. "
      + "Do not invent records, market figures or expose secrets. If the facts are insufficient, say what is missing. "
      + PROSPECTING
      + "Return ONLY JSON with keys answer and action. action must be one of none, leads, contacts, companies, deals, "
      + "calendar, communications, campaigns, products, quotes, automations, reports, create_lead, create_contact, "
      + "create_company, create_deal, create_task, create_activity, create_note. "
      + "Use create actions only when the user asks to add a record. The app will open a form for review; do not claim it was created. "
      + "Use workspace actions when the user clearly wants to open that area.\nLive CRM facts:\n" + context + "\nUser: " + request, web);
    try {
      @SuppressWarnings("unchecked") Map<String, Object> data = json.readValue(result.text(), Map.class);
      String answer = String.valueOf(data.getOrDefault("answer", ""));
      if (web && !result.sources().isEmpty()) answer += "\n\nPublic sources:\n" + String.join("\n", result.sources());
      String action = String.valueOf(data.getOrDefault("action", "none"));
      if (!Set.of("none","leads","contacts","companies","deals","calendar","communications","campaigns","products","quotes","automations","reports",
          "create_lead","create_contact","create_company","create_deal","create_task","create_activity","create_note").contains(action)) action = "none";
      return new Answer(answer, action);
    } catch (RuntimeException error) { return new Answer(result.text(), "none"); }
  }

  public String draftEmail(CurrentUser user, String context) {
    return call(user, "email", "Draft a concise professional customer email from the user's context. "
      + "Return only the body text. Do not invent names, facts, commitments, attachments or contact details. "
      + "Do not send it. Context: " + limit(context, 3000), false);
  }

  public Workflow workflow(CurrentUser user, String description) {
    Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false,
      "properties", Map.of(
        "trigger", Map.of("type", "string", "enum", List.of("Deal created", "Deal won", "Lead qualified", "none")),
        "actions", Map.of("type", "array", "items", Map.of("type", "string", "enum", List.of("Notify owner", "Create follow-up"))),
        "delay", Map.of("type", "string", "enum", List.of("none", "Wait 1 hour", "Wait 1 day")),
        "condition", Map.of("type", "string", "enum", List.of("none", "Deal is won", "Lead is qualified")),
        "message", Map.of("type", "string")),
      "required", List.of("trigger", "actions", "delay", "condition", "message"));
    Map<String, Object> format = Map.of("type", "json_schema", "name", "crm_workflow", "strict", true, "schema", schema);
    String output = callResult(user, "workflow", "Map this CRM workflow request to executable steps. "
      + "Triggers: Deal created, Deal won, Lead qualified. Actions: Notify owner, Create follow-up. "
      + "Include every requested supported action in order; a workflow can have more than one action. "
      + "Optional delays: none, Wait 1 hour, Wait 1 day. Optional conditions: none, Deal is won, Lead is qualified. "
      + "If the request needs an unsupported trigger or action, set trigger to none, actions to an empty list and explain in message. "
      + "Do not reject a request just because it contains two supported actions. Request: " + limit(description, 2000), false, format).text();
    try {
      @SuppressWarnings("unchecked") Map<String, Object> data = json.readValue(output, Map.class);
      String trigger = String.valueOf(data.getOrDefault("trigger", "none"));
      List<String> actions = data.get("actions") instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
      String delay = String.valueOf(data.getOrDefault("delay", "none"));
      String condition = String.valueOf(data.getOrDefault("condition", "none"));
      String message = String.valueOf(data.getOrDefault("message", ""));
      if (!Set.of("Deal created","Deal won","Lead qualified").contains(trigger)
          || actions.isEmpty() || actions.size() > 4 || actions.stream().anyMatch(action -> !Set.of("Notify owner","Create follow-up").contains(action))
          || !Set.of("none","Wait 1 hour","Wait 1 day").contains(delay)
          || !Set.of("none","Deal is won","Lead is qualified").contains(condition))
        return new Workflow(false, message.isBlank() ? "This request needs steps the CRM does not support yet." : message, List.of(), List.of());
      List<Map<String, Object>> nodes = new ArrayList<>();
      List<Map<String, Object>> edges = new ArrayList<>();
      add(nodes, edges, "Trigger", trigger);
      if (!"none".equals(condition)) add(nodes, edges, "Condition", condition);
      if (!"none".equals(delay)) add(nodes, edges, "Delay", delay);
      for (String action : actions) add(nodes, edges, "Action", action);
      return new Workflow(true, message, nodes, edges);
    } catch (RuntimeException error) { throw new ApiException(ErrorCode.INVALID_STATE, "The AI workflow response could not be used. Try describing it more simply."); }
  }

  public Research research(CurrentUser user, String kind, UUID id) {
    String subject = subject(user, kind, id);
    ProviderAnswer result = callResult(user, "research", "Research this real CRM record on the public internet using web search: " + subject + ". "
      + "Find the official organization website, a public logo image URL, and a publicly listed contact email if applicable. "
      + "Never infer or guess an email address. If uncertain return null. Include source URLs for every claim. "
      + ("company".equals(kind)
        ? "Also write summary: one paragraph of 90 to 130 words that helps a salesperson size up this organization. Say what it sells and to whom, "
          + "then give the figures that matter most: latest annual revenue and its growth, profit or margin, headcount, number of clients or "
          + "countries served, and large recent deals or strategic moves. State the fiscal year or date each figure applies to. Use only figures "
          + "you found in the sources and leave out any you could not find; never estimate. Plain prose only: no markdown, lists, links or citations. "
        : "Set summary to null. ")
      + "Return ONLY JSON with keys website, logoUrl, email, summary, sources (array of URL strings).", true);
    try {
      @SuppressWarnings("unchecked") Map<String, Object> data = json.readValue(result.text(), Map.class);
      List<String> sources = result.sources();
      Research research = new Research(safeUrl(data.get("website")), safeUrl(data.get("logoUrl")), safeEmail(data.get("email")),
        "company".equals(kind) ? summary(data.get("summary")) : null, sources, Instant.now());
      // Nothing is kept when the search cited no source: the answer would be unverified.
      if (sources.isEmpty()) research = new Research(null, null, null, null, List.of(), Instant.now());
      jdbc.sql("insert into ai_research (kind,record_id,result) values (:kind,:id,cast(:result as jsonb)) "
          + "on conflict (kind,record_id) do update set result=excluded.result,researched_at=now()")
        .param("kind", kind).param("id", id).param("result", json.writeValueAsString(research)).update();
      return research;
    } catch (RuntimeException error) { throw new ApiException(ErrorCode.INVALID_STATE, "Research could not be verified. Try again later."); }
  }

  public Research savedResearch(CurrentUser user, String kind, UUID id) {
    subject(user, kind, id);
    return jdbc.sql("select result::text from ai_research where kind=:kind and record_id=:id")
      .param("kind", kind).param("id", id).query(String.class).optional()
      .map(value -> json.readValue(value, Research.class)).orElse(null);
  }

  private String subject(CurrentUser user, String kind, UUID id) {
    if ("company".equals(kind)) {
      var company = jdbc.sql("select name,domain,owner_id from company where id=:id and deleted_at is null")
        .param("id", id).query((rs,n) -> Map.of("name", rs.getString("name"), "domain", rs.getString("domain") == null ? "" : rs.getString("domain"), "owner", rs.getObject("owner_id", UUID.class))).optional()
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Company not found."));
      requireVisible(user, (UUID) company.get("owner"));
      return "Organization " + company.get("name") + ", domain " + company.get("domain");
    }
    if ("contact".equals(kind)) {
      var contact = jdbc.sql("select first_name,last_name,company_id,owner_id from contact where id=:id and deleted_at is null")
        .param("id", id).query((rs,n) -> Map.of("name", rs.getString("first_name") + " " + rs.getString("last_name"), "owner", rs.getObject("owner_id", UUID.class), "company", rs.getObject("company_id", UUID.class) == null ? "" : rs.getObject("company_id", UUID.class).toString())).optional()
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Contact not found."));
      requireVisible(user, (UUID) contact.get("owner"));
      String company = "";
      if (!"".equals(contact.get("company"))) company = jdbc.sql("select name from company where id=:id")
        .param("id", UUID.fromString((String) contact.get("company"))).query(String.class).optional().orElse("");
      return "Contact " + contact.get("name") + " at " + company;
    }
    throw new ApiException(ErrorCode.NOT_FOUND, "Research target not found.");
  }

  private void requireVisible(CurrentUser user, UUID owner) {
    if (users.visibleOwnerIds(user).map(ids -> !ids.contains(owner)).orElse(false))
      throw new ApiException(ErrorCode.NOT_FOUND, "Record not found.");
  }

  private String snapshot(CurrentUser user) {
    var visible = users.visibleOwnerIds(user);
    String scope = visible.isPresent() ? "owner_id in (:ids)" : "true";
    List<UUID> ids = visible.map(s -> s.isEmpty() ? List.of(new UUID(0,0)) : List.copyOf(s)).orElse(List.of());
    StringBuilder out = new StringBuilder();
    for (String table : List.of("lead", "contact", "company", "deal")) {
      var query = jdbc.sql("select count(*) from " + table + " where deleted_at is null and " + scope);
      if (visible.isPresent()) query = query.param("ids", ids);
      out.append(table).append(" count: ").append(query.query(Long.class).single()).append(". ");
      // One line per record with what a salesperson would ask about. No emails or phone numbers leave the CRM.
      String line = switch (table) {
        case "lead" -> "concat_ws(' ', first_name, last_name) || coalesce(' at ' || company_name, '') || ' (' || status || ', source ' || source"
          + " || coalesce(', ' || job_title, '') || coalesce(', value ' || trunc(estimated_value) || ' ' || currency, '') || ')'";
        case "deal" -> "name || ' (' || stage_code || ', ' || trunc(amount) || ' ' || currency || coalesce(', closes ' || expected_close_date, '') || ')'";
        case "company" -> "name || coalesce(' (' || industry || ')', '')";
        default -> "concat_ws(' ', first_name, last_name) || coalesce(' (' || job_title || ')', '')";
      };
      var recent = jdbc.sql("select " + line + " from " + table
          + " where deleted_at is null and " + scope + " order by updated_at desc limit " + ("contact".equals(table) ? 20 : 40));
      if (visible.isPresent()) recent = recent.param("ids", ids);
      out.append("Recent ").append(table).append(": ").append(recent.query(String.class).list()).append(". ");
    }
    for (String kind : List.of("communications", "campaigns", "automations", "products", "quotes")) {
      String moduleScope = "products".equals(kind) ? "true" : scope;
      var count = jdbc.sql("select count(*) from crm_module_record where kind=:kind and deleted_at is null and " + moduleScope)
          .param("kind", kind);
      if (visible.isPresent() && !"products".equals(kind)) count = count.param("ids", ids);
      out.append(kind).append(" count: ").append(count.query(Long.class).single()).append(". ");
    }
    return out.toString();
  }

  private void add(List<Map<String, Object>> nodes, List<Map<String, Object>> edges, String type, String label) {
    String id = UUID.randomUUID().toString();
    if (!nodes.isEmpty()) edges.add(Map.of("id", UUID.randomUUID().toString(), "source", nodes.getLast().get("id"), "target", id,
      "label", "Condition".equals(nodes.getLast().get("type")) ? "Yes" : ""));
    nodes.add(Map.of("id", id, "type", type, "label", label, "x", 80 + nodes.size() * 250, "y", 190));
  }

  private record ProviderAnswer(String text, List<String> sources) { }

  private String call(CurrentUser user, String kind, String prompt, boolean web) {
    return callResult(user, kind, prompt, web).text();
  }

  private ProviderAnswer callResult(CurrentUser user, String kind, String prompt, boolean web) {
    return callResult(user, kind, prompt, web, null);
  }

  private ProviderAnswer callResult(CurrentUser user, String kind, String prompt, boolean web, Map<String, Object> format) {
    long recent = jdbc.sql("select count(*) from ai_request where user_id=:user and created_at>now()-interval '1 hour'")
      .param("user", user.id()).query(Long.class).single();
    if (recent >= 30) throw new ApiException(ErrorCode.INVALID_STATE, "AI request limit reached for this hour. Try later.");
    AiKeyService.Credentials credential = keys.active();
    if (credential.key().isBlank()) throw new ApiException(ErrorCode.INVALID_STATE, "AI API key has not been set.");
    Map<String, Object> request = new java.util.HashMap<>(web
      ? Map.of("model", model, "input", prompt, "tools", List.of(Map.of("type", "web_search")), "tool_choice", "required", "include", List.of("web_search_call.action.sources"), "store", false)
      : Map.of("model", model, "input", prompt, "store", false));
    if (format != null) request.put("text", Map.of("format", format));
    try {
      HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
        .timeout(Duration.ofSeconds(90)).header("Authorization", "Bearer " + credential.key())
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request))).build();
      HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        if (response.statusCode() == 429 && response.body().contains("insufficient_quota")) {
          alert(credential.name(), "billing_quota", "AI key " + credential.name() + " has reached its billing quota. Please recharge it.");
          throw new ApiException(ErrorCode.INVALID_STATE, "AI billing quota was reached. An admin has been alerted.");
        }
        throw new ApiException(ErrorCode.INVALID_STATE, response.statusCode() == 429 ? "AI is busy. Try again shortly." : "AI is unavailable. Try again later.");
      }
      @SuppressWarnings("unchecked") Map<String, Object> data = json.readValue(response.body(), Map.class);
      String text = outputText(data);
      if (text.isBlank()) throw new ApiException(ErrorCode.INVALID_STATE, "AI returned no answer. Try again.");
      @SuppressWarnings("unchecked") Map<String, Object> usage = data.get("usage") instanceof Map<?,?> m ? (Map<String, Object>) m : Map.of();
      jdbc.sql("insert into ai_request (id,user_id,kind,input_tokens,output_tokens) values (:id,:user,:kind,:input,:output)")
        .param("id", UUID.randomUUID()).param("user", user.id()).param("kind", kind)
        .param("input", number(usage.get("input_tokens"))).param("output", number(usage.get("output_tokens"))).update();
      if (monthlyRequestLimit > 0) {
        long monthCount = jdbc.sql("select count(*) from ai_request where created_at>=date_trunc('month',now())").query(Long.class).single();
        if (monthCount >= Math.ceil(monthlyRequestLimit * .8)) alert(credential.name(), "request_budget", "AI key " + credential.name() + " has used at least 80% of the configured monthly request allowance.");
      }
      return new ProviderAnswer(text, web ? citedUrls(data) : List.of());
    } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new ApiException(ErrorCode.INVALID_STATE, "AI request was interrupted."); }
    catch (java.io.IOException error) { throw new ApiException(ErrorCode.INVALID_STATE, "AI is unreachable. Try again later."); }
  }

  public SseEmitter streamAssist(CurrentUser user, String message) {
    String request = limit(message, 2000);
    long recent = jdbc.sql("select count(*) from ai_request where user_id=:user and created_at>now()-interval '1 hour'")
      .param("user", user.id()).query(Long.class).single();
    if (recent >= 30) throw new ApiException(ErrorCode.INVALID_STATE, "AI request limit reached for this hour. Try later.");
    AiKeyService.Credentials credential = keys.active();
    if (credential.key().isBlank()) throw new ApiException(ErrorCode.INVALID_STATE, "AI API key has not been set.");
    boolean web = researchIntent(request);
    String prompt = "You are the Nexus CRM assistant. Answer only questions within the CRM and business research boundaries. "
      + "Use the supplied live CRM facts for private records and never invent a record. "
      + "For public company or market questions, search the web and assess market position, demand, competitors and risks using sourced facts. "
      + "State uncertainty when evidence is thin; never guess private contact details or precise market numbers. "
      + PROSPECTING
      + "Do not claim you created or changed records; a user must review a form first. Write clear plain text.\nLive CRM facts:\n"
      + snapshot(user) + "\nUser: " + request;
    UUID requestId = UUID.randomUUID();
    jdbc.sql("insert into ai_request (id,user_id,kind) values (:id,:user,:kind)")
      .param("id", requestId).param("user", user.id()).param("kind", web ? "market_stream" : "assist_stream").update();
    SseEmitter emitter = new SseEmitter(120_000L);
    Thread.ofVirtual().name("nexus-ai-stream").start(() -> streamProvider(emitter, credential, prompt, web, request, requestId));
    return emitter;
  }

  @SuppressWarnings("unchecked")
  private void streamProvider(SseEmitter emitter, AiKeyService.Credentials credential, String prompt, boolean web,
      String request, UUID requestId) {
    try {
      sendEvent(emitter, "start", Map.of("researching", web));
      Map<String, Object> body = web
        ? Map.of("model", model, "input", prompt, "tools", List.of(Map.of("type", "web_search")), "tool_choice", "required",
          "include", List.of("web_search_call.action.sources"), "reasoning", Map.of("effort", "low"), "max_output_tokens", 2500,
          "store", false, "stream", true)
        : Map.of("model", model, "input", prompt, "reasoning", Map.of("effort", "low"), "max_output_tokens", 1500,
          "store", false, "stream", true);
      HttpRequest provider = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
        .timeout(Duration.ofSeconds(100)).header("Authorization", "Bearer " + credential.key())
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
      HttpResponse<Stream<String>> response = http.send(provider, HttpResponse.BodyHandlers.ofLines());
      try (Stream<String> lines = response.body()) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          String error = lines.limit(20).reduce("", (a, b) -> a + b);
          if (response.statusCode() == 429 && error.contains("insufficient_quota")) {
            alert(credential.name(), "billing_quota", "AI key " + credential.name() + " has reached its billing quota. Please recharge it.");
            sendEvent(emitter, "error", Map.of("message", "AI billing quota was reached. An admin has been alerted."));
          } else sendEvent(emitter, "error", Map.of("message", "AI is unavailable. Try again later."));
          emitter.complete();
          return;
        }
        boolean completed = false;
        for (String line : (Iterable<String>) lines::iterator) {
          if (!line.startsWith("data: ")) continue;
          String payload = line.substring(6);
          if ("[DONE]".equals(payload)) break;
          Map<String, Object> event = json.readValue(payload, Map.class);
          String type = String.valueOf(event.get("type"));
          if ("response.output_text.delta".equals(type)) {
            String delta = String.valueOf(event.getOrDefault("delta", ""));
            if (!delta.isEmpty()) sendEvent(emitter, "delta", Map.of("text", delta));
          } else if ("response.completed".equals(type) && event.get("response") instanceof Map<?,?> finished) {
            Map<String, Object> result = (Map<String, Object>) finished;
            Map<String, Object> usage = result.get("usage") instanceof Map<?,?> values ? (Map<String, Object>) values : Map.of();
            jdbc.sql("update ai_request set input_tokens=:input,output_tokens=:output where id=:id")
              .param("input", number(usage.get("input_tokens"))).param("output", number(usage.get("output_tokens")))
              .param("id", requestId).update();
            if (monthlyRequestLimit > 0) {
              long monthCount = jdbc.sql("select count(*) from ai_request where created_at>=date_trunc('month',now())").query(Long.class).single();
              if (monthCount >= Math.ceil(monthlyRequestLimit * .8))
                alert(credential.name(), "request_budget", "AI key " + credential.name() + " has used at least 80% of the configured monthly request allowance.");
            }
            List<String> sources = web ? citedUrls(result) : List.of();
            if (!sources.isEmpty()) sendEvent(emitter, "sources", Map.of("urls", sources));
            sendEvent(emitter, "done", Map.of("action", suggestedAction(request)));
            completed = true;
          } else if ("error".equals(type) || "response.failed".equals(type)) {
            sendEvent(emitter, "error", Map.of("message", "AI could not complete the answer. Try again."));
            break;
          }
        }
        if (!completed) sendEvent(emitter, "error", Map.of("message", "AI stopped before finishing. Try again."));
      }
      emitter.complete();
    } catch (Exception error) {
      try { sendEvent(emitter, "error", Map.of("message", "AI stream was interrupted. Try again.")); }
      catch (Exception ignored) { }
      emitter.complete();
    }
  }

  private void sendEvent(SseEmitter emitter, String name, Object data) throws java.io.IOException {
    emitter.send(SseEmitter.event().name(name).data(data));
  }

  private String suggestedAction(String request) {
    String lower = request.toLowerCase(java.util.Locale.ROOT);
    boolean create = lower.matches("(?s).*(add|create|new)\\s+.*");
    if (create) for (String kind : List.of("lead", "contact", "company", "deal", "task", "activity", "note"))
      if (lower.contains(kind)) return "create_" + kind;
    if (lower.matches("(?s).*(open|show|go to)\\s+.*")) {
      for (String kind : List.of("leads", "contacts", "companies", "deals", "calendar", "communications", "campaigns", "products", "quotes", "automations", "reports"))
        if (lower.contains(kind)) return kind;
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
    if (!(data.get("output") instanceof List<?> output)) return "";
    for (Object item : output) if (item instanceof Map<?,?> message && message.get("content") instanceof List<?> content)
      for (Object part : content) if (part instanceof Map<?,?> entry && "output_text".equals(entry.get("type")))
        text.append(String.valueOf(entry.get("text")));
    return text.toString();
  }

  private List<String> citedUrls(Map<String, Object> data) {
    if (!(data.get("output") instanceof List<?> output)) return List.of();
    List<String> urls = new ArrayList<>();
    for (Object item : output) if (item instanceof Map<?,?> message && message.get("content") instanceof List<?> content)
      for (Object part : content) if (part instanceof Map<?,?> entry && entry.get("annotations") instanceof List<?> annotations)
        for (Object annotation : annotations) if (annotation instanceof Map<?,?> cite && "url_citation".equals(cite.get("type"))) {
          String url = String.valueOf(cite.get("url"));
          if (https(url) && !urls.contains(url) && urls.size() < 8) urls.add(url);
        }
    for (Object item : output) if (item instanceof Map<?,?> call && "web_search_call".equals(call.get("type"))
        && call.get("action") instanceof Map<?,?> action && action.get("sources") instanceof List<?> sources)
      for (Object source : sources) if (source instanceof Map<?,?> entry) {
        String url = String.valueOf(entry.get("url"));
        if (https(url) && !urls.contains(url) && urls.size() < 8) urls.add(url);
      }
    return urls;
  }

  private void alert(String keyName, String type, String message) {
    String period = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC).format(Instant.now());
    int created = jdbc.sql("insert into ai_alert (id,key_name,alert_type,period) values (:id,:name,:type,:period) on conflict do nothing")
      .param("id", UUID.randomUUID()).param("name", keyName).param("type", type).param("period", period).update();
    if (created == 0) return;
    List<Map<String,Object>> admins = jdbc.sql("select id,email from app_user where role='ADMIN' and status='ACTIVE'")
      .query((rs,n) -> Map.<String,Object>of("id", rs.getObject("id", UUID.class), "email", rs.getString("email"))).list();
    for (var admin : admins) {
      UUID id = (UUID) admin.get("id");
      notifications.send(id, "AI_QUOTA", message, "/settings");
      try { mail.sendSystemAlert(id, (String) admin.get("email"), "Nexus CRM AI quota alert", message); }
      catch (RuntimeException ignored) { /* In-app alert remains available when mailbox is not connected. */ }
    }
  }

  private String safeUrl(Object value) { String url = String.valueOf(value); return https(url) && url.length() <= 1000 ? url : null; }
  private boolean https(String url) { try { URI uri = URI.create(url); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null; } catch (RuntimeException e) { return false; } }
  /** The summary as plain text: links and citation marks removed, at most 1200 characters. */
  private String summary(Object value) {
    if (!(value instanceof String raw)) return null;
    String text = raw.replaceAll("\\(?\\[([^\\]]*)\\]\\([^)]*\\)\\)?", "").replaceAll("https?://\\S+", "").replaceAll("\\s+", " ").replaceAll(" ([.,;:])", "$1").strip();
    if (text.isEmpty()) return null;
    return text.length() <= 1200 ? text : text.substring(0, 1200).strip() + "…";
  }
  private String safeEmail(Object value) { String email = String.valueOf(value); return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") && email.length() <= 254 ? email : null; }
  private String limit(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Enter a shorter, nonempty request.");
    return value.strip();
  }
  private int number(Object value) { return value instanceof Number n ? n.intValue() : 0; }
}
