package com.edo.backend.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.StreamingRecognizeRequest;
import com.google.cloud.speech.v2.StreamingRecognizeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;


@Component
@RequiredArgsConstructor
public class SttWebSocketHandler extends BinaryWebSocketHandler {

    private final StreamingSttService stt;
    private final ObjectMapper om = new ObjectMapper();

    private LinkedBlockingQueue<StreamingRecognizeRequest> queue;
    private SpeechClient client;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        queue = new LinkedBlockingQueue<>();
        client = stt.newClient();

        stt.stream(
                client,
                queue,
                resp -> sendText(session, toMessage(resp)),
                err  -> sendText(session, errorMessage(err))
        );
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 첫 메시지(설정): {"type":"config","languageCode":"ko-KR","model":"latest_short"}
        JsonNode node = om.readTree(message.getPayload());
        if ("config".equals(node.path("type").asText())) {
            var req = stt.configReq(
                    node.path("languageCode").asText("ko-KR"),
                    node.path("model").asText("latest_short")
            );
            queue.offer(req);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // 브라우저 MediaRecorder(audio/webm;codecs=opus) 청크 → 그대로 릴레이
        ByteBuffer buf = message.getPayload();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        queue.offer(stt.audioReq(bytes));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        if (client != null) client.close();
    }

    private TextMessage toMessage(StreamingRecognizeResponse resp) {
        // partial / final 구분
        StringBuilder sb = new StringBuilder();
        resp.getResultsList().forEach(r -> {
            if (r.getAlternativesCount() > 0) {
                sb.append(r.getAlternatives(0).getTranscript()).append(" ");
            }
        });
        String kind = (resp.getSpeechEventType() == SpeechEventType.SPEECH_EVENT_TYPE_END_OF_SINGLE_UTTERANCE)
                ? "final" : "partial";
        String json = """
                {"type":"%s","text":%s}
                """.formatted(kind, quote(sb.toString().trim()));
        return new TextMessage(json);
    }

    private TextMessage errorMessage(Throwable t) {
        String json = """
                {"type":"error","text":%s}
                """.formatted(quote(t.getMessage() == null ? "stream error" : t.getMessage()));
        return new TextMessage(json);
    }

    private void sendText(WebSocketSession session, TextMessage msg) {
        try { if (session.isOpen()) session.sendMessage(msg); } catch (Exception ignored) {}
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }
}
