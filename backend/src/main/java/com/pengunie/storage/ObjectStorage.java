package com.pengunie.storage;

import java.io.InputStream;
import java.net.URI;

public interface ObjectStorage {

	void put(String key, byte[] content, String contentType);

	InputStream get(String key);

	/**
	 * A short-lived link the browser can open directly (used for citation deep links). May be
	 * relative to the API origin.
	 */
	URI presignedGet(String key, String downloadFilename, String contentType);

	void delete(String key);

}
