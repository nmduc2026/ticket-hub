package com.ndz.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class AccountWebConfiguration implements WebMvcConfigurer {

	private final ProfileSyncInterceptor profileSync;

	AccountWebConfiguration(ProfileSyncInterceptor profileSync) {
		this.profileSync = profileSync;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(profileSync).addPathPatterns("/api/**");
	}

}
