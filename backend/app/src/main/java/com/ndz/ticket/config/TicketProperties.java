package com.ndz.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình vé.
 *
 * @param signingJwk khoá ký QR ES256 dạng JWK JSON gồm cả phần riêng và {@code kid} (từ biến môi trường / K8s Secret,
 *        ADR 0024); để trống thì sinh khoá tạm lúc khởi động (chỉ dành cho dev và test: vé phát trước đó sẽ không còn
 *        xác minh được khi khởi động lại)
 */
@ConfigurationProperties("tickethub.ticket")
public record TicketProperties(String signingJwk) {
}
