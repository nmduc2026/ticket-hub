package com.ndz.catalog.entity.enums;

import java.util.Optional;

/**
 * Loại ảnh banner được chấp nhận, nhận diện bằng chữ ký đầu file chứ không tin {@code Content-Type} hay tên file do
 * client gửi (file đổi đuôi không qua được).
 */
public enum ImageType {

	JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp");

	private final String contentType;
	private final String extension;

	ImageType(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}

	public static Optional<ImageType> detect(byte[] bytes) {
		if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
			return Optional.of(JPEG);
		}
		if (startsWith(bytes, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
			return Optional.of(PNG);
		}
		// WebP: "RIFF" + 4 byte độ dài + "WEBP"
		if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] bytes, int offset, int... expected) {
		if (bytes.length < offset + expected.length) {
			return false;
		}
		for (int i = 0; i < expected.length; i++) {
			if ((bytes[offset + i] & 0xFF) != expected[i]) {
				return false;
			}
		}
		return true;
	}

}
