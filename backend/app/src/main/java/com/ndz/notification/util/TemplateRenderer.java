package com.ndz.notification.util;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Dựng nội dung từ mẫu Thymeleaf lưu trong DB (overview.md §6.5): tiêu đề ở chế độ TEXT, thân ở chế độ HTML (Thymeleaf
 * tự escape giá trị chèn vào HTML).
 */
@Component
public class TemplateRenderer {

	private final TemplateEngine textEngine = engine(TemplateMode.TEXT);
	private final TemplateEngine htmlEngine = engine(TemplateMode.HTML);

	public String renderSubject(String template, Map<String, Object> variables) {
		return textEngine.process(template, new Context(java.util.Locale.forLanguageTag("vi-VN"), variables));
	}

	public String renderBody(String template, Map<String, Object> variables) {
		return htmlEngine.process(template, new Context(java.util.Locale.forLanguageTag("vi-VN"), variables));
	}

	private static TemplateEngine engine(TemplateMode mode) {
		StringTemplateResolver resolver = new StringTemplateResolver();
		resolver.setTemplateMode(mode);
		resolver.setCacheable(false);
		TemplateEngine engine = new SpringTemplateEngine();
		engine.setTemplateResolver(resolver);
		return engine;
	}

}
