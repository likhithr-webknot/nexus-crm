package com.nexus.crm.ai;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

@Service
@RequiredArgsConstructor
public class AiKeyService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final UUID AUDIT_ID = new UUID(0, 2);
  private final JdbcClient jdbc;
  private final AuditService audit;

  /** Whose key it is. The row number in ai_key_config is fixed per provider. */
  public enum Provider { OPENAI(1), SARVAM(2); final int row; Provider(int row) { this.row = row; } }

  @Value("${OPENAI_API_KEY:}") private String openAiEnvironmentKey;
  @Value("${SARVAM_API_KEY:}") private String sarvamEnvironmentKey;
  @Value("${NEXUS_AI_KEY_NAME:primary}") private String environmentName;
  @Value("${NEXUS_AI_CONFIG_KEY:}") private String configKey;
  @Value("${NEXUS_MAIL_TOKEN_KEY:}") private String mailKey;

  public record Credentials(String key, String name) { }
  public record KeyStatus(boolean configured, String keyName, String source, Instant updatedAt) { }

  /** The OpenAI key in use: the one an admin saved, otherwise the server's. */
  public Credentials active() { return active(Provider.OPENAI); }
  public KeyStatus status() { return status(Provider.OPENAI); }

  public Credentials active(Provider provider) {
    var saved = jdbc.sql("select key_cipher,key_name from ai_key_config where id=:row").param("row", provider.row)
      .query((rs, row) -> new Credentials(decrypt(rs.getBytes("key_cipher")), rs.getString("key_name"))).optional();
    return saved.orElseGet(() -> new Credentials(environmentKey(provider), environmentName));
  }

  public KeyStatus status(Provider provider) {
    var saved = jdbc.sql("select key_name,updated_at from ai_key_config where id=:row").param("row", provider.row)
      .query((rs, row) -> new KeyStatus(true, rs.getString("key_name"), "ADMIN", rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant())).optional();
    boolean onServer = !environmentKey(provider).isBlank();
    return saved.orElseGet(() -> new KeyStatus(onServer, onServer ? environmentName : "", onServer ? "SERVER" : "NONE", null));
  }

  @Transactional
  public void set(UUID adminId, Provider provider, String key, String name) {
    boolean plausible = key != null && key.length() >= 16 && key.length() <= 1000 && key.chars().noneMatch(Character::isWhitespace);
    if (provider == Provider.OPENAI && (!plausible || !key.startsWith("sk-") || key.length() < 32)) throw invalid("Enter a valid OpenAI API key.");
    if (provider == Provider.SARVAM && !plausible) throw invalid("Enter a valid Sarvam AI API key.");
    if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9 ._-]{1,79}"))
      throw invalid("Give this key a short name.");
    byte[] cipher = encrypt(key);
    jdbc.sql("insert into ai_key_config (id,key_cipher,key_name,updated_by) values (:row,:cipher,:name,:user) "
        + "on conflict (id) do update set key_cipher=excluded.key_cipher,key_name=excluded.key_name,updated_by=excluded.updated_by,updated_at=now()")
      .param("row", provider.row).param("cipher", cipher).param("name", name.strip()).param("user", adminId).update();
    audit.record(adminId, "AiKeyConfig", auditId(provider), AuditLog.Action.UPDATE, null);
  }

  @Transactional
  public void clear(UUID adminId, Provider provider) {
    int removed = jdbc.sql("delete from ai_key_config where id=:row").param("row", provider.row).update();
    if (removed == 0) throw invalid("There is no admin-managed key to remove.");
    audit.record(adminId, "AiKeyConfig", auditId(provider), AuditLog.Action.DELETE, null);
  }

  private String environmentKey(Provider provider) { return provider == Provider.OPENAI ? openAiEnvironmentKey : sarvamEnvironmentKey; }
  /** OpenAI keeps the id it always had; Sarvam gets its own, so the audit log tells them apart. */
  private UUID auditId(Provider provider) { return provider == Provider.OPENAI ? AUDIT_ID : new UUID(0, 3); }

  private byte[] encryptionKey() {
    String encoded = configKey.isBlank() ? mailKey : configKey;
    try {
      byte[] decoded = Base64.getDecoder().decode(encoded);
      if (decoded.length == 32) return decoded;
    } catch (IllegalArgumentException ignored) { }
    throw invalid("AI key storage is not configured on the server.");
  }

  private byte[] encrypt(String value) {
    byte[] key = encryptionKey();
    try {
      byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
      cipher.updateAAD("nexus-ai-key-v1".getBytes(StandardCharsets.UTF_8));
      byte[] body = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.allocate(nonce.length + body.length).put(nonce).put(body).array();
    } catch (Exception error) { throw new IllegalStateException("AI key could not be stored.", error); }
  }

  private String decrypt(byte[] stored) {
    try {
      ByteBuffer data = ByteBuffer.wrap(stored);
      byte[] nonce = new byte[12]; data.get(nonce);
      byte[] body = new byte[data.remaining()]; data.get(body);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"), new GCMParameterSpec(128, nonce));
      cipher.updateAAD("nexus-ai-key-v1".getBytes(StandardCharsets.UTF_8));
      return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
    } catch (Exception error) { throw new IllegalStateException("Saved AI key could not be read.", error); }
  }

  private ApiException invalid(String message) { return new ApiException(ErrorCode.VALIDATION_FAILED, message); }
}
