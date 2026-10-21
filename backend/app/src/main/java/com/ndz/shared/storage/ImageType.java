package com.ndz.shared.storage;

import java.util.Optional;

import org.springframework.util.unit.DataSize;

import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/**
 * Loại ảnh tải lên được chấp nhận (banner, ảnh đại diện), nhận diện bằng chữ ký đầu file chứ không tin {@code Content-Type} hay tên file do
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

	/**
	 * Kiểm ảnh tải lên: không rỗng, không quá {@code maxSize}, đúng một trong các loại nhận.
	 *
	 * @throws BusinessException {@code EMPTY_FILE}, {@code IMAGE_TOO_LARGE} hoặc {@code UNSUPPORTED_IMAGE_TYPE}
	 */
	public static ImageType validate(byte[] content, DataSize maxSize) {
		if (content.length == 0) {
			throw new BusinessException(ErrorCode.EMPTY_FILE, "File rỗng");
		}
		if (content.length > maxSize.toBytes()) {
			throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE, "Ảnh vượt quá " + maxSize.toMegabytes() + " MB");
		}
		return detect(content).orElseThrow(
				() -> new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE, "Chỉ nhận ảnh jpg, png hoặc webp"));
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
