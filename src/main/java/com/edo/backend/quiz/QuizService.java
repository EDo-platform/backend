package com.edo.backend.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class QuizService {
    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private final WebClient webClient;
    private final ObjectMapper mapper;

    public QuizService(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            WebClient.Builder builder,
            ObjectMapper mapper
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is missing");
        }
        this.webClient = builder
                .baseUrl("https://api.openai.com/v1/responses")
                .defaultHeaders(h -> h.setBearerAuth(apiKey))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.mapper = mapper;
    }

    public QuizResponse createQuiz(String passage, String level, String style, String sourceFileId) {
        // 시스템 프롬프트(간결 버전)
        var systemPrompt = """
            너는 초등학교 3학년 경계성 지능(71~84) 아이들을 위한 친절한 독해 문제 출제 선생님이다.
            말투는 따뜻하고 짧게, 어려운 단어는 쓰지 않는다.
            총 5문항, 각 5지선다: ①어휘 ②키워드 ③제목 ④사실확인 ⑤추론.
            출력은 반드시 JSON(스키마 준수)으로만 한다.
            """;

        // 사용자 프롬프트
        var userPrompt = "본문:\n<<<\n" + passage + "\n>>>\n\n"
                + "요구사항:\n- 난이도: " + level
                + "\n- 문제 유형: " + style
                + "\n- 한국어로 출력";

        // Responses API는 messages가 아니라 input
        var input = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
        );

        // ✅ json_schema는 text.format 객체 안에 둔다 (name 필수)
        var body = Map.of(
                "model", "gpt-4o-mini",
                "input", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userPrompt)
                ),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "Quiz5",
                                // "strict", true, // 필요하면 켜세요. schema 내 additionalProperties:false는 이미 설정돼 있음
                                "schema", QuizSchema.schema()
                        )
                ),
                "max_output_tokens", 1024,
                "temperature", 0.4
        );

        try {
            if (log.isDebugEnabled()) {
                log.debug("OpenAI request body = {}", mapper.writeValueAsString(body));
            }

            JsonNode root = webClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class).flatMap(b ->
                                    Mono.error(new RuntimeException("OpenAI error " + resp.statusCode() + ": " + b))
                            )
                    )
                    .bodyToMono(JsonNode.class)
                    .block();

            String contentJson = extractTextFromResponses(root);
            if (contentJson == null || contentJson.isBlank()) {
                return new QuizResponse(false, sourceFileId, List.of(), "Empty content from Responses API");
            }

            QuizResponse parsed = mapper.readValue(contentJson, QuizResponse.class);

            // 서버측 형식 검증(5문항/5지선다/정답 0~4)
            boolean valid = parsed.questions() != null
                    && parsed.questions().size() == 5
                    && parsed.questions().stream().allMatch(q ->
                    q.choices() != null && q.choices().size() == 5
                            && q.answerIndex() >= 0 && q.answerIndex() < 5);

            if (!valid) {
                return new QuizResponse(false, sourceFileId, List.of(), "Invalid quiz format");
            }

            return new QuizResponse(true, sourceFileId, parsed.questions(), null);

        } catch (Exception e) {
            return new QuizResponse(false, sourceFileId, List.of(), e.getMessage());
        }
    }

    // Responses API 표준 파싱(환경에 따라 output_text로만 내려올 수도 있음)
    private String extractTextFromResponses(JsonNode root) {
        if (root == null) return null;

        // 1) 권장 경로
        JsonNode t1 = root.path("output").path(0).path("content").path(0).path("text");
        if (t1.isTextual()) return t1.asText();

        // 2) 대체 경로
        JsonNode t2 = root.path("output_text");
        if (t2.isTextual()) return t2.asText();

        // 3) (아주 예외적) chat 호환 응답
        JsonNode t3 = root.path("choices").path(0).path("message").path("content");
        if (t3.isTextual()) return t3.asText();

        return null;
    }
}