package com.edo.backend.ocr.debug;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {
    private final GcpProps props;

    @GetMapping("/gcp")
    public Map<String, Object> gcp() {
        Map<String, Object> out = new HashMap<>();
        out.put("projectId", props.projectId());   // null 허용
        out.put("bucket", props.bucket());         // null 허용
        out.put("googleApplicationCredentials", System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));
        return out;                                // HashMap은 null 허용
    }
}
