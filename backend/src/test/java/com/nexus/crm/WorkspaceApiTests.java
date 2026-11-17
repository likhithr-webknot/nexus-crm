package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

class WorkspaceApiTests extends IntegrationTest {
  private Session admin;
  @Autowired private JdbcClient jdbc;
  @BeforeEach void signIn() { admin = signedIn(Role.ADMIN); }
  private MvcTestResult send(MockMvcRequestBuilder request, String body) {
    var builder = withCsrf(request).cookie(admin.accessToken());
    return body == null ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
  }
  private String json(MvcTestResult result) { return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8); }
  private String id(MvcTestResult result) { return json(result).replaceAll("^.*?\\\"id\\\":\\\"([^\\\"]+)\\\".*$", "$1"); }
  private MvcTestResult sendAs(Session session, MockMvcRequestBuilder request, String body) {
    return withCsrf(request).cookie(session.accessToken()).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
  }
  private MvcTestResult get(String path) { return mvc.get().uri(path).cookie(admin.accessToken()).exchange(); }

  @Test void productsAndQuotesUsePersistedServerTotals() {
    var product = send(mvc.post().uri("/api/v1/workspaces/products"),
      "{\"name\":\"Service plan\",\"status\":\"Active\",\"amount\":200}");
    assertThat(product).hasStatus(201);
    String productId = id(product);
    assertThat(json(get("/api/v1/workspaces/products"))).contains(productId);
    var quote = send(mvc.post().uri("/api/v1/workspaces/quotes"),
      "{\"name\":\"Customer quote\",\"status\":\"Draft\",\"amount\":9999,\"lineItems\":[{\"id\":\"line1\",\"productId\":\"" + productId + "\",\"name\":\"Service plan\",\"quantity\":1,\"unitPrice\":200,\"discountPct\":10,\"taxPct\":20}]}");
    assertThat(quote).hasStatus(201);
    assertThat(json(quote)).contains("\"amount\":216.00");
    assertThat(json(get("/api/v1/workspaces/quotes/" + id(quote)))).contains("Service plan");
  }

  @Test void draftsCannotPretendToBeSentAndMailNeedsAConnection() {
    var draft = send(mvc.post().uri("/api/v1/workspaces/communications"),
      "{\"name\":\"Email subject\",\"status\":\"Draft\",\"recipient\":\"customer@example.com\",\"description\":\"Hello\"}");
    assertThat(draft).hasStatus(201);
    assertThat(send(mvc.post().uri("/api/v1/workspaces/communications"),
      "{\"name\":\"False sent\",\"status\":\"Sent\"}")).hasStatus(422);
    assertThat(send(mvc.post().uri("/api/v1/mail/send/" + id(draft)), null)).hasStatus(409);
    assertThat(json(get("/api/v1/workspaces/communications/" + id(draft)))).contains("\"status\":\"Draft\"");
  }

  @Test void publishedWorkflowExecutesWhenDealIsCreated() {
    var workflow = send(mvc.post().uri("/api/v1/workspaces/automations"),
      "{\"name\":\"New deal follow-up\",\"status\":\"Draft\"}");
    assertThat(workflow).hasStatus(201);
    String workflowId = id(workflow);
    var published = send(mvc.patch().uri("/api/v1/workspaces/automations/" + workflowId),
      "{\"name\":\"New deal follow-up\",\"status\":\"Published\",\"version\":0,\"flowNodes\":[{\"id\":\"t\",\"type\":\"Trigger\",\"label\":\"Deal created\"},{\"id\":\"a\",\"type\":\"Action\",\"label\":\"Create follow-up\"}],\"flowEdges\":[{\"id\":\"e\",\"source\":\"t\",\"target\":\"a\"}]}");
    assertThat(published).hasStatusOk();
    var company = send(mvc.post().uri("/api/v1/companies"), "{\"name\":\"Workflow Co " + UUID.randomUUID() + "\"}");
    var deal = send(mvc.post().uri("/api/v1/deals"),
      "{\"name\":\"Workflow deal\",\"companyId\":\"" + id(company) + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}");
    assertThat(deal).hasStatus(201);
    // Workflows run on their own virtual thread after the request has finished, so wait for the result.
    await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
      assertThat(json(get("/api/v1/automations/" + workflowId + "/runs"))).contains("COMPLETED", "deal-created:" + id(deal)));
    assertThat(json(get("/api/v1/follow-ups?dealId=" + id(deal)))).contains("Follow up: Workflow deal");
  }

  @Test void aBrokenWorkflowEndsFailedNotRunningAndNeverBreaksTheRequest() {
    var workflow = send(mvc.post().uri("/api/v1/workspaces/automations"), "{\"name\":\"Broken flow\",\"status\":\"Draft\"}");
    String workflowId = id(workflow);
    var published = send(mvc.patch().uri("/api/v1/workspaces/automations/" + workflowId),
      "{\"name\":\"Broken flow\",\"status\":\"Published\",\"version\":0,\"flowNodes\":[{\"id\":\"t\",\"type\":\"Trigger\",\"label\":\"Deal created\"},{\"id\":\"a\",\"type\":\"Action\",\"label\":\"Create follow-up\"}],\"flowEdges\":[{\"id\":\"e\",\"source\":\"t\",\"target\":\"a\"}]}");
    assertThat(published).hasStatusOk();
    // Damage the saved definition behind the API's back, as a bug or a bad migration could.
    jdbc.sql("update crm_module_record set details = jsonb_set(details, '{flowNodes}', '[{\"id\":\"t\",\"type\":\"Trigger\",\"label\":\"Deal created\"},{\"id\":\"a\",\"type\":\"Action\",\"label\":\"Not a real action\"}]') where id = :id")
      .param("id", UUID.fromString(workflowId)).update();
    var company = send(mvc.post().uri("/api/v1/companies"), "{\"name\":\"Broken Co " + UUID.randomUUID() + "\"}");

    var deal = send(mvc.post().uri("/api/v1/deals"),
      "{\"name\":\"Broken deal\",\"companyId\":\"" + id(company) + "\",\"amount\":{\"amount\":\"10\",\"currency\":\"INR\"}}");

    assertThat(deal).hasStatus(201);
    await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
      assertThat(json(get("/api/v1/automations/" + workflowId + "/runs"))).contains("\"status\":\"FAILED\"").doesNotContain("RUNNING"));
  }

  @Test void roleRulesComeFromAnnotationsOnTheEndpoints() {
    Session rep = signedIn(Role.REP);
    Session manager = signedIn(Role.MANAGER);
    String product = "{\"name\":\"Rule product\",\"status\":\"Active\",\"amount\":1}";
    String automation = "{\"name\":\"Rule flow\",\"status\":\"Draft\"}";
    String campaign = "{\"name\":\"Rule campaign\",\"status\":\"Draft\"}";
    String quote = "{\"name\":\"Rule quote\",\"status\":\"Draft\"}";

    assertThat(sendAs(rep, mvc.post().uri("/api/v1/workspaces/products"), product)).hasStatus(403);
    assertThat(sendAs(rep, mvc.post().uri("/api/v1/workspaces/automations"), automation)).hasStatus(403);
    assertThat(sendAs(rep, mvc.post().uri("/api/v1/workspaces/campaigns"), campaign)).hasStatus(403);
    assertThat(sendAs(rep, mvc.post().uri("/api/v1/workspaces/quotes"), quote)).hasStatus(201);
    assertThat(sendAs(manager, mvc.post().uri("/api/v1/workspaces/campaigns"), campaign)).hasStatus(201);
    assertThat(sendAs(manager, mvc.post().uri("/api/v1/workspaces/products"), product)).hasStatus(403);
    assertThat(send(mvc.post().uri("/api/v1/workspaces/products"), product)).hasStatus(201);
    // Reading stays open to everyone who is signed in.
    assertThat(mvc.get().uri("/api/v1/workspaces/products").cookie(rep.accessToken())).hasStatusOk();
    // Only managers and admins can give a lead to someone else.
    assertThat(sendAs(rep, mvc.post().uri("/api/v1/leads/" + UUID.randomUUID() + "/assign"), "{}")).hasStatus(403);
  }

  @Test void copiesAreSavedAndEveryAddressIsChecked() {
    String base = "{\"name\":\"Copies\",\"status\":\"Draft\",\"recipient\":\"to@example.com\"";
    var saved = send(mvc.post().uri("/api/v1/workspaces/communications"),
      base + ",\"cc\":[\"Cc1@example.com\",\"cc1@example.com\",\"cc2@example.com\"],\"bcc\":[\"hidden@example.com\"]}");
    assertThat(saved).hasStatus(201);
    // Lower-cased, and the repeat is dropped.
    assertThat(json(saved)).contains("\"cc\":[\"cc1@example.com\",\"cc2@example.com\"]").contains("\"bcc\":[\"hidden@example.com\"]");

    assertThat(send(mvc.post().uri("/api/v1/workspaces/communications"), base.replace("to@example.com", "a@example.com,b@example.com") + "}")).hasStatus(422);
    assertThat(send(mvc.post().uri("/api/v1/workspaces/communications"), base + ",\"cc\":[\"not an email\"]}")).hasStatus(422);
    assertThat(send(mvc.post().uri("/api/v1/workspaces/communications"), base + ",\"cc\":[\"x@example.com\"],\"bcc\":[\"x@example.com\"]}")).hasStatus(422);
    assertThat(send(mvc.post().uri("/api/v1/workspaces/communications"), base + ",\"cc\":[\"to@example.com\"]}")).hasStatus(422);
    String twentyOne = java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(i -> "\"c" + i + "@example.com\"").collect(java.util.stream.Collectors.joining(","));
    assertThat(send(mvc.post().uri("/api/v1/workspaces/communications"), base + ",\"bcc\":[" + twentyOne + "]}")).hasStatus(422);
  }

  @Test void aWorkspaceIsPagedAndFilteredOnTheServer() {
    String tag = "Pg" + UUID.randomUUID().toString().substring(0, 8);
    for (int i = 1; i <= 3; i++)
      assertThat(send(mvc.post().uri("/api/v1/workspaces/products"), "{\"name\":\"" + tag + " item " + i + "\",\"status\":\"Active\",\"amount\":1}")).hasStatus(201);

    String first = json(get("/api/v1/workspaces/products?q=" + tag.toLowerCase() + "&size=2&sort=name"));
    assertThat(first).contains("\"total\":3", "\"size\":2", "\"page\":1").contains(tag + " item 1").contains(tag + " item 2").doesNotContain(tag + " item 3");
    String second = json(get("/api/v1/workspaces/products?q=" + tag + "&size=2&page=2&sort=name"));
    assertThat(second).contains(tag + " item 3").doesNotContain(tag + " item 1");
    assertThat(json(get("/api/v1/workspaces/products?q=" + tag + "&status=Archived"))).contains("\"total\":0");
  }

  @Test void savedReportIsServerPersisted() {
    var report = send(mvc.post().uri("/api/v1/saved-reports"), "{\"name\":\"My view\",\"dataset\":\"team\",\"chart\":\"bar\"}");
    assertThat(report).hasStatus(201);
    assertThat(json(get("/api/v1/saved-reports"))).contains(id(report));
    assertThat(send(mvc.delete().uri("/api/v1/saved-reports/" + id(report)), null)).hasStatus(204);
    assertThat(json(get("/api/v1/saved-reports"))).doesNotContain(id(report));
  }

  @Test void archivedWorkspaceRecordAppearsInRecycleBinAndRestores() {
    var product = send(mvc.post().uri("/api/v1/workspaces/products"),
      "{\"name\":\"Restorable product\",\"status\":\"Active\",\"amount\":50}");
    String productId = id(product);
    assertThat(send(mvc.delete().uri("/api/v1/workspaces/products/" + productId), null)).hasStatus(204);
    assertThat(json(get("/api/v1/trash?type=PRODUCT"))).contains(productId, "Restorable product");
    assertThat(send(mvc.post().uri("/api/v1/trash/PRODUCT/" + productId + "/restore"), null)).hasStatus(204);
    assertThat(json(get("/api/v1/workspaces/products/" + productId))).contains("Restorable product");
  }
}
