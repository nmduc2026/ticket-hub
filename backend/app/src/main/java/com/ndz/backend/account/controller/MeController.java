package com.ndz.backend.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.backend.account.service.UserProfileService;
import com.ndz.backend.account.dto.response.MeResponse;
import com.ndz.backend.account.mapper.UserProfileMapper;
import com.ndz.backend.shared.util.CurrentUser;

@RestController
@RequestMapping("/api/v1/me")
class MeController {

	private final UserProfileService profiles;
	private final UserProfileMapper mapper;

	MeController(UserProfileService profiles, UserProfileMapper mapper) {
		this.profiles = profiles;
		this.mapper = mapper;
	}

	/** Hồ sơ của người đang đăng nhập (đã được đồng bộ ở interceptor trước khi vào đây). */
	@Operation(operationId = "getMe", summary = "Hồ sơ và role của người đang đăng nhập")
	@ApiResponse(responseCode = "200", description = "Hồ sơ người dùng",
			content = @Content(schema = @Schema(implementation = MeResponse.class)))
	@ApiResponse(responseCode = "401", description = "Chưa đăng nhập (UNAUTHENTICATED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping
	MeResponse me() {
		return mapper.toMeResponse(profiles.get(CurrentUser.require().id()));
	}

}
