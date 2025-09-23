package com.edo.backend.ocr.recognition;

import com.edo.backend.fileuplaod.FileStorageService;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OcrService {

    private final FileStorageService fileStorageService;
    private final GoogleCredentials googleCredentials;

    public String extractText(String fileId, String lang) throws Exception {
        byte[] bytes = fileStorageService.getBytes(fileId);

        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                .build();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create(settings)) {
            ByteString content = ByteString.copyFrom(bytes);
            Image img = Image.newBuilder().setContent(content).build();
            ImageContext ctx = ImageContext.newBuilder().addLanguageHints(lang).build();
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
                throw new RuntimeException("OCR failed: " + res.getError().getMessage());
            }

            return res.hasFullTextAnnotation() ? res.getFullTextAnnotation().getText() : "";
        }
    }
}
