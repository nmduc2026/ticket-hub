package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ndz.catalog.entity.enums.ImageType;

class ImageTypeTests {

	@Test
	void detectsJpegPngAndWebpBySignature() {
		assertThat(ImageType.detect(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0 })).contains(ImageType.JPEG);
		assertThat(ImageType.detect(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }))
				.contains(ImageType.PNG);
		assertThat(ImageType.detect("RIFF\0\0\0\0WEBP".getBytes())).contains(ImageType.WEBP);
	}

	@Test
	void riffWithoutWebpIsNotAnImage() {
		assertThat(ImageType.detect("RIFF\0\0\0\0WAVE".getBytes())).isEmpty();
	}

	@Test
	void shortOrUnknownContentIsNotAnImage() {
		assertThat(ImageType.detect(new byte[0])).isEmpty();
		assertThat(ImageType.detect(new byte[] { (byte) 0xFF, (byte) 0xD8 })).isEmpty();
		assertThat(ImageType.detect("GIF89a".getBytes())).isEmpty();
		assertThat(ImageType.detect("<svg></svg>".getBytes())).isEmpty();
	}

}
