package com.edo.backend.example;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    @GetMapping
    public ResponseEntity<List<SampleMeta>> list() {
        return ResponseEntity.ok(sampleService.list());
    }

    @PostMapping("/{id}/use")
    public ResponseEntity<Map<String, Object>> use(@PathVariable String id) throws Exception {
        String fileId = sampleService.materialize(id);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "fileId", fileId
        ));
    }
}
