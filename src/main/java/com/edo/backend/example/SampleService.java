package com.edo.backend.example;

import com.edo.backend.fileuplaod.FileMetadata;
import com.edo.backend.fileuplaod.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SampleService {

    private final FileStorageService storage;

    private static final String SAMPLE_TEXT_SPACESUIT = """
            우주의 환경과 지구의 환경은 매우 다르기 때문에 우주 비 행사들에게는 특별한 옷이 필요합니다. 
            우주에는 숨을 쉴 수 있는 공기가 거의 없고, 높은 온도와 낮은 온도가 반복됩니다. 
            또한 우주에는 각종 우주 먼지와 방사선이 있습니다. 
            우주복은 우주의 이러한 특성을 고려하여 비행사의 몸을 보호 하면서 비행사가 안전하게 활동할 수 있도록 만들어집니다.
            우주복에는 생명 유지 장치, 통신 장치, 온도 조절 장치 같은 온갖 첨단 장치가 부착되어 있어 비행사가 우주선 밖에서
            8시간 정도를 견딜 수 있게 해 줍니다. 
            따라서 우주 탐사를 위해서는 우주복이 필수적입니다.
            2019년 두 여성 우주 비행사가 우주 탐사를 떠나지 못 했던 이유도 바로 이 우주복 때문이었습니다. 
            당시의 우주복은 백인 남성 12명의 평균 체격에 맞춰 제작되었습니다. 
            그래서 우주 탐사를 떠날 두 여성의 몸에 맞는 우주복이 없었고, 긴급히 남성 비행사가 여성 비행사들을 대신하여 떠나게 되었습니다. 
            이 사건 이후 비행사의 키에 따라 늘리고 줄일 수 있는 우주복이 개발되었고, 키가 작은 비 행사들도 무사히 우주 탐사를 떠날 수 있게 되었습니다.
            """;

    private final Map<String, SampleMeta> metas = Map.of(
            "spacesuit", new SampleMeta(
                    "spacesuit",
                    "몸에 맞는 우주복이 필요한 이유",
                    "spacesuit.png",
                    "text/plain",
                    "우주복과 안전/포용 관련 짧은 지문"
            )
    );

    private final Map<String, String> contents = Map.of(
            "spacesuit", SAMPLE_TEXT_SPACESUIT
    );

    public List<SampleMeta> list() {
        return List.copyOf(metas.values());
    }

    public String materialize(String sampleId) throws Exception {
        SampleMeta m = metas.get(sampleId);
        if (m == null) {
            throw new IllegalArgumentException("Unknown sample: " + sampleId);
        }
        String text = contents.get(sampleId);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Empty sample content for " + sampleId);
        }

        // ✅ 텍스트를 UTF-8로 저장하고 fileId 발급
        FileMetadata meta = storage.storeText(text, m.filename(), m.mimeType());
        return meta.getId();
    }
}
