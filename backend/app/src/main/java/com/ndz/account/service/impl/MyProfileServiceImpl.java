package com.ndz.account.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.account.client.KeycloakAdminClient;
import com.ndz.account.client.KeycloakAdminClient.UserSession;
import com.ndz.account.dto.request.ProfileRequests.UpdateProfile;
import com.ndz.account.dto.response.ProfileResponses.AvatarResponse;
import com.ndz.account.dto.response.ProfileResponses.ProfileResponse;
import com.ndz.account.dto.response.ProfileResponses.Province;
import com.ndz.account.dto.response.ProfileResponses.SessionResponse;
import com.ndz.account.entity.UserProfile;
import com.ndz.account.repository.UserProfileRepository;
import com.ndz.account.service.MyProfileService;
import com.ndz.catalog.api.CatalogApi;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.storage.ImageType;
import com.ndz.shared.storage.ObjectStorage;
import com.ndz.shared.storage.StorageProperties;
import com.ndz.shared.util.CurrentUser;
import com.ndz.shared.util.Uuid7;
import com.ndz.shared.util.VietnamesePhone;
import com.ndz.ticket.api.TicketApi;

@Service
public class MyProfileServiceImpl implements MyProfileService {

	private static final Logger log = LoggerFactory.getLogger(MyProfileServiceImpl.class);

