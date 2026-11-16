package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

class WorkspaceApiTests extends IntegrationTest {
  private Session admin;
  @BeforeEach void signIn() { admin = signedIn(Role.ADMIN); }
  private MvcTestResult send(MockMvcRequestBuilder request, String body) {
    var builder = withCsrf(request).cookie(admin.accessToken());
    return body == null ? builder.exchange() : builder.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
  }
  private String json(MvcTestResult result) { return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8); }
  private String id(MvcTestResult result) { return json(result).replaceAll("^.*?\\\"id\\\":\\\"([^\\\"]+)\\\".*$", "$1"); }
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
    assertThat(json(get("/api/v1/automations/" + workflowId + "/runs"))).contains("COMPLETED", "deal-created:" + id(deal));
    assertThat(json(get("/api/v1/follow-ups?dealId=" + id(deal)))).contains("Follow up: Workflow deal");
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
