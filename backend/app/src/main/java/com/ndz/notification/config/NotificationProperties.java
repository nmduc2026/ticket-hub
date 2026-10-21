package com.ndz.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình thông báo.
 *
 * @param from địa chỉ người gửi
 * @param webBaseUrl địa chỉ gốc của trang web, để đặt link "Vé của tôi" trong email, không có dấu "/" ở cuối
 */
@ConfigurationProperties("tickethub.notification")
public record NotificationProperties(@DefaultValue("Ticket Hub <no-reply@tickethub.local>") String from,
		@DefaultValue("http://localhost:5173") String webBaseUrl) {
}
