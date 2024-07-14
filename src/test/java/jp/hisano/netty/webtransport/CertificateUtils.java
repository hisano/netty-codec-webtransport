package jp.hisano.netty.webtransport;

import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Date;

final class CertificateUtils {
	static SelfSignedCertificate createSelfSignedCertificateForLocalHost() throws CertificateException {
		try {
			Date now = new Date();
			Date oneDayLater = new Date(now.getTime() + 24 * 60 * 60 * 1000);
			return new SelfSignedCertificate(now, oneDayLater, "EC", 256);
		} catch (CertificateException e) {
			throw new IllegalStateException("Creating self signed certificate for localhost failed.", e);
		}
	}

	static String toPublicKeyHashAsBase64(SelfSignedCertificate selfSignedCertificate) {
		PublicKey publicKey = selfSignedCertificate.cert().getPublicKey();

		byte[] publicKeyAsDer = publicKey.getEncoded();

		byte[] publicKeyHash;
		try {
			publicKeyHash = MessageDigest.getInstance("SHA-256").digest(publicKeyAsDer);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Loading hash algorithm failed.", e);
		}

		return Base64.getEncoder().encodeToString(publicKeyHash);
	}

	private CertificateUtils() {
	}
}
