package com.ndz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test tích hợp với Postgres thật (Testcontainers). Mọi test dùng cùng annotation này nên chia sẻ một context và một
 * container.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
// Job gửi lại event treo tắt trong test (interval dài): test tự gọi IncompleteEventPublications khi cần kiểm retry.
// Job điều tiết phòng chờ cũng tắt: test gọi QueueAdmissionService.admitWaiting() với đồng hồ test để kiểm từng nhịp.
@SpringBootTest(properties = { "tickethub.events.republish-interval=24h", "tickethub.queue.admit-interval=24h" })
@AutoConfigureMockMvc
// Bật xuất metric (mặc định @SpringBootTest tắt): test kiểm /actuator/prometheus.
@AutoConfigureMetrics
// `seed`: dữ liệu mẫu của catalog (organizer test, 1 địa điểm, sơ đồ 500 ghế); mọi test dùng chung một context.
@ActiveProfiles("seed")
@Import({ TestcontainersConfiguration.class, StripeStubConfiguration.class, EventRecorderConfiguration.class,
		TestSecurityConfiguration.class,
		TestClockConfiguration.class, ProbeController.class,
		AdminProbeController.class, IdempotencyProbeController.class })
public @interface IntegrationTest {

}
