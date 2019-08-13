
package com.vzoom.cert.utils;

import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.binary.Base64;

public class Encodes {
	private static final String DEFAULT_URL_ENCODING = "UTF-8";
	
	public static String encodeBase64(byte[] input) {
		return new String(Base64.encodeBase64(input));
	}
	
	public static byte[] decodeBase64(String input) throws UnsupportedEncodingException {
		return Base64.decodeBase64(input.getBytes(DEFAULT_URL_ENCODING));
	}
}
