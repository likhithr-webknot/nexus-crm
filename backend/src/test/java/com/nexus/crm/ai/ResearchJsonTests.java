package com.nexus.crm.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ResearchJsonTests {

	/** Research saved before the summary existed has no "summary" key. It must still load. */
	@Test
	void researchSavedWithoutASummaryStillLoads() {
		String saved = """
			{"website":"https://example.com/","logoUrl":null,"email":null,
			 "sources":["https://example.com/about"],"researchedAt":"2026-11-01T10:00:00Z"}""";

		AiService.Research research = JsonMapper.builder().build().readValue(saved, AiService.Research.class);

		assertThat(research.summary()).isNull();
		assertThat(research.website()).isEqualTo("https://example.com/");
		assertThat(research.sources()).containsExactly("https://example.com/about");
	}
}
