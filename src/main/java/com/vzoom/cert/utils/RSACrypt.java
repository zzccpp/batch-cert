package com.vzoom.cert.utils;

import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.Cipher;

public class RSACrypt {

	private static final String KEY_ALGORITHM = "RSA";
	private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";
	public static final String SPECIFIC_KEY_ALGORITHM = "RSA/ECB/PKCS1Padding";
	public static String signByPrivateKey(String data, String privateKeyStr, String charsetName) throws Exception {

		byte[] privateKeyByte = Encodes.decodeBase64(privateKeyStr);
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyByte);
		KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
		RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
		Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
		signature.initSign(privateKey);
		signature.update(data.getBytes(charsetName));

		return Encodes.encodeBase64(signature.sign());
	}

	/**
	 * 回调私钥解密方法
	 * @param data
	 * @param privateKeyStr
	 * @return
	 * @throws Exception
	 */
	public static String decryptByPrivateKey(String data, String privateKeyStr) throws Exception {
		if (null == data)
			return null;
		// byte[] dataB = data.getBytes("UTF-8");
		byte[] dataB = Encodes.decodeBase64(data);
		byte[] privateKeyByte = Encodes.decodeBase64(privateKeyStr);
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyByte);
		KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
		RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);

		Cipher cipher = Cipher.getInstance(SPECIFIC_KEY_ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		// 模长
		int key_len = privateKey.getModulus().bitLength() / 8;
		byte[] decryptedData = null;
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		try {
			int dataLength = dataB.length;
			for (int i = 0; i < dataLength; i += key_len) {
				int decryptLength = dataLength - i < key_len ? dataLength - i : key_len;
				byte[] doFinal = cipher.doFinal(dataB, i, decryptLength);
				bout.write(doFinal);
			}
			decryptedData = bout.toByteArray();
		} finally {
			if (bout != null) {
				bout.close();
			}
		}
		return new String(decryptedData,"UTF-8");
		//return Encodes.encodeBase64(decryptedData);
	}
}
