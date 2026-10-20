package com.ndz.backend.catalog.internal.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.springframework.http.HttpStatus;

import com.ndz.backend.shared.BusinessException;

/**
 * Sự kiện (overview.md §6.1). Quy tắc khoá sau publish (BR-14) nằm ở đây để mọi đường sửa đều đi qua cùng một chỗ;
 * chuyển trạng thái publish / bán / tạm dừng thêm ở hạng mục publish.
 */
@Entity
@Table(schema = "catalog", name = "events")
public class Event {

	/** Trạng thái còn cho sửa thông tin hiển thị; sự kiện đã kết thúc hoặc đã huỷ thì khoá hẳn. */
	private static final Set<EventStatus> DISPLAY_EDITABLE = EnumSet.of(EventStatus.DRAFT, EventStatus.PUBLISHED,
			EventStatus.ON_SALE, EventStatus.SUSPENDED);

	@Id
	private UUID id;

	@Column(name = "organizer_id", nullable = false, updatable = false)
	private UUID organizerId;

	@Column(name = "venue_id")
	private UUID venueId;

	@Column(name = "seat_map_id")
	private UUID seatMapId;

	private String title;

	private String description;

	private String category;

	@Column(name = "banner_url")
	private String bannerUrl;

	@Column(name = "starts_at")
	private Instant startsAt;

	@Column(name = "ends_at")
	private Instant endsAt;

	@Column(name = "sale_starts_at")
	private Instant saleStartsAt;

	@Enumerated(EnumType.STRING)
	private EventStatus status;

	@Column(name = "high_demand")
	private boolean highDemand;

	@Column(name = "max_tickets_per_user")
	private short maxTicketsPerUser;

	@Column(name = "platform_fee_bps")
	private Integer platformFeeBps;

	@Column(name = "inventory_ready")
	private boolean inventoryReady;

	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	protected Event() {
	}

	public static Event draft(UUID id, UUID organizerId, EventContent content, Instant now) {
		Event event = new Event();
		event.id = id;
		event.organizerId = organizerId;
		event.status = EventStatus.DRAFT;
		event.createdAt = now;
		event.applySalesInfo(content);
		event.applyDisplayInfo(content);
		return event;
	}

	/**
	 * Cập nhật theo nội dung đầy đủ organizer gửi. Nháp sửa được mọi thứ; sau publish chỉ đổi thông tin hiển thị,
	 * thông tin bán vé khác bản đang lưu bị từ chối (BR-14).
	 */
	public void update(EventContent content) {
		if (!DISPLAY_EDITABLE.contains(status)) {
			throw locked("Sự kiện đã kết thúc hoặc đã huỷ, không sửa được");
		}
		if (status != EventStatus.DRAFT && !content.sameSalesInfoAs(this)) {
			// BR-14: giờ diễn, giờ mở bán, địa điểm, sơ đồ ghế, giới hạn vé bị khoá sau publish
			throw locked("Sau khi publish chỉ sửa được tiêu đề, mô tả, ảnh banner và danh mục");
		}
		if (status == EventStatus.DRAFT) {
			applySalesInfo(content);
		}
		applyDisplayInfo(content);
	}

	/** Đổi ảnh banner: là thông tin hiển thị nên sửa được sau publish. */
	public void changeBanner(String bannerUrl) {
		if (!DISPLAY_EDITABLE.contains(status)) {
			throw locked("Sự kiện đã kết thúc hoặc đã huỷ, không sửa được");
		}
		this.bannerUrl = bannerUrl;
	}

	/** Hạng vé, giá và gán khu chỉ thay đổi được khi còn là nháp (BR-14). */
	public void requireDraftForSalesSetup() {
		if (status != EventStatus.DRAFT) {
			throw locked("Sau khi publish không sửa được hạng vé và giá (BR-14)");
		}
	}

	private void applySalesInfo(EventContent content) {
		this.venueId = content.venueId();
		this.seatMapId = content.seatMapId();
		this.startsAt = content.startsAt();
		this.endsAt = content.endsAt();
		this.saleStartsAt = content.saleStartsAt();
		this.maxTicketsPerUser = content.maxTicketsPerUser();
	}

	private void applyDisplayInfo(EventContent content) {
		this.title = content.title();
		this.description = content.description();
		this.category = content.category();
	}

	private static BusinessException locked(String message) {
		return new BusinessException("EVENT_LOCKED", message, HttpStatus.CONFLICT);
	}

	public boolean isOwnedBy(UUID userId) {
		return Objects.equals(organizerId, userId);
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrganizerId() {
		return organizerId;
	}

	public UUID getVenueId() {
		return venueId;
	}

	public UUID getSeatMapId() {
		return seatMapId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getCategory() {
		return category;
	}

	public String getBannerUrl() {
		return bannerUrl;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public Instant getSaleStartsAt() {
		return saleStartsAt;
	}

	public EventStatus getStatus() {
		return status;
	}

	public boolean isHighDemand() {
		return highDemand;
	}

	public short getMaxTicketsPerUser() {
		return maxTicketsPerUser;
	}

	public Integer getPlatformFeeBps() {
		return platformFeeBps;
	}

	public boolean isInventoryReady() {
		return inventoryReady;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
