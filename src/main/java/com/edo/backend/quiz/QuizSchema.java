package com.edo.backend.quiz;

import java.util.Map;
import java.util.List;

public final class QuizSchema {
    public static Map<String, Object> responseFormat() {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "Quiz5",
                        "strict", true,
                        "schema", Map.of(
                                "type", "object",
                                "required", List.of("questions"),
                                "properties", Map.of(
                                        "questions", Map.of(
                                                "type", "array", "minItems", 5, "maxItems", 5,
                                                "items", Map.of(
                                                        "type", "object",
                                                        "required", List.of("id","question","choices","answerIndex","explanation"),
                                                        "properties", Map.of(
                                                                "id", Map.of("type","string"),
                                                                "question", Map.of("type","string"),
                                                                "choices", Map.of("type","array","minItems",5,"maxItems",5,"items", Map.of("type","string")),
                                                                "answerIndex", Map.of("type","integer","minimum",0,"maximum",4),
                                                                "explanation", Map.of("type","string")
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }
}
