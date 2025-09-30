package com.edo.backend.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
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
        // (1) 시스템 지침: 톤/규칙 고정
        var systemPrompt = """
너는 초등학교 3학년 수준의 독해력을 가진 ‘경계선 지능 아동(IQ 71~84)’을 위한 친절한 독해 교육 보조 선생님이다.
목표는 지문 이해를 돕고, 문제 풀이 과정에서 ‘생각하며’ 답을 고르게 하는 것이다.

[말투/표현]
- 따뜻하고 친근한 말투. 짧고 단순한 문장(한 문장은 15단어 이내).
- 어려운 단어·추상 표현 금지. 부정적 표현(‘틀렸어’ 등) 금지.
- 한국어로 쓰되, 초등 3학년 눈높이에 맞춘 단어만 사용.

[출제 원칙(형식은 기존 스키마를 그대로 유지)]
- 총 5문항, 5지선다, 정답은 하나. (형식은 기존 JSON 스키마를 따른다)
- 문제 순서 의도: ① 어휘 ② 중심내용 ③ 제목 ④ 사실확인 ⑤ 추론
- 보기(오답) 설계는 ‘그럴듯하지만 틀린’ 이유가 분명해야 한다. 단순 농담/말장난/모호 금지.
- 정답 문장은 지문을 ‘그대로 복붙’하지 말고, 가능하면 짧게 ‘의미를 바꿔 말하기(패러프레이즈)’ 하되, 사실확인 문제는 원문 사실을 정확히 반영.
- “모두 정답/모두 오답/위의 어느 것도 아님” 등의 메타 선택지 금지.
- 보기 길이는 과도하게 길지 않게, 서로 길이가 크게 벌어지지 않게.

[문항별 설계 가이드]
1) 어휘(의미/뉘앙스)
   - 정답: 지문 속 표현의 ‘문맥상 의미’를 짧게 바꿔 말한 것.
   - 오답 4개 유형을 섞어라: (a) 반의 의미, (b) 비슷하지만 핵심 뉘앙스 다른 의미,
                             (c) 지문 주제 밖의 의미, (d) 문장 일부만 오해한 의미.
2) 중심내용(키 아이디어)
   - 정답: 글이 ‘주로 말하는 것’을 한 문장으로 압축.
   - 오답: 세부 사례만 확대하거나, 주제의 범위를 너무 좁히거나 과장한 것.
3) 제목
   - 정답: 주제(무엇에 관한 글) + 핵심 주장/이유가 느껴지는 간단한 말.
   - 오답: (a) 세부 한 부분만 제목화, (b) 글의 범위 벗어남, (c) 낚시성/감탄 위주.
4) 사실확인(텍스트 증거 기반)
   - 정답: 본문에 ‘분명히 적힌 사실’.
   - 오답: 숫자/주체/원인-결과를 한 부분 바꿔치기, 시점·조건을 살짝 틀리게.
5) 추론(직접 쓰지 않은 결론)
   - 정답: 본문 단서들로 충분히 ‘이유를 생각해 도달’ 가능한 결론.
   - 오답: 단서가 부족하거나, 글의 의도와 어긋나거나, 과도한 일반화.

[피드백(정답/오답 반응)]
- 정답: 과정 중심 칭찬 한 문장 (“잘했어! ~ 덕분이야”)
- 오답: 긍정으로 시작 + 본문 특정 힌트(문장/단어/부분)로 다시 찾게 유도. 정답을 바로 말하지는 말 것.

[품질 체크리스트(내부적으로 따르되 출력하지 말 것)]
- (의미 정확성) 정답은 텍스트 근거와 모순 없음.
- (오답 품질) 네 오답은 서로 다른 잘못된 추론/오해 유형.
- (난이도) 어휘는 초3 수준, 문장 짧게. 정답을 ‘문장 길이’나 ‘어휘 난이도’로 눈치채지 못하게.
- (중복 회피) 보기들 사이 문구 반복 최소화, 단서 단어를 한 보기에만 몰아넣지 말 것.

※ 출력은 반드시 기존 JSON 스키마에 ‘딱 맞게’만 내고, 다른 텍스트는 절대 출력하지 않는다.
""";

        // (2) 사용자 입력: 본문과 난이도/톤 힌트만 전달
        var userPrompt = """
본문:
<<<
%s
>>>

요구사항:
- 난이도: %s
- 말투/피드백 스타일(톤): %s
- 위 시스템 가이드에 따라, 아이가 ‘생각하며’ 고르도록 보기(오답)를 정교하게 설계하라.
- 정답은 본문 근거와 일치해야 하며, 오답은 그럴듯하지만 서로 다른 오해 유형을 반영해야 한다.
- 정답 문장은 가능하면 본문을 그대로 베끼지 말고 짧게 바꿔 말해라(사실확인 제외).
- 출력은 기존 JSON 스키마 형식을 정확히 따를 것. (JSON 외 텍스트 금지)
""".formatted(passage, level, style == null ? "" : style);

        // (3) Responses API 요청 바디
        var body = Map.of(
                "model", "gpt-4.1-mini", // 필요시 "gpt-4o"로 상향
                "instructions", systemPrompt,
                "input", userPrompt,
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "Quiz5",
                                "strict", true,
                                "schema", QuizSchema.schema() // 아래 클래스
                        )
                ),
                "max_output_tokens", 1536,
                "temperature", 0.3
                // ⚠ seed는 Responses API에서 미지원 → 제거
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

            // 서버측 형식 2차 검증
            boolean valid = parsed.questions() != null
                    && parsed.questions().size() == 5
                    && parsed.questions().stream().allMatch(q ->
                    q.choices() != null && q.choices().size() == 5
                            && q.answerIndex() >= 0 && q.answerIndex() < 5
                            && q.question() != null && !q.question().isBlank()
                            && q.explanation() != null && !q.explanation().isBlank()
            );

            if (!valid) {
                return new QuizResponse(false, sourceFileId, List.of(), "Invalid quiz format");
            }

            return new QuizResponse(true, sourceFileId, parsed.questions(), null);

        } catch (Exception e) {
            return new QuizResponse(false, sourceFileId, List.of(), e.getMessage());
        }
    }

    /**
     * Responses API 표준 파싱:
     * 1) output[*].content[*] 중 type == "output_text" 의 text
     * 2) 대체: root.output_text
     * 3) 예외적: chat 호환 choices[0].message.content
     */
    private String extractTextFromResponses(JsonNode root) {
        if (root == null) return null;

        // 1) 권장 경로
        JsonNode outputArr = root.path("output");
        if (outputArr.isArray()) {
            for (JsonNode item : outputArr) {
                JsonNode contentArr = item.path("content");
                if (contentArr.isArray()) {
                    for (JsonNode c : contentArr) {
                        String type = c.path("type").asText(null);
                        JsonNode txt = c.path("text");
                        if ("output_text".equals(type) && txt != null && txt.isTextual()) {
                            return txt.asText();
                        }
                        if (txt != null && txt.isTextual()) { // type 누락 케이스
                            return txt.asText();
                        }
                    }
                }
            }
        }

        // 2) 대체 경로
        JsonNode t2 = root.path("output_text");
        if (t2.isTextual()) return t2.asText();

        // 3) 예외적 chat 호환
        JsonNode t3 = root.path("choices").path(0).path("message").path("content");
        if (t3.isTextual()) return t3.asText();

        return null;
    }
}