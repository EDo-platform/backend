package com.edo.backend.ocr.recognition;

import com.edo.backend.fileuplaod.FileStorageService;
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

    @PostMapping(value = "/ocr", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> ocr(@RequestBody OcrRequest req) throws Exception {
        var meta = fileStorageService.getMeta(req.getFileId());
        byte[] bytes = Files.readAllBytes(Path.of(meta.getStoragePath()));

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            ByteString content = ByteString.copyFrom(bytes);
            Image img = Image.newBuilder().setContent(content).build();
            ImageContext ctx = ImageContext.newBuilder().addLanguageHints("ko").build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .setImage(img).addFeatures(feat).setImageContext(ctx).build();

            AnnotateImageResponse res = client.batchAnnotateImages(List.of(request)).getResponses(0);
            if (res.hasError()) {
                return Map.of("ok", false, "error", res.getError().getMessage());
            }
            String text = res.hasFullTextAnnotation() ? res.getFullTextAnnotation().getText() : "";
            return Map.of(
                    "ok", true,
                    "fileId", meta.getId(),
                    "len", text.length(),
                    "preview", text.length() > 300 ? text.substring(0, 300) : text
            );
        }
    }
}