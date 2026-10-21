package com.ndz.shared.storage;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Cấu hình kho file S3 (overview.md §21.1).
 *
 * @param endpoint ghi đè địa chỉ S3 (S3Mock khi dev); để trống thì dùng S3 của AWS
 * @param region region của bucket
 * @param bucket tên bucket
 * @param publicBaseUrl phần đầu địa chỉ công khai của đối tượng, không có dấu "/" ở cuối (S3Mock hoặc CloudFront)
 * @param accessKey khoá truy cập tĩnh cho dev; để trống thì dùng chuỗi credentials mặc định (IAM role)
 * @param secretKey khoá bí mật đi kèm {@code accessKey}
 * @param maxBannerSize dung lượng tối đa của ảnh banner
 */
@ConfigurationProperties("tickethub.storage")
public record StorageProperties(URI endpoint, @DefaultValue("ap-southeast-1") String region,
		@DefaultValue("tickethub-media") String bucket, String publicBaseUrl, String accessKey, String secretKey,
		@DefaultValue("2MB") DataSize maxBannerSize) {
}
