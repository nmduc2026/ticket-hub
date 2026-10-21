package com.ndz.shared.storage;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Cài đặt {@link ObjectStorage} bằng AWS SDK v2. */
final class S3ObjectStorage implements ObjectStorage {

	private final S3Client s3;
	private final String bucket;
	private final String publicBaseUrl;

	S3ObjectStorage(S3Client s3, String bucket, String publicBaseUrl) {
		this.s3 = s3;
		this.bucket = bucket;
		this.publicBaseUrl = publicBaseUrl;
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		try {
			s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
					RequestBody.fromBytes(content));
		}
		catch (SdkException e) {
			throw new StorageException("Không ghi được đối tượng " + key, e);
		}
	}

	@Override
	public void delete(String key) {
		try {
			s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		}
		catch (SdkException e) {
			throw new StorageException("Không xoá được đối tượng " + key, e);
		}
	}

	@Override
	public String publicUrl(String key) {
		return publicBaseUrl + "/" + key;
	}

	@Override
	public String keyOf(String publicUrl) {
		String prefix = publicBaseUrl + "/";
		return publicUrl != null && publicUrl.startsWith(prefix) ? publicUrl.substring(prefix.length()) : null;
	}

}
