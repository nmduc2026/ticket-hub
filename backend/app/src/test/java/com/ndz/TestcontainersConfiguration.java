package com.ndz;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

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

}
