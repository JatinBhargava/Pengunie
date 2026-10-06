package com.pengunie.common;

import jakarta.servlet.http.HttpServletRequest;

public final class Requests {

	private Requests() {
	}

	/** Client IP; X-Forwarded-For is already applied by server.forward-headers-strategy. */
	public static String clientIp(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

}
