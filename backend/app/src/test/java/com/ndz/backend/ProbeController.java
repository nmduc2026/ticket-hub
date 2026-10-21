package com.ndz.backend;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.backend.shared.exception.BusinessException;
import com.ndz.backend.shared.exception.ErrorCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Endpoint chỉ có trong test, để kích hoạt từng loại lỗi và log. */
@RestController
@RequestMapping("/test-probe")
class ProbeController {

	private static final Logger log = LoggerFactory.getLogger(ProbeController.class);

	@GetMapping("/log")
	String log() {
		log.info("probe-log-line");
		return "ok";
	}

	@GetMapping("/business-error")
	void businessError() {
		throw new BusinessException(ErrorCode.SEAT_UNAVAILABLE, "Ghế vừa có người giữ");
	}

	@GetMapping("/business-error-default-status")
	void businessErrorDefaultStatus() {
		throw new BusinessException(ErrorCode.MAX_SEATS_EXCEEDED, "Quá số ghế tối đa");
	}

	@PostMapping("/validate")
	void validate(@Valid @RequestBody Payload payload) {
	}

	@GetMapping("/boom")
	void boom() {
		throw new IllegalStateException("chi tiết nội bộ không được lộ ra ngoài");
	}

	record Payload(@NotBlank String name, @Size(max = 6) List<String> seats) {
	}

}
