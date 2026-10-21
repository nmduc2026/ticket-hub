package com.ndz;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint chỉ có trong test, nằm sau chuỗi bảo mật thật của `/api/**`, để kiểm tra phân quyền theo role. */
@RestController
@RequestMapping("/api/v1/test-probe")
class AdminProbeController {

	@GetMapping("/admin")
	@PreAuthorize("hasRole('ADMIN')")
	String admin() {
		return "admin-only";
	}

	@GetMapping("/organizer")
	@PreAuthorize("hasRole('ORGANIZER')")
	String organizer() {
		return "organizer-only";
	}

}
