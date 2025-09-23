package com.edo.backend.quiz;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuizService {
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1/chat/completions")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + System.getenv("OPENAI_API_KEY"))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

    public QuizResponse createQuiz(String passage, String level, String style, String sourceFileId) {
        // 메시지 구성
        var messages = List.of(
                Map.of("role","system","content",
                        "너는 시험 출제 전문가다. 아래 ‘본문’만을 근거로 5문항, 각 문항 5지선다 퀴즈를 만들어라. "
                                + "외부 지식 금지. 결과는 지정 스키마에 맞춰 JSON으로만 출력."),
                Map.of("role","user","content",
                        "본문:\n<<<\n" + passage + "\n>>>"
                                + "\n요구사항:\n- 난이도: " + level
                                + "\n- 문제 유형: " + style
                                + "\n- 한국어로 출력")
        );

        var body = Map.of(
                "model", "gpt-4o-mini",
                "messages", messages,
                "response_format", QuizSchema.responseFormat(),
                "temperature", 0.4
        );

        try {
            var node = webClient.post().bodyValue(body)
                    .retrieve()
                    .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                    .retryWhen(
                            reactor.util.retry.Retry.backoff(3, java.time.Duration.ofMillis(400))
                                    .filter(ex -> {
                                        if (ex instanceof WebClientResponseException wcre) {
                                            var sc = wcre.getStatusCode();
                                            return sc.value() == 429 || sc.is5xxServerError();
                                        }
                                        return false; // 다른 예외는 재시도 안 함(원하면 추가)
                                    })
                    )
                    .block();

            var content = node.path("choices").path(0).path("message").path("content").asText("{}");

            // JSON 파싱 → DTO 매핑
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var parsed = mapper.readValue(content, QuizResponse.class);
            return new QuizResponse(true, sourceFileId, parsed.questions(), null);

        } catch (Exception e) {
            return new QuizResponse(false, sourceFileId, List.of(), e.getMessage());
        }
    }


}
