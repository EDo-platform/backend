package com.edo.backend.stt;

import com.edo.backend.ocr.debug.GcpProps;
import com.google.cloud.speech.v2.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SttService {

    private final GcpProps gcp;

    public String transcribeShort(byte[] audioBytes, String languageCode) throws Exception {
        try (SpeechClient client = SpeechClient.create()) {
            String recognizer = "projects/%s/locations/global/recognizers/_"
                    .formatted(gcp.projectId());

            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setAutoDecodingConfig(AutoDetectDecodingConfig.getDefaultInstance())
                    .addLanguageCodes(languageCode)       // "ko-KR"
                    .setModel("latest_short")             // 짧은 발화
                    .setFeatures(RecognitionFeatures.newBuilder()
                            .setEnableAutomaticPunctuation(true)
                            .build())
                    .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(audioBytes))
                    .build();

            RecognizeRequest request = RecognizeRequest.newBuilder()
                    .setRecognizer(recognizer)
                    .setConfig(config)
                    .setAudio(audio)
                    .build();

            RecognizeResponse response = client.recognize(request);

            StringBuilder sb = new StringBuilder();
            response.getResultsList().forEach(r -> {
                if (r.getAlternativesCount() > 0) {
                    sb.append(r.getAlternatives(0).getTranscript()).append(" ");
                }
            });
            return sb.toString().trim();
        }
    }
}
