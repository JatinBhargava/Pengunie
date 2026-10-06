package com.pengunie.jobs;

/** Thrown by a handler when retrying cannot succeed (e.g. a corrupt file). */
public class PermanentJobFailure extends RuntimeException {

	public PermanentJobFailure(String message) {
		super(message);
	}

	public PermanentJobFailure(String message, Throwable cause) {
		super(message, cause);
	}

}
