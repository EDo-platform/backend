package com.edo.backend.stt;

import com.google.api.gax.rpc.BidiStream;
import com.google.cloud.speech.v2.*;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

@Component
public class SttSocketHandler extends BinaryWebSocketHandler {

    private final SpeechClient speechClient;
    private final String recognizerName;

    public SttSocketHandler(@Value("${gcp.project-id}") String projectId) throws Exception {
        this.speechClient = SpeechClient.create();
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("❌ GCP project-id is not set. Check env GCP_PROJECT_ID");
        }
        // '_' = 암시적 Recognizer (v2에서 허용)
        this.recognizerName = String.format("projects/%s/locations/global/recognizers/_", projectId);
        System.out.println("✅ recognizerName = " + recognizerName);
    }

    private static class SessionCtx {
        BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse> stream;
        ExecutorService reader;
        FFmpegTranscoder ff;
        Future<?> pumpTask;
        volatile boolean inputClosed = false;
    }

    private final ConcurrentHashMap<String, SessionCtx> sessions = new ConcurrentHashMap<>();
    private final ExecutorService ioPool = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 1) gRPC 스트림 생성
        BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse> stream =
                speechClient.streamingRecognizeCallable().call();

        // 2) 인식 설정(v2 방식: 명시적 디코딩 설정)
        ExplicitDecodingConfig dec = ExplicitDecodingConfig.newBuilder()
                .setEncoding(ExplicitDecodingConfig.AudioEncoding.LINEAR16) // <-- 여기!
                .setSampleRateHertz(16000)
                .setAudioChannelCount(1)
                .build();

        RecognitionConfig recConfig = RecognitionConfig.newBuilder()
                .addLanguageCodes("ko-KR")
                .setModel("latest_long")
                .setFeatures(RecognitionFeatures.newBuilder()
                        .setEnableAutomaticPunctuation(true)
                        .build())
                .setExplicitDecodingConfig(dec) // <-- RecognitionConfig에 붙이기
                .build();

        StreamingRecognitionFeatures sFeatures = StreamingRecognitionFeatures.newBuilder()
                .setInterimResults(true)
                .setEnableVoiceActivityEvents(false)
                .build();

        StreamingRecognitionConfig streamingCfg = StreamingRecognitionConfig.newBuilder()
                .setConfig(recConfig)
                .setStreamingFeatures(sFeatures)
                .build();

        // 첫 요청: 설정만
        stream.send(StreamingRecognizeRequest.newBuilder()
                .setRecognizer(recognizerName)
                .setStreamingConfig(streamingCfg)
                .build());

        // 3) FFmpeg 시작 (임의 포맷 → s16le/16k/mono)
        FFmpegTranscoder ff = new FFmpegTranscoder();
        InputStream pcm = ff.stdout();

        // 4) FFmpeg stdout(PCM)을 15KB 미만으로 잘라 gRPC로 전송
        Future<?> pumpTask = ioPool.submit(() -> {
            final int SLICE = 14_000; // 자바 레퍼런스 상한(15KB)보다 적게
            byte[] buf = new byte[4096];
            byte[] spill = new byte[0];
            int n;
            try {
                while ((n = pcm.read(buf)) > 0) {
                    // spill + buf[0..n) 를 SLICE 크기로 분할
                    byte[] chunk = concat(spill, buf, n);
                    int off = 0;
                    while (off < chunk.length) {
                        int len = Math.min(SLICE, chunk.length - off);
                        stream.send(StreamingRecognizeRequest.newBuilder()
                                .setAudio(ByteString.copyFrom(chunk, off, len))
                                .build());
                        off += len;
                    }
                    // 남는 건 없도록(정확히 분할했으니 남지 않음)
                    spill = new byte[0];
                }
            } catch (Exception e) {
                System.err.println("PCM pump error: " + e.getMessage());
            }
        });

        // 5) 응답 읽기(부분/최종)
        ExecutorService reader = Executors.newSingleThreadExecutor();
        reader.submit(() -> {
            try {
                for (StreamingRecognizeResponse resp : stream) {
                    if (resp.getResultsCount() == 0) continue;
                    var r = resp.getResults(0);
                    if (r.getAlternativesCount() == 0) continue;

                    boolean isFinal = r.getIsFinal();
                    String text = r.getAlternatives(0).getTranscript();
                    var payload = String.format(
                            "{\"type\":\"transcript\",\"final\":%s,\"text\":%s}",
                            isFinal, jsonEscape(text)
                    );
                    if (session.isOpen()) session.sendMessage(new TextMessage(payload));
                }
            } catch (Exception e) {
                String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                System.err.println("❌ STT stream error: " + err);
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(
                                "{\"type\":\"error\",\"message\":" + jsonEscape(err) + "}"
                        ));
                    }
                } catch (Exception ignored) {}
            }
        });

        var ctx = new SessionCtx();
        ctx.stream = stream;
        ctx.reader = reader;
        ctx.ff = ff;
        ctx.pumpTask = pumpTask;
        sessions.put(session.getId(), ctx);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null || ctx.inputClosed) return;

        ByteBuffer buf = message.getPayload();
        int size = buf.remaining();
        System.out.println("🎤 Received media chunk: " + size + " bytes");
        byte[] bytes = new byte[size];
        buf.get(bytes); // direct/slice 대응

        try {
            // 임의 컨테이너/코덱 → ffmpeg stdin으로 투입
            ctx.ff.stdin().write(bytes);
            ctx.ff.stdin().flush();
        } catch (Exception e) {
            System.err.println("❌ write to ffmpeg failed: " + e.getMessage());
            try {
                if (session.isOpen()) {
                    String msg = "{\"type\":\"error\",\"message\":" + jsonEscape("transcode write failed: " + e.getMessage()) + "}";
                    session.sendMessage(new TextMessage(msg));
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SessionCtx ctx = sessions.get(session.getId());
        if (ctx == null) return;

        if ("{\"type\":\"stop\"}".equals(message.getPayload())) {
            System.out.println("🛑 stop received -> close ffmpeg stdin & GCP half-close");
            ctx.inputClosed = true;
            try { ctx.ff.stdin().close(); } catch (Exception ignored) {}
            try { ctx.stream.closeSend(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionCtx ctx = sessions.remove(session.getId());
        if (ctx != null) {
            ctx.inputClosed = true;
            try { ctx.ff.stdin().close(); } catch (Exception ignored) {}
            try { if (ctx.pumpTask != null) ctx.pumpTask.get(1500, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
            try { ctx.stream.closeSend(); } catch (Exception ignored) {}
            try { ctx.ff.close(); } catch (Exception ignored) {}
            if (ctx.reader != null) ctx.reader.shutdown();
        }
        System.out.println("🔌 STT WebSocket closed: " + session.getId() + " (" + status + ")");
    }

    private static String jsonEscape(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
    }

    private static byte[] concat(byte[] a, byte[] b, int nB) {
        if (a.length == 0) {
            byte[] out = new byte[nB];
            System.arraycopy(b, 0, out, 0, nB);
            return out;
        }
        byte[] out = new byte[a.length + nB];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, nB);
        return out;
    }
}
