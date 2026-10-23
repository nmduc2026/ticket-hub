package com.ndz.catalog.entity.enums;

import java.util.Arrays;
import java.util.Optional;

/** Loại import Excel (overview.md §6.1); {@code slug} là giá trị trên URL. */
public enum ImportType {

	ADMINISTRATIVE_UNITS("administrative-units", "mau-don-vi-hanh-chinh.xlsx"),
	VENUES("venues", "mau-dia-diem.xlsx"),
	SEAT_MAPS("seat-maps", "mau-so-do-ghe.xlsx");

	private final String slug;
	private final String templateFileName;

	ImportType(String slug, String templateFileName) {
		this.slug = slug;
		this.templateFileName = templateFileName;
	}

	public String slug() {
		return slug;
	}

	public String templateFileName() {
		return templateFileName;
	}

	public static Optional<ImportType> fromSlug(String slug) {
		return Arrays.stream(values()).filter(type -> type.slug.equals(slug)).findFirst();
	}

}
