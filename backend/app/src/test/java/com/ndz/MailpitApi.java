package com.ndz;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

/** Gọi API của Mailpit (hộp thư thử) để test kiểm email thật sự đã tới. */
public class MailpitApi {

	private final String baseUrl;
	private final HttpClient http = HttpClient.newHttpClient();

	MailpitApi(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	/** Xoá mọi thư. */
	public void clear() {
		send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/messages")).DELETE().build());
	}

	/** Số thư gửi tới {@code address}. Lọc theo người nhận để test không lẫn với thư của test khác chạy bất đồng bộ. */
	public int countTo(String address) {
		return JsonPath.<Integer>read(search(address), "$.messages_count");
	}

	/** Tiêu đề các thư gửi tới {@code address}. */
	public List<String> subjectsTo(String address) {
		return JsonPath.read(search(address), "$.messages[*].Subject");
	}

	/** Nội dung HTML của thư mới nhất gửi tới {@code address}. */
	public String latestHtmlTo(String address) {
		String id = JsonPath.read(search(address), "$.messages[0].ID");
		return JsonPath.read(get("/api/v1/message/" + id), "$.HTML");
	}

	private String search(String address) {
		return get("/api/v1/search?query=" + URLEncoder.encode("to:" + address, StandardCharsets.UTF_8));
	}

	private String get(String path) {
		return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
	}

	private String send(HttpRequest request) {
		try {
			HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
			return response.body();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
