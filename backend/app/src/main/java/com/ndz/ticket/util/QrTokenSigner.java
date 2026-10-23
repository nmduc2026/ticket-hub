package com.ndz.ticket.util;

import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.stereotype.Component;

/**
 * Ký mã QR của vé: JWT ES256 với claim {@code ticketId, eventId, seat, kid} (overview.md §7.5, ADR 0024). Không có
 * {@code exp} hay trạng thái: chữ ký chỉ để loại vé giả, server mới là bên quyết định vé còn dùng được hay không.
 */
@Component
public class QrTokenSigner {

	private final ECKey key;
	private final ECDSASigner signer;

	QrTokenSigner(ECKey key) throws JOSEException {
		this.key = key;
		this.signer = new ECDSASigner(key);
	}

	public String keyId() {
		return key.getKeyID();
	}

	/** @param seat nhãn ghế ngắn gọn để người soát vé đối chiếu, vd. {@code A-B-12} (khu A, hàng B, ghế 12) */
	public String sign(UUID ticketId, UUID eventId, String seat) {
		try {
			JWTClaimsSet claims = new JWTClaimsSet.Builder().claim("ticketId", ticketId.toString())
					.claim("eventId", eventId.toString()).claim("seat", seat).claim("kid", key.getKeyID()).build();
			SignedJWT jwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(), claims);
			jwt.sign(signer);
			return jwt.serialize();
		}
		catch (JOSEException e) {
			throw new IllegalStateException("Không ký được mã QR của vé " + ticketId, e);
		}
	}

}
