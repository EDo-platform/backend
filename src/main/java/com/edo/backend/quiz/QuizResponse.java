package com.edo.backend.quiz;

import org.hibernate.cache.spi.support.AbstractReadWriteAccess;

import java.util.List;

public record QuizResponse(boolean ok, String sourceFileId, List<Item> questions, String error) {
    public record Item(String id, String question, List<String> choices, int answerIndex, String explanation) {}
}
