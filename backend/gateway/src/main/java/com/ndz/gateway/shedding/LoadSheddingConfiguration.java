package com.ndz.gateway.shedding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoadSheddingProperties.class)
class LoadSheddingConfiguration {

}
