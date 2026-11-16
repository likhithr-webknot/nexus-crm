package com.nexus.crm.identity.auth;

import java.time.Instant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nexus.crm.identity.user.AppUser;
import com.nexus.crm.identity.user.AppUserRepository;

/** Decides whether a person who signed in with Google may use the app. Anyone can have a Google account, so they must also have been invited. This class never creates a user. */
@Service
@RequiredArgsConstructor
public class SignInService {

	/** Google confirmed who the person is, but they are not allowed into this app. */
	@Getter
	public static class SignInRefusedException extends RuntimeException {

		/** The code sent to the login page: not_invited, account_disabled or email_unverified. */
		private final String code;

		SignInRefusedException(String code, String message) {
			super(message);
			this.code = code;
		}

	}

	private final AppUserRepository users;

	private final UserIdentityRepository identities;

	/**
	 * @param googleId Google's own ID for the person (the "sub" value)
	 * @return the user to sign in
	 * @throws SignInRefusedException if the person may not use the app
	 */
	@Transactional
	public AppUser signIn(String googleId, String email, boolean emailVerified, String pictureUrl) {
		// Check 1: have we seen this Google account before?
		AppUser user = this.identities.findByProviderAndSubject(UserIdentity.GOOGLE, googleId)
			.map((identity) -> this.users.findById(identity.getUserId()).orElseThrow())
			// Check 2: if not, is there an invitation for this email?
			.orElseGet(() -> linkToInvitation(googleId, email, emailVerified));

		if (user.getStatus() == AppUser.Status.DISABLED) {
			throw new SignInRefusedException("account_disabled", "user is disabled");
		}
		user.signedIn(pictureUrl, Instant.now());
		return user;
	}

	private AppUser linkToInvitation(String googleId, String email, boolean emailVerified) {
		if (!emailVerified || email == null || email.isBlank()) {
			throw new SignInRefusedException("email_unverified", "Google did not verify the email");
		}
		String normalized = AppUser.normalizeEmail(email);
		AppUser user = this.users.findByEmail(normalized)
			.orElseThrow(() -> new SignInRefusedException("not_invited", "no user with this email"));
		if (this.identities.existsByUserIdAndProvider(user.getId(), UserIdentity.GOOGLE)) {
			// This email already belongs to a different Google account, so the address must have been given to someone new. Do not let them in.
			throw new SignInRefusedException("not_invited", "email is already linked to another Google account");
		}
		this.identities.save(new UserIdentity(user.getId(), googleId, normalized));
		return user;
	}

}
