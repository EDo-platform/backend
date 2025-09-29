package com.edo.backend.stt;

import com.google.api.gax.rpc.BidiStream;
import com.google.cloud.speech.v2.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SttSocketHandler extends BinaryWebSocketHandler {

    private final SpeechClient speechClient;
    private final String recognizerName;

    public SttSocketHandler(@Value("${gcp.project-id}") String projectId) throws Exception {
        this.speechClient = SpeechClient.create();

        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("❌ GCP project-id is not set. Check env GCP_PROJECT_ID");
        }

        this.recognizerName = String.format(
                "projects/%s/locations/global/recognizers/_",
                projectId
        );

        System.out.println("✅ recognizerName = " + recognizerName);
    }

    private static class SessionCtx {
        BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse> stream;
        ExecutorService reader;
    }

    private final ConcurrentHashMap<String, SessionCtx> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 1) GCP Streaming 스트림 열기
        BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse> stream =
                speechClient.streamingRecognizeCallable().call();

        // 2) Config 전송 (먼저 1회)
        RecognitionConfig recConfig = RecognitionConfig.newBuilder()
                .setAutoDecodingConfig(AutoDetectDecodingConfig.getDefaultInstance())
                .addLanguageCodes("ko-KR")
                .setModel("latest_long")
                .setFeatures(RecognitionFeatures.newBuilder()
                        .setEnableAutomaticPunctuation(true)
                        .build())
                .build();

// ✅ v2: interim_results / VAD는 StreamingRecognitionFeatures에 설정
        StreamingRecognitionFeatures sFeatures = StreamingRecognitionFeatures.newBuilder()
                .setInterimResults(true)            // partial 결과 받기
                .setEnableVoiceActivityEvents(false) // 필요 시 true
                .build();

        StreamingRecognitionConfig streamingCfg = StreamingRecognitionConfig.newBuilder()
                .setConfig(recConfig)
                .setStreamingFeatures(sFeatures)     // ✅ 여기!
                .build();

        stream.send(StreamingRecognizeRequest.newBuilder()
                .setRecognizer(recognizerName)
                .setStreamingConfig(streamingCfg)
                .build());

        // 3) 응답 읽기 스레드
        ExecutorService reader = Executors.newSingleThreadExecutor();
        reader.submit(() -> {
            try {
                for (StreamingRecognizeResponse resp : stream) {
                    if (resp.getResultsCount() > 0) {
                        var r = resp.getResults(0);
                        var alt = r.getAlternativesCount() > 0 ? r.getAlternatives(0) : null;
                        if (alt != null) {
                            boolean isFinal = r.getIsFinal();
                            String text = alt.getTranscript();
                            var payload = String.format("{\"type\":\"transcript\",\"final\":%s,\"text\":%s}",
                                    isFinal, jsonEscape(text));
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage(payload));
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}
        });

        var ctx = new SessionCtx();
        ctx.stream = stream;
        ctx.reader = reader;
        sessions.put(session.getId(), ctx);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null) return;

        ByteBuffer buf = message.getPayload();
        System.out.println("🎤 Received audio chunk: " + buf.remaining() + " bytes");
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);

        // ✅ v2: setAudio(...)
        ctx.stream.send(StreamingRecognizeRequest.newBuilder()
                .setAudio(com.google.protobuf.ByteString.copyFrom(bytes))
                .build());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionCtx ctx = sessions.remove(session.getId());
        if (ctx != null) {
            try { ctx.stream.closeSend(); } catch (Exception ignored) {}
            ctx.reader.shutdownNow();
        }
    }

    private static String jsonEscape(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
    }
}
