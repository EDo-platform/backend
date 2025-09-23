package com.edo.backend.stt;

import com.edo.backend.ocr.debug.GcpProps;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v2.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class StreamingSttService {

    private final GcpProps gcp;

    public SpeechClient newClient() throws Exception{
        return SpeechClient.create();
    }

    public StreamingRecognizeRequest configReq(String languageCode, String model) {
        String recognizer = "projects/%s/locations/global/recognizers/_"
                .formatted(gcp.projectId());

        var cfg = RecognitionConfig.newBuilder()
                .setAutoDecodingConfig(AutoDetectDecodingConfig.getDefaultInstance())
                .addLanguageCodes(languageCode)    // "ko-KR"
                .setModel(model)                   // "latest_short" or "latest_long"
                .setFeatures(RecognitionFeatures.newBuilder()
                        .setEnableAutomaticPunctuation(true)
                        .build())
                .build();

        var streamCfg = StreamingRecognitionConfig.newBuilder()
                .setConfig(cfg)
                .build();

        return StreamingRecognizeRequest.newBuilder()
                .setRecognizer(recognizer)
                .setStreamingConfig(streamCfg)
                .build();
    }

    public StreamingRecognizeRequest audioReq(byte[] bytes) {
        return StreamingRecognizeRequest.newBuilder()
                .setAudio(ByteString.copyFrom(bytes))
                .build();
    }

    /** 요청 큐(설정 → 오디오청크들)를 gRPC에 흘려보내고, 응답을 콜백으로 전달 */
    public void stream(SpeechClient client,
                       LinkedBlockingQueue<StreamingRecognizeRequest> reqQ,
                       Consumer<StreamingRecognizeResponse> onResp,
                       Consumer<Throwable> onError) {

        var call = client.streamingRecognizeCallable().splitCall(new ResponseObserver<>() {
            @Override public void onStart(StreamController controller) {}
            @Override public void onResponse(StreamingRecognizeResponse r) { onResp.accept(r); }
            @Override public void onError(Throwable t) { if (onError != null) onError.accept(t); }
            @Override public void onComplete() {}
        });

        // 요청 펌프 스레드
        new Thread(() -> {
            try {
                while (true) {
                    var req = reqQ.take();
                    call.send(req);
                }
            } catch (InterruptedException ignored) {
            } finally {
                call.closeSend();
            }
        }, "stt-req-pump").start();
    }
}
