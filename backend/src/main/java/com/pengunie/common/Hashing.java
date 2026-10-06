package com.pengunie.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {

	private Hashing() {
	}

	public static MessageDigest sha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static String sha256Hex(byte[] bytes) {
		return HexFormat.of().formatHex(sha256Digest().digest(bytes));
	}

}
