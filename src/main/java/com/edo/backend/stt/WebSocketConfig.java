package com.edo.backend.stt;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final SttWebSocketHandler sttWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry reg) {
        reg.addHandler(sttWebSocketHandler, "/ws/stt").setAllowedOrigins("*");
    }

}
