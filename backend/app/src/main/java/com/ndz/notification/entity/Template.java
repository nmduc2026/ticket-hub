package com.ndz.notification.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Mẫu thông báo theo (mã, kênh); {@code subject} và {@code bodyTemplate} là mẫu Thymeleaf (overview.md §6.5). */
@Entity
@Table(schema = "notification", name = "templates")
public class Template {

	@EmbeddedId
	private TemplateId id;

	private String subject;

	@Column(name = "body_template")
	private String bodyTemplate;

	protected Template() {
	}

	public String getSubject() {
		return subject;
	}

	public String getBodyTemplate() {
		return bodyTemplate;
	}

	@Embeddable
	public static class TemplateId implements Serializable {

		private String code;

		private String channel;

		protected TemplateId() {
		}

		public TemplateId(String code, String channel) {
			this.code = code;
			this.channel = channel;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof TemplateId that && Objects.equals(code, that.code)
					&& Objects.equals(channel, that.channel);
		}

		@Override
		public int hashCode() {
			return Objects.hash(code, channel);
		}

	}

}
