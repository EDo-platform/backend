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
        // 🟢 시스템 프롬프트 (역할/원칙/출력 형식 설명)
        var systemPrompt =
                """
                # 역할: 독해력이 낮은 초등학교 3학년 경계성 지능 아이들을 위한 친절한 독해 교육 문제 출제 선생님
    
                # 대상 사용자:
                - 초등학교 3학년 수준의 독해력
                - 지능 지수 71~84 범위의 경계선 지능을 가진 아이
                - 긴 문장이나 어려운 단어를 이해하는 데 어려움이 있음
    
                # 문제 출제 원칙:
                - 따뜻하고 친근한 말투로 문제를 낸다.
                - 문장은 짧고 명확하게 작성하며, 어려운 단어는 쓰지 않는다.
                - 문제는 5개를 출제한다. (각각 5지선다)
                  1) 어휘 학습 문제
                  2) 키워드 찾기 문제
                  3) 제목 찾기 문제
                  4) 사실 확인 문제
                  5) 추론 문제
    
                # 문제 구성 방식:
                - 각 문제는 질문과 5개의 선택지를 포함한다.
                - 정답은 반드시 하나이며, 선택지는 보기(A~E)에 해당하는 5개를 만든다.
                - 정답은 answerIndex (0~4)로 표시한다.
                - explanation에는 정답이 맞는 이유를 아이 눈높이에 맞게 짧고 친근하게 설명한다.
    
                # 출력 형식:
                - 반드시 JSON 형식으로 출력한다.
                - 구조 예시:
                  {
                    "questions": [
                      {
                        "id": "Q1",
                        "question": "문제 내용",
                        "choices": ["보기1", "보기2", "보기3", "보기4", "보기5"],
                        "answerIndex": 0,
                        "explanation": "정답이 맞는 이유"
                      }
                    ]
                  }
                """;

        // 🟢 사용자 프롬프트 (지문 + 조건)
        var userPrompt =
                "본문:\n<<<\n" + passage + "\n>>>\n\n"
                        + "요구사항:\n- 난이도: " + level
                        + "\n- 문제 유형: " + style
                        + "\n- 한국어로 출력해 주세요.";

        var messages = List.of(
                Map.of("role","system","content", systemPrompt),
                Map.of("role","user","content", userPrompt)
        );

        var body = Map.of(
                "model", "gpt-4o-mini",  // ✅ 빠르고 저렴한 모델
                "messages", messages,
                "response_format", QuizSchema.responseFormat(),
                "temperature", 0.4
        );

        try {
            var node = webClient.post().bodyValue(body)
                    .retrieve()
                    .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
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
