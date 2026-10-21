package com.ndz.account.controller;

import java.io.IOException;
import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ndz.account.dto.request.ProfileRequests.DeleteAccount;
import com.ndz.account.dto.request.ProfileRequests.UpdateProfile;
import com.ndz.account.dto.response.ProfileResponses.AvatarResponse;
import com.ndz.account.dto.response.ProfileResponses.ProfileResponse;
import com.ndz.account.dto.response.ProfileResponses.SessionResponse;
import com.ndz.account.service.MyProfileService;
import com.ndz.shared.util.CurrentUser;

/** Hồ sơ cá nhân, phiên đăng nhập và xoá tài khoản của người đang đăng nhập (màn #19, overview.md §6.6, §11.2). */
@RestController
@RequestMapping("/api/v1/me")
class MyProfileController {

	private final MyProfileService service;

	MyProfileController(MyProfileService service) {
		this.service = service;
	}

	@Operation(operationId = "getMyProfile", summary = "Hồ sơ cá nhân")
	@ApiResponse(responseCode = "200", description = "Hồ sơ", content = @Content(schema = @Schema(implementation = ProfileResponse.class)))
	@GetMapping("/profile")
	ProfileResponse profile() {
		return service.get(CurrentUser.require().id());
	}

	@Operation(operationId = "updateMyProfile", summary = "Sửa SĐT, ngày sinh, tỉnh / thành")
	@ApiResponse(responseCode = "200", description = "Hồ sơ sau khi sửa",
			content = @Content(schema = @Schema(implementation = ProfileResponse.class)))
	@ApiResponse(responseCode = "422", description = "PHONE_INVALID, DATE_OF_BIRTH_INVALID hoặc PROVINCE_NOT_FOUND",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping("/profile")
	ProfileResponse updateProfile(@Valid @RequestBody UpdateProfile request) {
		return service.update(CurrentUser.require().id(), request);
	}

	@Operation(operationId = "uploadMyAvatar", summary = "Tải ảnh đại diện (jpg, png, webp, tối đa 2 MB)")
	@ApiResponse(responseCode = "200", description = "Địa chỉ ảnh mới",
			content = @Content(schema = @Schema(implementation = AvatarResponse.class)))
	@ApiResponse(responseCode = "422", description = "UNSUPPORTED_IMAGE_TYPE, IMAGE_TOO_LARGE hoặc EMPTY_FILE",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	AvatarResponse uploadAvatar(@RequestPart("file") MultipartFile file) throws IOException {
		return service.uploadAvatar(CurrentUser.require().id(), file.getBytes());
	}

	@Operation(operationId = "listMySessions", summary = "Phiên đăng nhập đang có")
	@ApiResponse(responseCode = "200", description = "Các phiên; phiên hiện tại có current = true")
	@GetMapping("/sessions")
	List<SessionResponse> sessions() {
		return service.sessions(CurrentUser.require());
	}

	@Operation(operationId = "endMySession", summary = "Đăng xuất một phiên khác")
	@ApiResponse(responseCode = "204", description = "Đã kết thúc phiên")
	@ApiResponse(responseCode = "404", description = "SESSION_NOT_FOUND",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "CANNOT_END_CURRENT_SESSION (dùng nút Đăng xuất)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@DeleteMapping("/sessions/{sessionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void endSession(@PathVariable String sessionId) {
		service.endSession(CurrentUser.require(), sessionId);
	}

	@Operation(operationId = "deleteMyAccount", summary = "Xoá tài khoản",
			description = "Xoá tài khoản ở Keycloak và ẩn danh hoá hồ sơ; đơn và vé cũ giữ lại để đối soát.")
	@ApiResponse(responseCode = "204", description = "Đã xoá")
	@ApiResponse(responseCode = "409", description = "ACCOUNT_HAS_UPCOMING_TICKETS",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "CONFIRM_EMAIL_MISMATCH",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteAccount(@Valid @RequestBody DeleteAccount request) {
		service.deleteAccount(CurrentUser.require().id(), request.confirmEmail());
	}

}
