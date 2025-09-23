package com.edo.backend.quiz;

import java.util.Map;
import java.util.List;

public final class QuizSchema {
    private QuizSchema() {}

    // 엄격 스키마 (권장)
    public static Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("questions"),
                "properties", Map.of(
                        "questions", Map.of(
                                "type", "array",
                                "minItems", 5,
                                "maxItems", 5,
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("id","question","choices","answerIndex","explanation"),
                                        "properties", Map.of(
                                                "id",          Map.of("type","string", "minLength", 1),
                                                "question",    Map.of("type","string", "minLength", 1),
                                                "choices",     Map.of(
                                                        "type","array",
                                                        "minItems", 5,
                                                        "maxItems", 5,
                                                        "items", Map.of("type","string", "minLength", 1)
                                                ),
                                                "answerIndex", Map.of("type","integer","minimum",0,"maximum",4),
                                                "explanation", Map.of("type","string")
                                        )
                                )
                        )
                )
        );
    }

    // 최소 스키마(문제 시 롤백용)
    public static Map<String, Object> minimalSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("questions"),
                "properties", Map.of(
                        "questions", Map.of(
                                "type","array",
                                "items", Map.of(
                                        "type","object",
                                        "additionalProperties", false,
                                        "properties", Map.of() // 최소로 둠
                                )
                        )
                )
        );
    }
}