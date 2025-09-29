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
        volatile boolean inputClosed = false;
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
                    // 디버깅용 최소 로그 (여기서 과한 System.out은 유지해도 됨)
                    // System.out.println("📩 Got STT response: " + resp.getResultsCount());

                    if (resp.getResultsCount() == 0) continue;
                    var r = resp.getResults(0);
                    if (r.getAlternativesCount() == 0) continue;

                    boolean isFinal = r.getIsFinal();
                    String text = r.getAlternatives(0).getTranscript();
                    var payload = String.format(
                            "{\"type\":\"transcript\",\"final\":%s,\"text\":%s}",
                            isFinal, jsonEscape(text)
                    );
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(payload));
                    }
                }
            } catch (Exception e) {
                // ✅ 에러를 브라우저로 먼저 전송 (연결을 즉시 닫지 않음)
                String err = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
                System.err.println("❌ STT stream error: " + err);
                e.printStackTrace();
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(
                                "{\"type\":\"error\",\"message\":" + jsonEscape(err) + "}"
                        ));
                    }
                } catch (Exception ignored) {}
                // 여기서 session.close() 하지 말기! (브라우저가 에러를 볼 시간 필요)
            }
            // finally 없음: 자동 종료 금지. 필요하면 stop 핸드셰이크에서 닫기.
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

        // ✅ stop 이후 들어온 청크는 바로 버림
        if (ctx.inputClosed) {
            // 선택: 디버깅 로그
            // System.out.println("⏭️ ignore chunk after stop: " + message.getPayloadLength() + " bytes");
            return;
        }

        ByteBuffer buf = message.getPayload();
        int size = buf.remaining();
        System.out.println("🎤 Received audio chunk: " + size + " bytes");

        byte[] bytes = new byte[size];
        buf.get(bytes);

        try {
            // v2: setAudio(...)
            ctx.stream.send(StreamingRecognizeRequest.newBuilder()
                    .setAudio(com.google.protobuf.ByteString.copyFrom(bytes))
                    .build());
        } catch (IllegalStateException ise) {
            // ✅ closeSend() 이후 늦게 도착한 청크가 send되면 여기로 옴 → 조용히 무시
            System.out.println("⏭️ ignore send after half-close: " + ise.getMessage());
        } catch (Exception e) {
            System.err.println("❌ send audio failed: " + e.getMessage());
            // 필요시 클라이언트에 에러 전달
            try {
                if (session.isOpen()) {
                    String msg = "{\"type\":\"error\",\"message\":" + jsonEscape("send audio failed: " + e.getMessage()) + "}";
                    session.sendMessage(new TextMessage(msg));
                }
            } catch (Exception ignored) {}
        }
    }

    // --- 텍스트(제어) 처리 ---
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null) return;

        String payload = message.getPayload();
        if ("{\"type\":\"stop\"}".equals(payload)) {
            System.out.println("🛑 stop received -> closeSend()");
            // ✅ 더 이상 오디오 받지 않도록 플래그 세팅
            ctx.inputClosed = true;
            try {
                // ✅ GCP 스트림 입력만 닫고(final 수신은 계속)
                ctx.stream.closeSend();
            } catch (Exception e) {
                System.err.println("closeSend() failed: " + e.getMessage());
            }
            // 여기서는 session.close() 하지 않음 (reader가 final까지 받고 나서 닫게)
        }
    }

    // --- 연결 종료(soft close) ---
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionCtx ctx = sessions.remove(session.getId());
        if (ctx != null) {
            try { ctx.stream.closeSend(); } catch (Exception ignored) {}
            if (ctx.reader != null) {
                // ✅ 강제 인터럽트 대신 부드럽게 종료
                ctx.reader.shutdown();
                // 필요시 일정 시간 기다렸다가 강제 종료:
                // try { if (!ctx.reader.awaitTermination(3, TimeUnit.SECONDS)) ctx.reader.shutdownNow(); } catch (InterruptedException ignored) {}
            }
        }
        System.out.println("🔌 STT WebSocket closed: " + session.getId() + " (" + status + ")");
    }


    private static String jsonEscape(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
    }
}
