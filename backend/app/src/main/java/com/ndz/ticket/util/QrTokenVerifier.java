package com.ndz.ticket.util;

import java.text.ParseException;
import java.util.Optional;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import com.ndz.ticket.repository.SigningKeyRepository;

/**
 * Xác minh chữ ký QR ở server (overview.md §7.5, ADR 0024). Chữ ký chỉ chứng minh vé do hệ thống phát; vé còn dùng được
 * hay không do trạng thái trong DB quyết định. Khoá lấy theo {@code kid} nên vé ký bằng khoá đã xoay vẫn xác minh được.
 */
@Component
public class QrTokenVerifier {

	private final SigningKeyRepository keys;

	QrTokenVerifier(SigningKeyRepository keys) {
		this.keys = keys;
	}

	/** Nội dung QR đã xác minh chữ ký. */
	public record Claims(UUID ticketId, UUID eventId) {
	}

	/** Rỗng nếu chuỗi không phải JWT ES256 hợp lệ, {@code kid} lạ, chữ ký sai hoặc claim thiếu / sai kiểu. */
	public Optional<Claims> verify(String token) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			String keyId = jwt.getHeader().getKeyID();
			if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm()) || keyId == null) {
				return Optional.empty();
			}
			Optional<String> publicKey = keys.findPublicKey(keyId);
			if (publicKey.isEmpty() || !jwt.verify(new ECDSAVerifier(ECKey.parse(publicKey.get()).toECPublicKey()))) {
				return Optional.empty();
			}
			return Optional.of(new Claims(UUID.fromString(jwt.getJWTClaimsSet().getStringClaim("ticketId")),
					UUID.fromString(jwt.getJWTClaimsSet().getStringClaim("eventId"))));
		}
		catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
			return Optional.empty();
		}
	}

}
