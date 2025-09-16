package com.edo.backend.ocr.debug;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GcpProps.class)
public class PropsConfig {
}
