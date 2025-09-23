package com.edo.backend.stt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stt")
public class SttController {

    private final SttService sttService;

    @PostMapping("/sync")
    public ResponseEntity<TranscriptResponse> transcribeSync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "ko-KR") String languageCode
    ) throws Exception {
        String text = sttService.transcribeShort(file.getBytes(), languageCode);
        return ResponseEntity.ok(new TranscriptResponse(text));
    }

    @Data
    @AllArgsConstructor
    static class TranscriptResponse { private String transcript; }
}
