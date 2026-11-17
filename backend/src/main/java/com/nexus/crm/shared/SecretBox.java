package com.nexus.crm.shared;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts small secrets (API keys, mail tokens) with AES-256-GCM. Give it one or more keys, newest first. It
 * encrypts with the first and can read data made with any of them, so a key can be replaced without losing what was
 * saved: put the new key in front, and drop the old one once everything has been saved again.
 */
public final class SecretBox {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int NONCE_BYTES = 12;

	private final List<byte[]> keys;

	private final byte[] context;

	private SecretBox(List<byte[]> keys, String context) {
		this.keys = keys;
		this.context = (context == null) ? null : context.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * @param keyList one or more base64 keys of 32 bytes, separated by commas, newest first. Blank parts are ignored.
	 * @param context tied to the encrypted data, so a value saved for one purpose cannot be read as another. Null for none.
	 * @return null when there is no valid key in the list
	 */
	public static SecretBox of(String keyList, String context) {
		List<byte[]> keys = new ArrayList<>();
		if (keyList != null) {
			for (String part : keyList.split(",")) {
				if (part.isBlank()) {
					continue;
				}
				try {
					byte[] key = Base64.getDecoder().decode(part.strip());
					if (key.length != 32) {
						return null;
					}
					keys.add(key);
				}
				catch (IllegalArgumentException notBase64) {
					return null;
				}
			}
		}
		return keys.isEmpty() ? null : new SecretBox(keys, context);
	}

	public byte[] encrypt(String value) {
		try {
			byte[] nonce = new byte[NONCE_BYTES];
			RANDOM.nextBytes(nonce);
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, this.keys.getFirst(), nonce);
			byte[] body = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.allocate(nonce.length + body.length).put(nonce).put(body).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("The value could not be encrypted.", ex);
		}
	}

	/** Tries each key. Throws if none of them opens the value. */
	public String decrypt(byte[] stored) {
		ByteBuffer data = ByteBuffer.wrap(stored);
		byte[] nonce = new byte[NONCE_BYTES];
		data.get(nonce);
		byte[] body = new byte[data.remaining()];
		data.get(body);
		for (byte[] key : this.keys) {
			try {
				return new String(cipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(body), StandardCharsets.UTF_8);
			}
			catch (GeneralSecurityException wrongKey) {
				// Try the next key.
			}
		}
		throw new IllegalStateException("The saved value could not be read with the configured keys.");
	}

	private Cipher cipher(int mode, byte[] key, byte[] nonce) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
		if (this.context != null) {
			cipher.updateAAD(this.context);
		}
		return cipher;
	}

}
