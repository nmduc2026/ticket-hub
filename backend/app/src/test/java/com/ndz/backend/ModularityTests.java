package com.ndz.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Ranh giới module (ADR 0012, 0015): module chỉ dùng API công khai của module khác.
 */
class ModularityTests {

	@Test
	void applicationModulesAreValid() {
		ApplicationModules.of(AppApplication.class).verify();
	}

	@Test
	void applicationHasTheExpectedModules() {
		var names = ApplicationModules.of(AppApplication.class).stream()
				.map(module -> module.getIdentifier().toString())
				.toList();

		assertThat(names).containsExactlyInAnyOrder(
				"account", "catalog", "booking", "payment", "ticket", "notification", "shared");
	}

}
