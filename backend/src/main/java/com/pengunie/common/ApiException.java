package com.pengunie.common;

import org.springframework.http.HttpStatus;

/** An error with a stable machine-readable code, rendered as an RFC 9457 problem detail. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static ApiException notFound(String what) {
		return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found");
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	public static ApiException unauthorized(String code, String message) {
		return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

}
