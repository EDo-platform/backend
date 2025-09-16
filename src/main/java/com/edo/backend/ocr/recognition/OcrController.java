package com.edo.backend.ocr.recognition;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OcrController {

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> ocr(@RequestPart("file") MultipartFile file) throws Exception {
        Map<String, Object> out = new HashMap<>();

        if (file == null || file.isEmpty()) {
            out.put("ok", false);
            out.put("error", "file is empty");
            return out;
        }

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            ByteString content = ByteString.copyFrom(file.getBytes());
            Image img = Image.newBuilder().setContent(content).build();

            // 문서 OCR 권장 + 한글 힌트(선택)
            ImageContext ctx = ImageContext.newBuilder().addLanguageHints("ko").build();
            Feature feat = Feature.newBuilder()
                    .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .setImage(img)
                    .addFeatures(feat)
                    .setImageContext(ctx)
                    .build();

            AnnotateImageResponse res = client.batchAnnotateImages(List.of(request)).getResponses(0);

            if (res.hasError()) {
                out.put("ok", false);
                out.put("error", res.getError().getMessage());
                return out;
            }

            String text = null;
            if (res.hasFullTextAnnotation()) {
                text = res.getFullTextAnnotation().getText();
            }

            out.put("ok", true);
            out.put("len", text == null ? 0 : text.length());
            out.put("preview", (text == null) ? "" : (text.length() > 300 ? text.substring(0, 300) : text));
            return out;
        }
    }
}