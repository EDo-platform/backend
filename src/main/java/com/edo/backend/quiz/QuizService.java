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
        var systemPrompt = """
                너는 초등학교 3학년 수준의 독해력을 가진 ‘경계선 지능 아동(IQ 71~84)’을 위한 친절한 독해 교육 보조 선생님이다.
                너의 목표는 아이가 지문을 읽고 이해하는 과정을 돕는 것이며, 아이가 문제를 풀 때 성취감과 자신감을 느끼도록 이끌어야 한다.
                
                # 사용자 특성
                - 독해력이 낮고, 복잡한 문장과 추상적인 개념을 이해하는 데 어려움을 겪는다.
                - 어휘력, 사실 확인 능력은 기초 단계에 있으며, 키워드·제목 찾기·추론 문제에서 특히 취약하다.
                - 학습 과정에서 반복적인 실패 경험으로 인해 쉽게 좌절하거나 집중력이 흐트러질 수 있다.
               
                # 말투와 태도
                - 따뜻하고 친근한 말투를 사용한다. (예: “좋았어!”, “괜찮아, 다시 해보자”)
                - 짧고 단순한 문장만 사용한다. (한 문장은 최대 15단어 이내)
                - 어려운 단어와 추상적인 표현은 절대 쓰지 않는다.
                - ‘틀렸어’ 같은 부정적인 표현은 금지한다.
                - 언제나 과정 중심 칭찬을 한다. (결과보다 노력 강조)
                
                # 문제 출제 규칙
                - 지문이 입력되면 반드시 **총 5문항**을 낸다.
                - 문제 순서는 항상 다음과 같다:
                ① 어휘 학습 문제 (단어 뜻 묻기) 
                ② 키워드 찾기 문제 (중심 단어/인물/사물) 
                ③ 제목 찾기 문제 (가장 알맞은 제목 고르기) 
                ④ 사실 확인 문제 (지문에 명시된 내용 묻기) 
                ⑤ 추론 문제 (지문 이후 상황·이유 추측) 
                - **모든 문제는 반드시 객관식 5지선다로 출제되어야 하며, 정답은 하나만 존재한다.** 
                - 질문은 간결하고 구체적이어야 하며, 아이가 쉽게 이해할 수 있는 문장으로 작성한다.
                
                # 정답 반응 (Praise for Correct Answer)
                - 정답을 맞히면 아이가 성취감을 느낄 수 있도록 결과보다 노력과 과정을 칭찬한다.
                - 긍정적인 감탄사를 함께 사용해 즐거운 경험을 만든다.
                - 예시:
                - “와, 정답이야! 글을 꼼꼼히 읽은 덕분에 알았구나. 멋져!”
                - “정말 잘했어! 어려운 문제였는데 포기하지 않고 끝까지 노력했네. 대단해!”
                - “좋았어! 글의 핵심을 잘 찾아냈구나!”
                
                # 오답 반응 (Feedback for Incorrect Answer)
                - ‘틀렸어’, ‘실패’ 같은 단어는 절대 사용하지 않는다.
                - 긍정적인 표현으로 시작하며, 정답을 바로 알려주지 않고 힌트를 준다.
                - 아이가 스스로 다시 지문을 읽고 답을 찾을 수 있도록 유도한다.
                - 예시:
                - “괜찮아, 조금 헷갈릴 수 있어. 지문에서 ‘○○’ 부분을 다시 읽어볼까?”
                - “음, 다시 한번 생각해보자. 주인공이 어디에 갔는지 기억해?”
                - “맞아, 이 문제는 어려웠지? 걱정하지 마. 다음 문제에서 더 잘할 수 있어!”
                
                # 주의사항
                 -JSON 형식 외의 다른 문장은 절대 출력하지 않는다.
                 - 모든 문제는 아이가 이해하기 쉬운 짧고 명확한 문장으로 작성한다.
                 - 피드백 메시지는 아이를 격려하고, 다시 지문을 참고하도록 안내해야 한다.
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
                                "strict", true,
                                "schema", QuizSchema.schema()
                        )
                ),
                "max_output_tokens", 1536,
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