package com.ndz;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	static final String KEYCLOAK_ADMIN = "admin";
	static final String ACCOUNT_ADMIN_SECRET = "account-admin-secret";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17.6"));
	}

	/** Adobe S3Mock cho test upload ảnh; bucket tạo sẵn lúc khởi động. */
	@Bean
	GenericContainer<?> s3MockContainer() {
		return new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3")).withExposedPorts(9090)
				.withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "tickethub-media")
				.waitingFor(Wait.forHttp("/").forStatusCode(200));
	}

	@Bean
	DynamicPropertyRegistrar storageProperties(GenericContainer<?> s3MockContainer) {
		return registry -> {
			String base = "http://" + s3MockContainer.getHost() + ":" + s3MockContainer.getMappedPort(9090);
			registry.add("tickethub.storage.endpoint", () -> base);
			registry.add("tickethub.storage.public-base-url", () -> base + "/tickethub-media");
		};
	}

	/** Mailpit: hộp thư thử cho test email vé (SMTP :1025, API :8025). */
	@Bean
	GenericContainer<?> mailpitContainer() {
		return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27.7")).withExposedPorts(1025, 8025)
				.waitingFor(Wait.forHttp("/readyz").forPort(8025).forStatusCode(200));
	}

	@Bean
	DynamicPropertyRegistrar mailProperties(GenericContainer<?> mailpitContainer) {
		return registry -> {
			registry.add("spring.mail.host", mailpitContainer::getHost);
			registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(1025));
		};
	}

	/**
	 * Keycloak thật với realm của dự án (infra/keycloak): module account gọi Admin API của nó để gán role và khoá tài
	 * khoản. Chạy từ thư mục module ({@code backend/app}) nên realm nằm ở {@code ../../infra/keycloak}.
	 */
	@Bean
	GenericContainer<?> keycloakContainer() {
		Path realm = Path.of("../../infra/keycloak/tickethub-realm.json").toAbsolutePath().normalize();
		return new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.3.3"))
				.withExposedPorts(8080).withCommand("start-dev", "--import-realm")
				.withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", KEYCLOAK_ADMIN)
				.withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", KEYCLOAK_ADMIN)
				.withEnv("KEYCLOAK_BFF_CLIENT_SECRET", "bff-secret")
				.withEnv("KEYCLOAK_INTERNAL_CLIENT_SECRET", "internal-secret")
				.withEnv("KEYCLOAK_ACCOUNT_ADMIN_CLIENT_SECRET", ACCOUNT_ADMIN_SECRET)
				.withCopyFileToContainer(MountableFile.forHostPath(realm),
						"/opt/keycloak/data/import/tickethub-realm.json")
				.waitingFor(Wait.forHttp("/realms/tickethub").forPort(8080).forStatusCode(200)
						.withStartupTimeout(Duration.ofMinutes(3)));
	}

	@Bean
	DynamicPropertyRegistrar keycloakProperties(GenericContainer<?> keycloakContainer) {
		return registry -> {
			registry.add("tickethub.account.keycloak.base-url",
					() -> "http://" + keycloakContainer.getHost() + ":" + keycloakContainer.getMappedPort(8080));
			registry.add("tickethub.account.keycloak.client-secret", () -> ACCOUNT_ADMIN_SECRET);
		};
	}

	@Bean
	KeycloakTestApi keycloak(GenericContainer<?> keycloakContainer) {
		return new KeycloakTestApi(
				"http://" + keycloakContainer.getHost() + ":" + keycloakContainer.getMappedPort(8080));
	}

	@Bean
	MailpitApi mailpit(GenericContainer<?> mailpitContainer) {
		return new MailpitApi("http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025));
	}

}