	private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s.\\-]");
	private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);
	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	private final UserProfileRepository profiles;
	private final KeycloakAdminClient keycloak;
	private final CatalogApi catalog;
	private final TicketApi tickets;
	private final ObjectStorage storage;
	private final StorageProperties storageProperties;
	private final TransactionTemplate transaction;
	private final Clock clock;

	MyProfileServiceImpl(UserProfileRepository profiles, KeycloakAdminClient keycloak, CatalogApi catalog,
			TicketApi tickets, ObjectStorage storage, StorageProperties storageProperties,
			TransactionTemplate transaction, Clock clock) {
		this.profiles = profiles;
		this.keycloak = keycloak;
		this.catalog = catalog;
		this.tickets = tickets;
		this.storage = storage;
		this.storageProperties = storageProperties;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public ProfileResponse get(UUID userId) {
		return transaction.execute(status -> toResponse(profile(userId)));
	}

	@Override
	public ProfileResponse update(UUID userId, UpdateProfile request) {
		String phone = normalizePhone(request.phone());
		validateDateOfBirth(request.dateOfBirth());
		if (request.provinceId() != null && catalog.provinceName(request.provinceId()).isEmpty()) {
			throw new BusinessException(ErrorCode.PROVINCE_NOT_FOUND, "Không tìm thấy tỉnh / thành");
		}
		return transaction.execute(status -> {
			UserProfile profile = profile(userId);
			profile.changeDetails(phone, request.dateOfBirth(), request.provinceId(), clock.instant());
			return toResponse(profiles.save(profile));
		});
	}

	@Override
	public AvatarResponse uploadAvatar(UUID userId, byte[] content) {
		ImageType type = ImageType.validate(content, storageProperties.maxAvatarSize());
		transaction.executeWithoutResult(status -> profile(userId));
		String key = "avatars/%s/%s.%s".formatted(userId, Uuid7.next(clock), type.extension());
		storage.put(key, content, type.contentType());
		String url = storage.publicUrl(key);
		String previous;
		try {
			previous = transaction.execute(status -> {
				UserProfile profile = profile(userId);
				String old = profile.changeAvatar(url, clock.instant());
				profiles.save(profile);
				return old;
			});
		}
		catch (RuntimeException e) {
			removeQuietly(storage.keyOf(url), "ảnh vừa ghi sau khi lưu hồ sơ thất bại");
			throw e;
		}
		if (previous != null) {
			removeQuietly(storage.keyOf(previous), "ảnh đại diện cũ");
		}
		return new AvatarResponse(url);
	}

	@Override
	public List<SessionResponse> sessions(CurrentUser user) {
		return keycloak.sessions(user.id()).stream()
				.map(session -> new SessionResponse(session.id(), session.ipAddress(), session.startedAt(),
						session.lastAccessAt(), session.id().equals(user.sessionId())))
				.toList();
	}

	@Override
	public void endSession(CurrentUser user, String sessionId) {
		if (sessionId.equals(user.sessionId())) {
			throw new BusinessException(ErrorCode.CANNOT_END_CURRENT_SESSION,
					"Không kết thúc phiên đang dùng ở đây, hãy đăng xuất");
		}
		// Chỉ kết thúc phiên của chính mình: id phiên lạ (của người khác hoặc đã hết) trả 404 như nhau.
		boolean mine = keycloak.sessions(user.id()).stream().map(UserSession::id).anyMatch(sessionId::equals);
		if (!mine) {
			throw new BusinessException(ErrorCode.SESSION_NOT_FOUND, "Không tìm thấy phiên đăng nhập");
		}
		keycloak.endSession(sessionId);
		log.info("Người dùng {} kết thúc một phiên đăng nhập", user.id());
	}

	@Override
	public void deleteAccount(UUID userId, String confirmEmail) {
		UserProfile current = transaction.execute(status -> profile(userId));
		if (current.getEmail() == null || !current.getEmail().equalsIgnoreCase(confirmEmail.strip())) {
			throw new BusinessException(ErrorCode.CONFIRM_EMAIL_MISMATCH, "Email xác nhận không khớp với tài khoản");
		}
		if (tickets.hasUpcomingTickets(userId, clock.instant())) {
			throw new BusinessException(ErrorCode.ACCOUNT_HAS_UPCOMING_TICKETS,
					"Tài khoản còn vé của sự kiện chưa diễn ra");
		}
		// Xoá ở Keycloak trước (ngoài transaction DB): lỗi ở đây thì hồ sơ còn nguyên, khách thử lại được. Keycloak đã
		// xoá mà ẩn danh hoá lỗi thì gọi lại vẫn chạy tiếp vì xoá user đã xoá được coi là xong.
		keycloak.deleteUser(userId);
		String avatar = transaction.execute(status -> {
			UserProfile profile = profile(userId);
			String old = profile.anonymize(clock.instant());
			profiles.save(profile);
			return old;
		});
		if (avatar != null) {
			removeQuietly(storage.keyOf(avatar), "ảnh đại diện của tài khoản đã xoá");
		}
		log.info("Đã xoá tài khoản {} (Keycloak) và ẩn danh hoá hồ sơ", userId);
	}

	private UserProfile profile(UUID userId) {
		return profiles.findById(userId).filter(found -> found.getDeletedAt() == null)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Không tìm thấy hồ sơ"));
	}

	private ProfileResponse toResponse(UserProfile profile) {
		Province province = profile.getProvinceId() == null ? null
				: catalog.provinceName(profile.getProvinceId()).map(name -> new Province(profile.getProvinceId(), name))
						.orElse(null);
		return new ProfileResponse(profile.getUserId(), profile.getEmail(), profile.getFullName(),
				profile.getAvatarUrl(), profile.getPhone(), profile.getDateOfBirth(), province);
	}

	private static String normalizePhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return null;
		}
		String normalized = PHONE_SEPARATORS.matcher(phone).replaceAll("");
		if (!VietnamesePhone.isValid(normalized)) {
			throw new BusinessException(ErrorCode.PHONE_INVALID, "Số điện thoại không hợp lệ");
		}
		return normalized;
	}

	private void validateDateOfBirth(LocalDate dateOfBirth) {
		if (dateOfBirth == null) {
			return;
		}
		LocalDate today = LocalDate.ofInstant(clock.instant(), VIETNAM);
		if (dateOfBirth.isAfter(today) || dateOfBirth.isBefore(EARLIEST_BIRTH_DATE)) {
			throw new BusinessException(ErrorCode.DATE_OF_BIRTH_INVALID, "Ngày sinh không hợp lệ");
		}
	}

	/** Xoá không làm hỏng kết quả chính: file mồ côi chỉ tốn chỗ, nhưng phải để lại dấu vết trong log. */
	private void removeQuietly(String key, String what) {
		if (key == null) {
			return;
		}
		try {
			storage.delete(key);
		}
		catch (RuntimeException e) {
			log.warn("Không xoá được {} ({}): {}", what, key, e.getMessage());
		}
	}

}
