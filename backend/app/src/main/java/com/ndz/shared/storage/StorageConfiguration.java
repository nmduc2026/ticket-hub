package com.ndz.shared.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfiguration {

	@Bean(destroyMethod = "close")
	S3Client s3Client(StorageProperties properties) {
		var builder = S3Client.builder().region(Region.of(properties.region()))
				.credentialsProvider(credentials(properties))
				// S3Mock và các kho tương thích S3 dùng địa chỉ dạng path (host/bucket/key).
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.endpoint() != null)
						.build());
		if (properties.endpoint() != null) {
			builder.endpointOverride(properties.endpoint());
		}
		return builder.build();
	}

	@Bean
	ObjectStorage objectStorage(S3Client s3, StorageProperties properties) {
		return new S3ObjectStorage(s3, properties.bucket(), properties.publicBaseUrl());
	}

	/** Khoá tĩnh chỉ dành cho dev (S3Mock); môi trường thật dùng IAM role qua chuỗi credentials mặc định. */
	private static AwsCredentialsProvider credentials(StorageProperties properties) {
		if (StringUtils.hasText(properties.accessKey()) && StringUtils.hasText(properties.secretKey())) {
			return StaticCredentialsProvider
					.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
		}
		return DefaultCredentialsProvider.builder().build();
	}

}
