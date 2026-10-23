package com.ndz.notification.client;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.ndz.notification.config.NotificationProperties;

/** Adapter gửi email qua SMTP (Mailpit khi dev, SES khi chạy cloud); service không phụ thuộc kiểu của JavaMail. */
@Component
public class EmailSender {

	private final JavaMailSender mailSender;
	private final NotificationProperties properties;

	EmailSender(JavaMailSender mailSender, NotificationProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	/** @throws EmailSendException gửi không được (SMTP không phản hồi, địa chỉ sai...) */
	public void send(String to, String subject, String htmlBody) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			helper.setFrom(properties.from());
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(htmlBody, true);
			mailSender.send(message);
		}
		catch (MessagingException | MailException e) {
			throw new EmailSendException("Không gửi được email: " + e.getClass().getSimpleName(), e);
		}
	}

}
