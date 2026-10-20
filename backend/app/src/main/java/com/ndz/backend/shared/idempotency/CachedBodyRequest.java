package com.ndz.backend.shared.idempotency;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** Request đã đọc sẵn body vào bộ nhớ: filter cần body để tính hash, còn controller vẫn đọc lại được. */
final class CachedBodyRequest extends HttpServletRequestWrapper {

	private final byte[] body;

	CachedBodyRequest(HttpServletRequest request, byte[] body) {
		super(request);
		this.body = body;
	}

	byte[] body() {
		return body;
	}

	@Override
	public ServletInputStream getInputStream() {
		ByteArrayInputStream source = new ByteArrayInputStream(body);
		return new ServletInputStream() {
			@Override
			public int read() {
				return source.read();
			}

			@Override
			public int read(byte[] buffer, int offset, int length) {
				return source.read(buffer, offset, length);
			}

			@Override
			public boolean isFinished() {
				return source.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener listener) {
				throw new UnsupportedOperationException("Không hỗ trợ đọc bất đồng bộ");
			}
		};
	}

	@Override
	public BufferedReader getReader() throws IOException {
		Charset charset = getCharacterEncoding() != null ? Charset.forName(getCharacterEncoding())
				: StandardCharsets.UTF_8;
		return new BufferedReader(new InputStreamReader(getInputStream(), charset));
	}

}
