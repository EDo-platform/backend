package com.edo.backend.fileuplaod;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    // 파일 업로드 api
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "file is empty"));
        }
        FileMetadata meta = fileStorageService.store(file);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "fileId", meta.getOriginalName(),
                "size", meta.getSize(),
                "contentType", meta.getContentType()
        ));
    }

    @GetMapping("files/{fileId")
    public ResponseEntity<Resource> download(@PathVariable String fileId) throws Exception {
        FileMetadata meta = fileStorageService.getMeta(fileId);
        Resource res = fileStorageService.load(fileId);

        // 한글 파일명 처리
        String encoded = URLEncoder.encode(meta.getOriginalName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        Optional.ofNullable(meta.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(res);
    }


}
