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

    private final FileStorageService fileStorageService;
    private final GoogleCredentials googleCredentials;  // ✅ GcpConfig에서 주입받음

    @PostMapping(value = "/ocr", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> ocr(@RequestBody OcrRequest req) throws Exception {
        byte[] bytes = fileStorageService.getBytes(req.getFileId());

        // ✅ GoogleCredentials를 직접 주입
        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                .build();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create(settings)) {
            ByteString content = ByteString.copyFrom(bytes);
            Image img = Image.newBuilder().setContent(content).build();
            ImageContext ctx = ImageContext.newBuilder().addLanguageHints("ko").build();
            Feature feat = Feature.newBuilder()
                    .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .setImage(img)
                    .addFeatures(feat)
                    .setImageContext(ctx)
                    .build();

            AnnotateImageResponse res = client
                    .batchAnnotateImages(List.of(request))
                    .getResponses(0);

            if (res.hasError()) {
                return Map.of("ok", false, "error", res.getError().getMessage());
            }

            String text = res.hasFullTextAnnotation() ? res.getFullTextAnnotation().getText() : "";
            return Map.of(
                    "ok", true,
                    "fileId", req.getFileId(),
                    "len", text.length(),
                    "preview", text.length() > 300 ? text.substring(0, 300) : text
            );
        }
    }
}