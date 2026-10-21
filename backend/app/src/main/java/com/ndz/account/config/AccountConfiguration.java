package com.ndz.account.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

import com.ndz.account.client.KeycloakAdminClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountProperties.class)
class AccountConfiguration {

	@Bean
	KeycloakAdminClient keycloakAdminClient(AccountProperties properties, Clock clock) {
		AccountProperties.Keycloak keycloak = properties.keycloak();
		var factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(keycloak.timeout()).build());
		factory.setReadTimeout(keycloak.timeout());
		RestClient rest = RestClient.builder().requestFactory(factory).baseUrl(keycloak.baseUrl()).build();
		return new KeycloakAdminClient(rest, keycloak, clock);
	}

}
