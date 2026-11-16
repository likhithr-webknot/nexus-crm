package com.nexus.crm.identity;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import com.nexus.crm.shared.GlobalExceptionHandler;

/** Sets up how access tokens (JWTs) are signed and checked. */
@Slf4j
@Configuration(proxyBeanMethods = false)
class JwtConfig {

	/**
	 * The key used to sign access tokens. It is loaded once at startup, and the
	 * app will not start without it (unless a temporary key is allowed).
	 */
	@Bean
	ECKey signingKey(AuthProperties properties) throws JOSEException {
		KeyPair pair = loadKeyPair(properties.jwt());
		// The key ID is worked out from the key itself, so a new key gets a new ID.
		return new ECKey.Builder(Curve.P_256, (ECPublicKey) pair.getPublic()).privateKey((ECPrivateKey) pair.getPrivate())
			.keyUse(KeyUse.SIGNATURE)
			.algorithm(JWSAlgorithm.ES256)
			.keyIDFromThumbprint()
			.build();
	}

	@Bean
	JWKSource<SecurityContext> jwkSource(ECKey signingKey) {
		// A set of keys, so an old key can be added here while switching to a new one.
		return new ImmutableJWKSet<>(new JWKSet(signingKey));
	}

	@Bean
	JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
		return new NimbusJwtEncoder(jwkSource);
	}

	@Bean
	JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, AuthProperties properties) {
		// Only tokens signed with ES256 are accepted. Anything else is rejected.
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource)
			.jwsAlgorithm(SignatureAlgorithm.ES256)
			.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtIssuerValidator(properties.jwt().issuer()), JwtConfig::checkNotExpired));
		return decoder;
	}

	/**
	 * A second secret key, made from the signing key, used to encrypt the
	 * sign-in cookie. This saves having another secret to configure.
	 */
	@Bean
	SecretKey signInCookieKey(ECKey signingKey) throws GeneralSecurityException, JOSEException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(signingKey.toECPrivateKey().getEncoded(), "HmacSHA256"));
		byte[] key = mac.doFinal("nexus-crm/oauth-authorization-request/v1".getBytes(StandardCharsets.UTF_8));
		return new SecretKeySpec(key, "AES");
	}

	/**
	 * Checks that the token has not expired. An expired token gets its own
	 * error name, so the API can tell the frontend to refresh.
	 */
	private static OAuth2TokenValidatorResult checkNotExpired(Jwt token) {
		Instant expiresAt = token.getExpiresAt();
		if (expiresAt == null) {
			return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "No expiry", null));
		}
		if (!Instant.now().isBefore(expiresAt)) {
			return OAuth2TokenValidatorResult
				.failure(new OAuth2Error(GlobalExceptionHandler.TOKEN_EXPIRED_ERROR, "Token expired", null));
		}
		return OAuth2TokenValidatorResult.success();
	}

	private static KeyPair loadKeyPair(AuthProperties.Jwt properties) {
		boolean configured = StringUtils.hasText(properties.privateKey()) && StringUtils.hasText(properties.publicKey());
		try {
			if (configured) {
				KeyFactory factory = KeyFactory.getInstance("EC");
				KeyPair pair = new KeyPair(
						factory.generatePublic(new X509EncodedKeySpec(decodePem(properties.publicKey()))),
						factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(properties.privateKey()))));
				requireMatchingPair(pair);
				return pair;
			}
			if (properties.allowEphemeralKey()) {
				log.warn("No JWT signing key configured: using a key generated for this run only. "
						+ "Access tokens stop working at every restart. Never do this in production.");
				KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
				generator.initialize(new ECGenParameterSpec("secp256r1"));
				return generator.generateKeyPair();
			}
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			// Do not include the original error: it can contain parts of the key.
			throw new IllegalStateException("nexus.auth.jwt.private-key / public-key are not a valid EC P-256 "
					+ "key pair (PKCS#8 private key, X.509 public key, PEM).");
		}
		throw new IllegalStateException("No JWT signing key configured. Set NEXUS_JWT_PRIVATE_KEY and "
				+ "NEXUS_JWT_PUBLIC_KEY (see backend/README.md).");
	}

	/** Reads a key from PEM text. The BEGIN/END lines and line breaks are optional. */
	private static byte[] decodePem(String pem) {
		String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replace("\\n", "").replaceAll("\\s", "");
		return Base64.getDecoder().decode(base64);
	}

	/** Signs a test message with the private key and checks it with the public key. */
	private static void requireMatchingPair(KeyPair pair) throws GeneralSecurityException {
		byte[] probe = "nexus-crm-key-check".getBytes(StandardCharsets.UTF_8);
		Signature signer = Signature.getInstance("SHA256withECDSA");
		signer.initSign(pair.getPrivate());
		signer.update(probe);
		Signature verifier = Signature.getInstance("SHA256withECDSA");
		verifier.initVerify(pair.getPublic());
		verifier.update(probe);
		if (!verifier.verify(signer.sign())) {
			throw new GeneralSecurityException("public key does not match private key");
		}
	}

}
