package com.nexus.crm.shared.error;

import lombok.Getter;

/** Throw this to return an error to the client. The message is shown to the client, so keep it safe to read. */
@Getter
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	public ApiException(ErrorCode code, String detail) {
		super(detail);
		this.code = code;
	}

}
