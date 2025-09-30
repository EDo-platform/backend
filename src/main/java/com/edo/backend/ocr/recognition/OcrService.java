package com.edo.backend.ocr.recognition;

import com.edo.backend.fileuplaod.FileMetadata;
import com.edo.backend.fileuplaod.FileStorageService;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OcrService {

    private final FileStorageService fileStorageService;
    private final GoogleCredentials googleCredentials;

    public String extractText(String fileId, String lang) throws Exception {
        FileMetadata meta = fileStorageService.getMeta(fileId);
        if (meta == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found");
        }

        String contentType = Optional.ofNullable(meta.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        byte[] bytes = meta.getData();
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file data");
        }

        try {
            if (contentType.startsWith("image/") || contentType.isBlank()) {
                // ✅ 이미지: Vision OCR
                return runGcpOcr(bytes, lang);
            } else if (contentType.startsWith("text/") || contentType.equals("application/json")) {
                // ✅ 텍스트/JSON: 그대로 디코딩
                return decodeText(bytes, contentType);
            } else if ("application/pdf".equals(contentType)) {
                // ✅ PDF: 임베디드 텍스트 추출
                String txt = extractPdfText(bytes);
                return txt == null ? "" : txt;
            } else {
                // 기타 형식은 명시적으로 거부
                throw new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "unsupported contentType for text extraction: " + contentType
                );
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // 디버깅에 도움되도록 context 포함
            throw new RuntimeException("Text extraction failed (contentType=" + contentType + ", size=" + bytes.length + "): " + e.getMessage(), e);
        }
    }

    /* ---------------- Vision OCR ---------------- */

    private String runGcpOcr(byte[] imageBytes, String lang) throws Exception {
        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                .build();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create(settings)) {
            ByteString content = ByteString.copyFrom(imageBytes);
            Image img = Image.newBuilder().setContent(content).build();

            ImageContext.Builder ctxBuilder = ImageContext.newBuilder();
            if (lang != null && !lang.isBlank()) {
                ctxBuilder.addLanguageHints(lang);
            }

            Feature feat = Feature.newBuilder()
                    .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .setImage(img)
                    .addFeatures(feat)
                    .setImageContext(ctxBuilder.build())
                    .build();

            AnnotateImageResponse res = client.batchAnnotateImages(List.of(request)).getResponses(0);

            if (res.hasError()) {
                throw new RuntimeException("OCR failed: " + res.getError().getMessage());
            }
            return res.hasFullTextAnnotation() ? res.getFullTextAnnotation().getText() : "";
        }
    }

    /* ---------------- Text decoding ---------------- */

    private String decodeText(byte[] bytes, String contentType) {
        Charset cs = charsetFromContentType(contentType);
        return new String(bytes, cs);
    }

    private Charset charsetFromContentType(String contentType) {
        try {
            String[] parts = contentType.split(";");
            for (String p : parts) {
                String s = p.trim().toLowerCase(Locale.ROOT);
                if (s.startsWith("charset=")) {
                    String enc = s.substring("charset=".length()).trim().replace("\"", "");
                    return Charset.forName(enc);
                }
            }
        } catch (Exception ignore) {}
        return StandardCharsets.UTF_8; // 기본값
    }

    /* ---------------- PDF text extraction ---------------- */

    private String extractPdfText(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }
}
