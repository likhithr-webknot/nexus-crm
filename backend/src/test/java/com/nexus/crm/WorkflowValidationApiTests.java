package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

/** The rules a workflow must meet before it can be published. A draft may hold any shape. */
class WorkflowValidationApiTests extends IntegrationTest {

	private static final String TRIGGER = node("t", "Trigger", "Deal created");

	private static final String ACTION = node("a", "Action", "Create follow-up");

	private Session admin;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void signInAsAdmin() {
		this.admin = signedIn(Role.ADMIN);
	}

	/**
	 * A published workflow keeps reacting to every deal created afterwards, in every other test. These tests only check
	 * the rules, so they remove what they publish.
	 */
	@AfterEach
	void removeWorkflows() {
		this.jdbc.sql("delete from automation_run where workflow_id in (select id from crm_module_record where kind = 'automations' and name = 'Flow')")
			.update();
		this.jdbc.sql("delete from crm_module_record where kind = 'automations' and name = 'Flow'").update();
	}

	private static String node(String id, String type, String label) {
		return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"label\":\"" + label + "\"}";
	}

	private static String edge(String source, String target) {
		return "{\"id\":\"" + source + target + "\",\"source\":\"" + source + "\",\"target\":\"" + target + "\"}";
	}

	private static String edge(String source, String target, String label) {
		return "{\"id\":\"" + source + target + label + "\",\"source\":\"" + source + "\",\"target\":\"" + target
				+ "\",\"label\":\"" + label + "\"}";
	}

	private static String list(String... items) {
		return "[" + String.join(",", items) + "]";
	}

	/** Creates a draft workflow, then tries to publish it with the given steps and connections. */
	private MvcTestResult publish(String nodes, String edges) {
		MvcTestResult created = send(this.mvc.post().uri("/api/v1/workspaces/automations"),
				"{\"name\":\"Flow\",\"status\":\"Draft\"}");
		String id = json(created).replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1");
		return send(this.mvc.patch().uri("/api/v1/workspaces/automations/" + id),
				"{\"name\":\"Flow\",\"status\":\"Published\",\"version\":0,\"flowNodes\":" + nodes + ",\"flowEdges\":" + edges + "}");
	}

	private MvcTestResult send(org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder request, String body) {
		return withCsrf(request).cookie(this.admin.accessToken()).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private String json(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private void assertRefused(MvcTestResult result, String message) {
		assertThat(result).hasStatus(422);
		assertThat(json(result)).contains(message);
	}

	@Test
	void aTriggerThenAnActionIsAccepted() {
		assertThat(publish(list(TRIGGER, ACTION), list(edge("t", "a")))).hasStatusOk();
	}

	@Test
	void aConditionWithYesAndNoPathsIsAccepted() {
		String nodes = list(TRIGGER, node("c", "Condition", "Deal is won"), ACTION, node("b", "Action", "Notify owner"));
		String edges = list(edge("t", "c"), edge("c", "a", "Yes"), edge("c", "b", "No"));

		assertThat(publish(nodes, edges)).hasStatusOk();
	}

	@Test
	void aDraftMayHoldAnyShape() {
		MvcTestResult created = send(this.mvc.post().uri("/api/v1/workspaces/automations"), "{\"name\":\"Draft flow\",\"status\":\"Draft\"}");
		String id = json(created).replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1");

		MvcTestResult saved = send(this.mvc.patch().uri("/api/v1/workspaces/automations/" + id),
				"{\"name\":\"Draft flow\",\"status\":\"Draft\",\"version\":0,\"flowNodes\":" + list(ACTION) + ",\"flowEdges\":[]}");

		assertThat(saved).hasStatusOk();
	}

	@Test
	void exactlyOneTriggerIsNeeded() {
		assertRefused(publish(list(ACTION), "[]"), "exactly one trigger");
		assertRefused(publish(list(TRIGGER, node("t2", "Trigger", "Deal won"), ACTION), list(edge("t", "a"))), "exactly one trigger");
	}

	@Test
	void anActionIsNeeded() {
		assertRefused(publish(list(TRIGGER), "[]"), "Add an action before publishing.");
	}

	@Test
	void everyStepMustBeOfAKnownTypeWithAUniqueShortId() {
		assertRefused(publish(list(TRIGGER, node("a", "Teleport", "Create follow-up")), "[]"), "Workflow has an invalid step.");
		assertRefused(publish(list(TRIGGER, ACTION, ACTION), list(edge("t", "a"))), "Workflow has an invalid step.");
		assertRefused(publish(list(TRIGGER, node("x".repeat(101), "Action", "Create follow-up")), "[]"), "Workflow has an invalid step.");
	}

	@Test
	void everyStepMustUseASupportedLabel() {
		assertRefused(publish(list(node("t", "Trigger", "Moon landing"), ACTION), list(edge("t", "a"))), "Choose a supported workflow step.");
		assertRefused(publish(list(TRIGGER, node("a", "Action", "Send a rocket")), list(edge("t", "a"))), "Choose a supported workflow step.");
		assertRefused(publish(list(TRIGGER, ACTION, node("d", "Delay", "Wait 3 weeks")), list(edge("t", "a"), edge("a", "d"))),
				"Choose a supported workflow step.");
	}

	@Test
	void aConnectionMustJoinTwoDifferentExistingSteps() {
		assertRefused(publish(list(TRIGGER, ACTION), list(edge("t", "a"), edge("a", "ghost"))), "Workflow has an invalid connection.");
		assertRefused(publish(list(TRIGGER, ACTION), list(edge("t", "a"), edge("a", "a"))), "Workflow has an invalid connection.");
	}

	@Test
	void conditionPathsMustBeLabelledYesOrNo() {
		String nodes = list(TRIGGER, node("c", "Condition", "Deal is won"), ACTION);

		assertRefused(publish(nodes, list(edge("t", "c"), edge("c", "a", "Maybe"))), "Condition paths must be Yes or No.");
	}

	@Test
	void everyStepMustBeReachableFromTheTrigger() {
		assertRefused(publish(list(TRIGGER, ACTION, node("b", "Action", "Notify owner")), list(edge("t", "a"))),
				"Connect every step to the trigger.");
	}

	@Test
	void aPlainStepMayLeadToOnlyOneNextStep() {
		String nodes = list(TRIGGER, ACTION, node("b", "Action", "Notify owner"));

		assertRefused(publish(nodes, list(edge("t", "a"), edge("t", "b"))), "A step has too many connections.");
	}

	@Test
	void twoPathsOutOfAConditionMustDiffer() {
		String nodes = list(TRIGGER, node("c", "Condition", "Deal is won"), ACTION, node("b", "Action", "Notify owner"));

		assertRefused(publish(nodes, list(edge("t", "c"), edge("c", "a", "Yes"), edge("c", "b", "Yes"))), "Each condition path must be unique.");
	}

	@Test
	void connectionsCannotLoopBack() {
		String nodes = list(TRIGGER, ACTION, node("b", "Action", "Notify owner"));

		assertRefused(publish(nodes, list(edge("t", "a"), edge("a", "b"), edge("b", "a"))), "Workflow connections cannot form a cycle.");
	}

	@Test
	void aHugeWorkflowIsRefused() {
		String nodes = "[" + IntStream.range(0, 51).mapToObj((i) -> node("n" + i, "Action", "Notify owner")).collect(Collectors.joining(",")) + "]";

		assertRefused(publish(nodes, "[]"), "Workflow is too large.");
	}

}
