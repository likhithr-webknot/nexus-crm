package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.nexus.crm.support.IntegrationTest;

class NexusCrmApplicationTests extends IntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void healthEndpointIsPublicAndReportsUp() {
		assertThat(this.mvc.get().uri("/actuator/health")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("UP");
	}

	@Test
	void otherActuatorEndpointsAreNotExposed() {
		assertThat(this.mvc.get().uri("/actuator/env")).hasStatus4xxClientError();
	}

}
