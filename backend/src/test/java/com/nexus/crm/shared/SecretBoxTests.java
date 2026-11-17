package com.nexus.crm.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class SecretBoxTests {

	private static String key(int fill) {
		byte[] bytes = new byte[32];
		java.util.Arrays.fill(bytes, (byte) fill);
		return Base64.getEncoder().encodeToString(bytes);
	}

	@Test
	void aValueComesBackAsItWent() {
		SecretBox box = SecretBox.of(key(1), "purpose");

		assertThat(box.decrypt(box.encrypt("sk-secret"))).isEqualTo("sk-secret");
	}

	@Test
	void theSameValueIsEncryptedDifferentlyEachTime() {
		SecretBox box = SecretBox.of(key(1), null);

		assertThat(box.encrypt("same")).isNotEqualTo(box.encrypt("same"));
	}

	@Test
	void anotherKeyCannotReadIt() {
		byte[] stored = SecretBox.of(key(1), null).encrypt("secret");

		assertThatThrownBy(() -> SecretBox.of(key(2), null).decrypt(stored)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aValueSavedForOnePurposeCannotBeReadAsAnother() {
		byte[] stored = SecretBox.of(key(1), "ai").encrypt("secret");

		assertThatThrownBy(() -> SecretBox.of(key(1), "mail").decrypt(stored)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aKeyCanBeReplacedWithoutLosingWhatWasSaved() {
		byte[] saved = SecretBox.of(key(1), null).encrypt("secret");
		SecretBox rotated = SecretBox.of(key(2) + "," + key(1), null);

		assertThat(rotated.decrypt(saved)).isEqualTo("secret");
		// New values use the first key, so the old key is not needed for them.
		assertThat(SecretBox.of(key(2), null).decrypt(rotated.encrypt("new"))).isEqualTo("new");
	}

	@Test
	void badOrMissingKeysGiveNoBox() {
		assertThat(SecretBox.of(null, null)).isNull();
		assertThat(SecretBox.of("", null)).isNull();
		assertThat(SecretBox.of("not base64!", null)).isNull();
		assertThat(SecretBox.of(Base64.getEncoder().encodeToString(new byte[16]), null)).isNull();
		assertThat(SecretBox.of(key(1) + ",short", null)).isNull();
	}

}
