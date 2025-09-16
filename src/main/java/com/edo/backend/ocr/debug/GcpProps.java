package com.edo.backend.ocr.debug;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "gcp")
public record GcpProps(String projectId, String bucket) {
}
