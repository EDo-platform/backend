package com.edo.backend.quiz;

import com.edo.backend.ocr.recognition.OcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QuizController {
    private final OcrService ocrService;
    private final QuizService quizService;

    public record QuizReq(String fileId, String level, String style) {}

    @PostMapping(value="/quiz", consumes= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QuizResponse> quiz(@RequestBody QuizReq req) throws Exception {
        // 1) OCR 실행
        String passage = ocrService.extractText(req.fileId(), "ko");

        // 2) GPT 퀴즈 생성
        var res = quizService.createQuiz(
                passage,
                Optional.ofNullable(req.level()).orElse("초급"),
                Optional.ofNullable(req.style()).orElse("지문 이해"),
                req.fileId()
        );

        // 3) 스키마 검증
        if (res.ok() && res.questions() != null && res.questions().size() == 5) {
            boolean valid = res.questions().stream().allMatch(q ->
                    q.choices() != null && q.choices().size() == 5
                            && q.answerIndex() >= 0 && q.answerIndex() < 5
            );
            if (!valid) {
                return ResponseEntity.ok(new QuizResponse(false, req.fileId(), List.of(), "Invalid quiz format"));
            }
        }

        return ResponseEntity.ok(res);
    }
}
