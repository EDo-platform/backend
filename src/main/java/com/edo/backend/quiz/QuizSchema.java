package com.edo.backend.quiz;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QuizSchema {

    private QuizSchema() {}

    // 최상위 스키마
    public static Map<String, Object> schema() {
        Map<String, Object> root = new HashMap<>();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.put("required", List.of("questions"));

        Map<String, Object> props = new HashMap<>();
        props.put("questions", questionsArraySchema());
        root.put("properties", props);

        return root;
    }

    private static Map<String, Object> questionsArraySchema() {
        Map<String, Object> arr = new HashMap<>();
        arr.put("type", "array");
        arr.put("minItems", 5);
        arr.put("maxItems", 5);
        arr.put("items", questionObjectSchema());
        return arr;
    }

    private static Map<String, Object> questionObjectSchema() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("type", "object");
        obj.put("additionalProperties", false);
        obj.put("required", List.of("id", "question", "choices", "answerIndex", "answerText", "explanation", "hints"));

        Map<String, Object> props = new HashMap<>(); // 모든 필드 집합 정의

        // id 필드
        Map<String, Object> idProp = new HashMap<>();
        idProp.put("type", "string");
        idProp.put("minLength", 1);
        props.put("id", idProp);

        // question 필드
        Map<String, Object> questionProp = new HashMap<>();
        questionProp.put("type", "string");
        questionProp.put("minLength", 1);
        props.put("question", questionProp);

        // choices 필드
        Map<String, Object> choicesProp = new HashMap<>();
        choicesProp.put("type", "array");
        choicesProp.put("minItems", 5);
        choicesProp.put("maxItems", 5);
        Map<String, Object> choiceItem = new HashMap<>();
        choiceItem.put("type", "string");
        choiceItem.put("minLength", 1);
        choicesProp.put("items", choiceItem);
        props.put("choices", choicesProp);

        // answerIndex 필드
        Map<String, Object> answerIndexProp = new HashMap<>();
        answerIndexProp.put("type", "integer");
        answerIndexProp.put("minimum", 0);
        answerIndexProp.put("maximum", 4);
        props.put("answerIndex", answerIndexProp);

        // answerText 필드
        Map<String, Object> answerTextProp = new HashMap<>();
        answerTextProp.put("type", "string");
        answerTextProp.put("minLength", 1);
        props.put("answerText", answerTextProp);

        // explanation 필드
        Map<String, Object> explanationProp = new HashMap<>();
        explanationProp.put("type", "string");
        explanationProp.put("minLength", 1);
        props.put("explanation", explanationProp);

        // hints 필드
        Map<String, Object> hintsProp = new HashMap<>();
        hintsProp.put("type", "object");
        hintsProp.put("additionalProperties", false);

        Map<String, Object> hintsProps = new HashMap<>();

        // byChoice 필드
        Map<String, Object> byChoiceProp = new HashMap<>();
        byChoiceProp.put("type", "array");
        byChoiceProp.put("minItems", 5);
        byChoiceProp.put("maxItems", 5);
        Map<String, Object> byChoiceItem = new HashMap<>();
        byChoiceItem.put("type", "string");
        byChoiceItem.put("minLength", 1);
        byChoiceProp.put("items", byChoiceItem);

        hintsProps.put("byChoice", byChoiceProp);
        hintsProp.put("properties", hintsProps);
        hintsProp.put("required", List.of("byChoice"));
        props.put("hints", hintsProp);

        obj.put("properties", props);
        return obj;
    }
}