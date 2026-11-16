package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Modules may depend on each other in one direction only. If leads uses deals, deals must not use leads,
 * directly or through a third module. A cycle makes modules impossible to understand or change on their own.
 *
 * <p>
 * Plain text scan of the imports, so this needs no database and runs in a second.
 */
class ModuleDependencyTests {

	private static final Pattern PACKAGE = Pattern.compile("^package ([\\w.]+);", Pattern.MULTILINE);

	private static final Pattern IMPORT = Pattern.compile("^import (com\\.nexus\\.crm\\.[\\w.]+);", Pattern.MULTILINE);

	/** "com.nexus.crm.leads.dto" is the module "leads". In identity and shared the second level is the module. */
	private static String moduleOf(String pkg) {
		String[] parts = pkg.replace("com.nexus.crm.", "").split("\\.");
		boolean grouped = parts[0].equals("identity") || parts[0].equals("shared");
		boolean sub = grouped && parts.length > 1 && !parts[1].equals("dto") && !parts[1].equals("event");
		return sub ? parts[0] + "." + parts[1] : parts[0];
	}

	private static Map<String, Set<String>> dependencies() throws IOException {
		Map<String, Set<String>> graph = new HashMap<>();
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path file : files.filter((p) -> p.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				Matcher own = PACKAGE.matcher(source);
				if (!own.find()) {
					continue;
				}
				String from = moduleOf(own.group(1));
				Matcher imports = IMPORT.matcher(source);
				while (imports.find()) {
					String target = imports.group(1);
					String to = moduleOf(target.substring(0, target.lastIndexOf('.')));
					if (!to.equals(from)) {
						graph.computeIfAbsent(from, (k) -> new HashSet<>()).add(to);
					}
				}
			}
		}
		return graph;
	}

	/** Depth-first search for a way back to where we started. Returns the cycle, or an empty list. */
	private static List<String> findCycle(Map<String, Set<String>> graph) {
		for (String start : graph.keySet()) {
			List<String> path = new ArrayList<>(List.of(start));
			if (walk(graph, start, start, path, new HashSet<>())) {
				return path;
			}
		}
		return List.of();
	}

	private static boolean walk(Map<String, Set<String>> graph, String start, String current, List<String> path,
			Set<String> seen) {
		for (String next : graph.getOrDefault(current, Set.of())) {
			if (next.equals(start)) {
				path.add(next);
				return true;
			}
			if (seen.add(next)) {
				path.add(next);
				if (walk(graph, start, next, path, seen)) {
					return true;
				}
				path.removeLast();
			}
		}
		return false;
	}

	@Test
	void modulesDoNotDependOnEachOtherInACircle() throws IOException {
		assertThat(findCycle(dependencies())).as("a dependency cycle between modules").isEmpty();
	}

	@Test
	void theSharedPartNeverDependsOnABusinessModule() throws IOException {
		Set<String> business = Set.of("leads", "contacts", "deals", "activities", "followups", "notifications", "reporting",
				"search", "audit");

		dependencies().forEach((from, targets) -> {
			if (from.startsWith("shared")) {
				assertThat(targets).as(from + " must not depend on a business module").doesNotContainAnyElementsOf(business);
			}
		});
	}

	@Test
	void identityDoesNotDependOnABusinessModule() throws IOException {
		Set<String> business = Set.of("leads", "contacts", "deals", "activities", "followups", "notifications", "reporting",
				"search", "audit");

		dependencies().forEach((from, targets) -> {
			if (from.startsWith("identity")) {
				assertThat(targets).as(from + " must not depend on a business module").doesNotContainAnyElementsOf(business);
			}
		});
	}

}
