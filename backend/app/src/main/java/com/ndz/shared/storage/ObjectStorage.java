package com.ndz.shared.storage;

/**
 * Lưu file đối tượng (ảnh banner, sau này file import và vé). Module nghiệp vụ chỉ biết interface này; cài đặt là S3
 * (S3Mock khi dev). Lỗi hạ tầng ném {@link StorageException} để framework xử lý (5xx), không nuốt.
 */
public interface ObjectStorage {

	/** Ghi đè nếu key đã có. */
	void put(String key, byte[] content, String contentType);

	/** Nội dung đối tượng; ném {@link StorageException} nếu không có hoặc lỗi hạ tầng. */
	byte[] get(String key);

	/** Xoá một đối tượng; không có thì coi như đã xoá. */
	void delete(String key);

	/** Địa chỉ trình duyệt dùng để tải đối tượng (CDN hoặc endpoint công khai của bucket). */
	String publicUrl(String key);

	/** Khoá của đối tượng ứng với địa chỉ công khai, hoặc null nếu địa chỉ không thuộc kho này. */
	String keyOf(String publicUrl);

}
