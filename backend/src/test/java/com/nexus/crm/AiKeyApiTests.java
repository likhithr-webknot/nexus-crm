package com.nexus.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import com.nexus.crm.shared.Role;
import com.nexus.crm.support.IntegrationTest;

@TestPropertySource(properties = "NEXUS_AI_CONFIG_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class AiKeyApiTests extends IntegrationTest {
  @Autowired JdbcClient jdbc;

  @Test void adminCanReplaceKeyWithoutExposingItAndRepsCannotReadIt() {
    var admin = signedIn(Role.ADMIN);
    var rep = signedIn(Role.REP);
    String secret = "sk-test-abcdefghijklmnopqrstuvwxyz0123456789";
    var saved = withCsrf(mvc.put().uri("/api/v1/ai/admin/key").cookie(admin.accessToken()))
      .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Team key\",\"key\":\"" + secret + "\"}").exchange();
    assertThat(saved).hasStatusOk();
    String response = new String(saved.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    assertThat(response).contains("Team key", "ADMIN").doesNotContain(secret);
    byte[] stored = jdbc.sql("select key_cipher from ai_key_config where id=1").query(byte[].class).single();
    assertThat(new String(stored, StandardCharsets.UTF_8)).doesNotContain(secret);
    assertThat(mvc.get().uri("/api/v1/ai/admin/key").cookie(rep.accessToken()).exchange()).hasStatus(403);
    assertThat(withCsrf(mvc.put().uri("/api/v1/ai/admin/key").cookie(rep.accessToken()))
      .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other key\",\"key\":\"" + secret + "\"}").exchange()).hasStatus(403);
    assertThat(withCsrf(mvc.delete().uri("/api/v1/ai/admin/key").cookie(admin.accessToken())).exchange()).hasStatus(204);
  }
}
