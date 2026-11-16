package com.nexus.crm.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.nexus.crm.shared.web.RequestIdFilter;

/** Turns every exception into an error response. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String TYPE_BASE = "https://nexus-crm.example/errors/";

	/** The error name used when an access token has expired. */
	public static final String TOKEN_EXPIRED_ERROR = "token_expired";

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
		return respond(ex, ex.getCode(), ex.getMessage(), request);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
		// An expired token gets its own code, so the frontend knows to refresh instead of signing in again.
		boolean expired = ex.getCause() instanceof JwtValidationException validation && validation.getErrors()
			.stream()
			.anyMatch((error) -> TOKEN_EXPIRED_ERROR.equals(error.getErrorCode()));
		return expired ? respond(ex, ErrorCode.TOKEN_EXPIRED, "The access token has expired.", request)
				: respond(ex, ErrorCode.NOT_AUTHENTICATED, "Sign in to continue.", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
		if (ex instanceof CsrfException) {
			log.warn("Security event: csrf_invalid");
			return respond(ex, ErrorCode.CSRF_INVALID, "The CSRF token is missing or does not match.", request);
		}
		return respond(ex, ErrorCode.FORBIDDEN, "Your role does not allow this action.", request);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<Object> handleVersionConflict(ObjectOptimisticLockingFailureException ex, WebRequest request) {
		return respond(ex, ErrorCode.VERSION_CONFLICT, "The record was changed by someone else. Reload and retry.",
				request);
	}

	/** A database rule stopped the change, for example the unique email of an open lead. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> handleConstraint(DataIntegrityViolationException ex, WebRequest request) {
		log.warn("Database constraint violated: {}", ex.getMostSpecificCause().getMessage());
		return respond(ex, ErrorCode.DUPLICATE, "A record with the same unique value already exists.", request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		log.error("Unhandled exception", ex);
		// The real error is only logged. The client gets a generic message and the requestId.
		return respond(ex, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map((error) -> Map.of("field", error.getField(), "code", fieldCode(error.getCode()), "message",
					String.valueOf(error.getDefaultMessage())))
			.toList();
		ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED,
				errors.size() == 1 ? "1 field is invalid." : errors.size() + " fields are invalid.");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, ErrorCode.VALIDATION_FAILED.getStatus(), request);
	}

	/** Adds the code and requestId to every error response. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
				problem.setProperty("code", defaultCode(statusCode).value());
			}
			if (request instanceof ServletWebRequest servletRequest) {
				HttpServletRequest httpRequest = servletRequest.getRequest();
				problem.setProperty("requestId", RequestIdFilter.current(httpRequest));
				problem.setInstance(URI.create(httpRequest.getRequestURI()));
			}
		}
		return response;
	}

	private ResponseEntity<Object> respond(Exception ex, ErrorCode code, String detail, WebRequest request) {
		return handleExceptionInternal(ex, problem(code, detail), new HttpHeaders(), code.getStatus(), request);
	}

	private static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
		problem.setType(URI.create(TYPE_BASE + code.value().replace('_', '-')));
		problem.setTitle(code.getTitle());
		problem.setProperty("code", code.value());
		return problem;
	}

	private static ErrorCode defaultCode(HttpStatusCode status) {
		if (status.value() == HttpStatus.NOT_FOUND.value()) {
			return ErrorCode.NOT_FOUND;
		}
		return status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.MALFORMED_REQUEST;
	}

	/** Maps a validation rule (NotBlank, Email...) to the code we return for that field. */
	private static String fieldCode(String constraint) {
		if (constraint == null) {
			return "invalid";
		}
		return switch (constraint) {
			case "NotNull", "NotBlank", "NotEmpty" -> "required";
			case "Email", "Pattern" -> "invalid_format";
			case "Size" -> "invalid_length";
			default -> "invalid";
		};
	}

}
