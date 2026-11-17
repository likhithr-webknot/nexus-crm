package com.nexus.crm.shared.error;

import java.util.Locale;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** The error codes the API can return. The frontend reads these to decide what to show, so do not rename or remove one. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
	NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED, "Not authenticated"),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token expired"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have access"),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "Invalid CSRF token"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
	DUPLICATE(HttpStatus.CONFLICT, "Already exists"),
	INVALID_STATE(HttpStatus.CONFLICT, "Not allowed in the current state"),
	VERSION_CONFLICT(HttpStatus.CONFLICT, "Changed by someone else"),
	VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed"),
	TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Request too large"),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
	UNSUPPORTED_CURRENCY(HttpStatus.UNPROCESSABLE_CONTENT, "Currency not available"),
	INVALID_PRECISION(HttpStatus.UNPROCESSABLE_CONTENT, "Too many decimal places"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");

	private final HttpStatus status;

	private final String title;

	/** The code as it appears in the response, for example token_expired. */
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
