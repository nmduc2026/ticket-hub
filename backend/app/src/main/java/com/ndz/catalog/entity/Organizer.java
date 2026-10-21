package com.ndz.catalog.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import com.ndz.catalog.entity.enums.OrganizerStatus;
import com.ndz.catalog.entity.enums.OrganizerType;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/**
 * Organizer và hồ sơ đăng ký (overview.md §6.1, §7.6). Máy trạng thái hồ sơ nằm ở đây: nộp → {@code PENDING};
 * Admin duyệt → {@code APPROVED}; từ chối → {@code REJECTED} (nộp lại được, về {@code PENDING}).
 */
@Entity
@Table(schema = "catalog", name = "organizers")
@AuditTable(schema = "catalog", value = "organizers_aud")
public class Organizer {

	@Id
	private UUID id;

	@Audited
	@Column(nullable = false)
	private String displayName;

	@Audited
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrganizerStatus status;

	@Audited
	private Instant approvedAt;

	@Audited
	private String rejectionReason;

	@Audited
	private boolean payoutReady;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	private OrganizerType organizerType;

	private String legalName;

	private String legalId;

	private String contactEmail;

	private String contactPhone;

	private String website;

	private String introduction;

	private String eventScale;

	private Instant submittedAt;

	@Version
	private Long version;

	protected Organizer() {
	}

	/** Thông tin khách khai khi đăng ký; đã được chuẩn hoá và kiểm ở service. */
	public record Application(OrganizerType type, String displayName, String legalName, String legalId,
			String contactEmail, String contactPhone, String website, String introduction, String eventScale) {
	}

	public static Organizer apply(UUID userId, Application application, Instant now) {
		Organizer organizer = new Organizer();
		organizer.id = userId;
		organizer.createdAt = now;
		organizer.fill(application, now);
		return organizer;
	}

	/**
	 * Nộp hồ sơ.
	 *
	 * @throws BusinessException {@code APPLICATION_ALREADY_PENDING} khi đang chờ duyệt, {@code ALREADY_ORGANIZER}
	 *         khi đã là organizer
	 */
	public void resubmit(Application application, Instant now) {
		switch (status) {
			case PENDING -> throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PENDING,
					"Hồ sơ của bạn đang chờ duyệt");
			case APPROVED, SUSPENDED -> throw new BusinessException(ErrorCode.ALREADY_ORGANIZER,
					"Bạn đã là ban tổ chức");
			case REJECTED -> fill(application, now);
		}
	}

	public void approve(Instant now) {
		requirePending();
		this.status = OrganizerStatus.APPROVED;
		this.approvedAt = now;
		this.rejectionReason = null;
	}

	public void reject(String reason) {
		requirePending();
		this.status = OrganizerStatus.REJECTED;
		this.rejectionReason = reason;
	}

	public boolean isPending() {
		return status == OrganizerStatus.PENDING;
	}

	private void requirePending() {
		if (status != OrganizerStatus.PENDING) {
			throw new BusinessException(ErrorCode.ORGANIZER_NOT_PENDING, "Hồ sơ không còn ở trạng thái chờ duyệt");
		}
	}

	private void fill(Application application, Instant now) {
		this.status = OrganizerStatus.PENDING;
		this.rejectionReason = null;
		this.displayName = application.displayName();
		this.organizerType = application.type();
		this.legalName = application.legalName();
		this.legalId = application.legalId();
		this.contactEmail = application.contactEmail();
		this.contactPhone = application.contactPhone();
		this.website = application.website();
		this.introduction = application.introduction();
		this.eventScale = application.eventScale();
		this.submittedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public OrganizerStatus getStatus() {
		return status;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public OrganizerType getOrganizerType() {
		return organizerType;
	}

	public String getLegalName() {
		return legalName;
	}

	public String getLegalId() {
		return legalId;
	}

	public String getContactEmail() {
		return contactEmail;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public String getWebsite() {
		return website;
	}

	public String getIntroduction() {
		return introduction;
	}

	public String getEventScale() {
		return eventScale;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

}
