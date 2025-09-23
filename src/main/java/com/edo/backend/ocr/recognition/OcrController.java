package com.edo.backend.ocr.recognition;

import com.edo.backend.fileuplaod.FileStorageService;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OcrController {

    private final OcrService ocrService;

    @PostMapping(value = "/ocr", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> ocr(@RequestBody OcrRequest req) throws Exception {
        String text = ocrService.extractText(req.getFileId(), "ko");
        return Map.of(
                "ok", true,
                "fileId", req.getFileId(),
                "len", text.length(),
                "preview", text.length() > 300 ? text.substring(0, 300) : text
        );
    }
}