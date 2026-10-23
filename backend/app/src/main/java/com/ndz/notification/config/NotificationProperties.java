package com.ndz.notification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình thông báo.
 *
 * @param from địa chỉ người gửi
 * @param webBaseUrl địa chỉ gốc của trang web, để đặt link "Vé của tôi" trong email, không có dấu "/" ở cuối
 * @param inboxRetention thời gian giữ thông báo trong app trước khi job xoá (overview.md §6.5: 90 ngày)
 */
@ConfigurationProperties("tickethub.notification")
public record NotificationProperties(@DefaultValue("Ticket Hub <no-reply@tickethub.local>") String from,
		@DefaultValue("http://localhost:5173") String webBaseUrl, @DefaultValue("90d") Duration inboxRetention) {
}
