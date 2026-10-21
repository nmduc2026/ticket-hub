package com.ndz.ticket.config;

import java.text.ParseException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.ndz.ticket.repository.SigningKeyRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TicketProperties.class)
class TicketSigningConfiguration {

	private static final Logger log = LoggerFactory.getLogger(TicketSigningConfiguration.class);

	/** Khoá ký QR đang dùng (ES256, P-256). Khoá riêng chỉ sống trong bộ nhớ, không lưu DB. */
	@Bean
	ECKey ticketSigningKey(TicketProperties properties) throws ParseException, JOSEException {
		if (StringUtils.hasText(properties.signingJwk())) {
			ECKey key = ECKey.parse(properties.signingJwk());
			if (!key.isPrivate() || key.getKeyID() == null || !Curve.P_256.equals(key.getCurve())) {
				throw new IllegalStateException(
						"tickethub.ticket.signing-jwk phải là JWK EC P-256 có khoá riêng và kid");
			}
			return key;
		}
		String keyId = "dev-" + Long.toHexString(System.nanoTime());
		log.warn("tickethub.ticket.signing-jwk chưa đặt: dùng khoá ký tạm {}, vé phát ra sẽ không xác minh được sau "
				+ "khi khởi động lại (chỉ dành cho dev / test)", keyId);
		return new ECKeyGenerator(Curve.P_256).keyID(keyId).generate();
	}

	/** Ghi khoá công khai của khoá đang dùng vào DB lúc khởi động, để xác minh QR theo {@code kid} (xoay vòng khoá). */
	@Bean
	ApplicationRunner registerSigningKey(ECKey ticketSigningKey, SigningKeyRepository keys) {
		return args -> keys.activate(ticketSigningKey.getKeyID(), ticketSigningKey.toPublicJWK().toJSONString());
	}

}
