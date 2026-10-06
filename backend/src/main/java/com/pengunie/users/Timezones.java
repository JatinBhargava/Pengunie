package com.pengunie.users;

import java.time.DateTimeException;
import java.time.ZoneId;

import com.pengunie.common.ApiException;

public final class Timezones {

	private Timezones() {
	}

	/** Returns the canonical zone id or throws a 400 problem. */
	public static String validate(String timezone) {
		try {
			return ZoneId.of(timezone).getId();
		}
		catch (DateTimeException ex) {
			throw ApiException.badRequest("invalid_timezone", "Unknown timezone: " + timezone);
		}
	}

}
