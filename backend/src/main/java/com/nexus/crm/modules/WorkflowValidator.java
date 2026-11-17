package com.nexus.crm.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * The rules a workflow must meet before it can be published. A draft may hold any shape; only publishing is checked,
 * because only a published workflow runs.
 *
 * <p>A workflow is a set of steps ("nodes") joined by connections ("edges"): exactly one Trigger, at least one Action,
 * every step reachable from the trigger, no loops, and Condition/Branch steps leave by a "Yes" and a "No" path.
 */
final class WorkflowValidator {

	private static final Set<String> STEP_TYPES = Set.of("Trigger", "Condition", "Action", "Delay", "Branch");

	private static final Set<String> TRIGGERS = Set.of("Deal created", "Deal won", "Lead qualified");

	private static final Set<String> ACTIONS = Set.of("Notify owner", "Create follow-up");

	private static final Set<String> DELAYS = Set.of("Wait 1 hour", "Wait 1 day");

	private static final Set<String> CONDITIONS = Set.of("Deal is won", "Lead is qualified");

	private static final Set<String> YES_NO = Set.of("Yes", "No");

	private static final int MAX_ID_LENGTH = 100;

	private WorkflowValidator() {
	}

	static void validate(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
		requireExactlyOneTrigger(nodes);
		Map<String, String> typeById = checkSteps(nodes);
		checkConnections(edges, typeById);

		Map<String, List<String>> next = nextSteps(edges);
		requireEveryStepReachable(nodes, next, typeById.keySet());
		checkEachStep(edges, next, typeById);
	}

	private static void requireExactlyOneTrigger(List<Map<String, Object>> nodes) {
		if (nodes.stream().filter((n) -> "Trigger".equals(n.get("type"))).count() != 1) {
			throw invalid("A published workflow needs exactly one trigger.");
		}
	}

	/** Checks each step on its own and returns every step's type by id. */
	private static Map<String, String> checkSteps(List<Map<String, Object>> nodes) {
		Set<String> ids = new HashSet<>();
		Map<String, String> typeById = new HashMap<>();
		int actions = 0;

		for (Map<String, Object> node : nodes) {
			String id = String.valueOf(node.get("id"));
			String type = String.valueOf(node.get("type"));
			String label = String.valueOf(node.get("label"));

			boolean validShape = node.get("id") != null && ids.add(id) && id.length() <= MAX_ID_LENGTH && STEP_TYPES.contains(type);
			if (!validShape) {
				throw invalid("Workflow has an invalid step.");
			}
			if (!isSupportedLabel(type, label)) {
				throw invalid("Choose a supported workflow step.");
			}
			typeById.put(id, type);
			if ("Action".equals(type)) {
				actions++;
			}
		}

		if (actions == 0) {
			throw invalid("Add an action before publishing.");
		}
		return typeById;
	}

	private static boolean isSupportedLabel(String type, String label) {
		return switch (type) {
			case "Trigger" -> TRIGGERS.contains(label);
			case "Action" -> ACTIONS.contains(label);
			case "Delay" -> DELAYS.contains(label);
			default -> CONDITIONS.contains(label); // Condition and Branch
		};
	}

	/** Each connection joins two different, existing steps, and leaves a Condition or Branch by a Yes or a No path. */
	private static void checkConnections(List<Map<String, Object>> edges, Map<String, String> typeById) {
		for (Map<String, Object> edge : edges) {
			Object source = edge.get("source");
			Object target = edge.get("target");
			boolean joinsTwoSteps = typeById.containsKey(source) && typeById.containsKey(target) && !source.equals(target);
			if (!joinsTwoSteps) {
				throw invalid("Workflow has an invalid connection.");
			}
			if (isDecision(typeById.get(source)) && !YES_NO.contains(String.valueOf(edge.get("label")))) {
				throw invalid("Condition paths must be Yes or No.");
			}
		}
	}

	/** For each step, the steps it leads to. */
	private static Map<String, List<String>> nextSteps(List<Map<String, Object>> edges) {
		Map<String, List<String>> next = new HashMap<>();
		for (Map<String, Object> edge : edges) {
			next.computeIfAbsent((String) edge.get("source"), (key) -> new ArrayList<>()).add((String) edge.get("target"));
		}
		return next;
	}

	private static void requireEveryStepReachable(List<Map<String, Object>> nodes, Map<String, List<String>> next, Set<String> ids) {
		String triggerId = nodes.stream()
			.filter((n) -> "Trigger".equals(n.get("type")))
			.map((n) -> String.valueOf(n.get("id")))
			.findFirst()
			.orElseThrow();
		Set<String> reachable = new HashSet<>();
		collectReachable(triggerId, next, reachable);
		if (reachable.size() != ids.size()) {
			throw invalid("Connect every step to the trigger.");
		}
	}

	/** Per-step limits: how many ways out, distinct Yes/No paths, and no way back to itself. */
	private static void checkEachStep(List<Map<String, Object>> edges, Map<String, List<String>> next, Map<String, String> typeById) {
		for (String id : typeById.keySet()) {
			boolean decision = isDecision(typeById.get(id));
			int outgoing = next.getOrDefault(id, List.of()).size();

			if (outgoing > (decision ? 2 : 1)) {
				throw invalid("A step has too many connections.");
			}
			if (decision) {
				long distinctPaths = edges.stream().filter((e) -> id.equals(e.get("source"))).map((e) -> e.get("label")).distinct().count();
				if (distinctPaths != outgoing) {
					throw invalid("Each condition path must be unique.");
				}
			}
			if (hasCycle(id, next, new HashSet<>())) {
				throw invalid("Workflow connections cannot form a cycle.");
			}
		}
	}

	private static boolean isDecision(String type) {
		return "Condition".equals(type) || "Branch".equals(type);
	}

	private static void collectReachable(String id, Map<String, List<String>> next, Set<String> reachable) {
		if (!reachable.add(id)) {
			return;
		}
		for (String following : next.getOrDefault(id, List.of())) {
			collectReachable(following, next, reachable);
		}
	}

	/** True when walking forward from {@code id} comes back to a step already on the current path. */
	private static boolean hasCycle(String id, Map<String, List<String>> next, Set<String> path) {
		if (!path.add(id)) {
			return true;
		}
		for (String following : next.getOrDefault(id, List.of())) {
			if (hasCycle(following, next, path)) {
				return true;
			}
		}
		path.remove(id);
		return false;
	}

	private static ApiException invalid(String message) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, message);
	}

}
