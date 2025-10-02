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

    private static final String SAMPLE_TEXT_YELLOW_UMBRELLA = """
            비가 주륵주륵 내리는 하굣길이었습니다.
            지우는 우산을 깜빡 잊고 나와 발만 동동 굴렀습니다.
            그때 누군가 지우의 어깨를 톡톡 쳤습니다. 뒤돌아보니 같은 반 친구 서아였습니다.
            서아는 커다란 노란 우산을 쓰고 있었습니다. "지우야, 우산 없어? 같이 쓰고 가자!" 서아는 활짝 웃으며 말했습니다.
            지우는 서아의 우산 속으로 쏙 들어갔습니다. 둘은 조심조심 물웅덩이를 피해 함께 걸었습니다. 노란 우산 덕 분에 지우는 비를 한 방울도 맞지 않았습니다.
            집 앞에 도착해서 지우는 서아에게 큰 소리로 인사했습니다. "서아야, 정말 고마워!" 서아는 손을 흔들어 주었 습니다. 혼자였다면 쓸쓸했을 빗길이 친구와 함께여서 즐겁고 따뜻하게 느껴졌습니다.
            """;

    private static final String SAMPLE_TEXT_TRASHCAN = """
            요즘 길거리를 걷다가 쓰레기를 버릴 데가 없어 난감할 때가 많다.
            최근 길거리에 설치된 쓰레기통의 수가 많이 줄어들었기 때문이다.
            쓰레기통 수를 줄이면 길거리가 깨끗하게 유지될 것으로 예상했지만, 오히려 길거리는 더 더러워지고 있다.
            길거리를 청결하게 유지하기 위해서는 쓰레기통을 더 많이 설치해야 한다.
            그 이유를 살펴보면 다음과 같다.첫째, 쓰레기통이 많으면 사람들이 쓰레기를 아무 데나 버리지 않게 된다.
            쓰레기통이 부족하여서 버려야 할 쓰레기를 계속 갖고 있게 되면 사람들은 불편함을 느낀다.
            이때 쓰레기통을 쉽게 찾을 수 없다면 사람들이 길가에 쓰레기를 버릴 가능성이 높아진다.둘째, 쓰레기의 수거와 처리가 쉬워진다.
            여기저기 버려져 있는 쓰레기는 일일이 수거하기 어렵지만, 쓰레기통이 있으면 쓰레기통에 모여 있는 쓰레기만 수거하면 되어서 처리가 편리하다.
            쓰레기 수거가 잘 이루어지면 길거리가 더욱 깨끗해질 수 있다.
            사람들이 편리하게 쓰레기를 처리하고 길거리를 깨끗하게 유지할 수 있도록 쓰레기통을 더 많이 설치해야 한다.
            """;

    private static final String SAMPLE_TEXT_HAIR_STORY = """
            우리 몸 곳곳에는 털이 나 있다.
            눈에 잘 보이는 털도 많지만 아주 짧고 가늘어서 눈에 잘 띄지 않는 털도 있다.
            우리 몸에서 털이 없는 곳은 손바닥, 발바닥, 입술뿐이다. 털은 우리 몸에서 어떤 일을 할까?
            털은 우리 몸의 온도를 일정하게 유지해 준다. 
            추울 때는 근육이 오그라들면서 털이 위로 선다. 
            위로 선 털이 따뜻한 공기를 머무르게 하여 몸의 온도를 높인다. 
            더울 때는 근육이 느슨해지면서 털이 눕게 된다. 
            누워 있는 털 위로 따뜻한 공기가 빠져나가면서 몸의 온도가 내려간다.
            털은 충격이나 이물질로부터 우리 몸을 보호해 준다. 
            머리카락은 머리를 부딪혔을 때 충격을 완화한다. 
            코털, 귓구멍 속의 털, 눈썹 등과 같은 털은 더러운 물질이 몸에 들어오지 못하도록 막아 준다.
            털은 우리 몸의 건강 상태를 알려 준다. 
            몸이 아프거나 피곤할 때 머리카락이 부스스해지거나 빠지기도 한다. 
            몸에 있는 털이 갑자기 많이 빠진다면 아픈 곳이 없는지 살펴봐야 한다.
            몸에 있는 털은 우리의 안전과 건강을 위해 여러 가지 일을 한다. 
            우리는 털이 하는 일을 알고 고마운 마음을 가질 필요가 있다.
            """;

    private static String SAMPLE_TEXT_SAMULNORI = """
            사물놀이는 꽹과리, 징, 북, 장구 네 가지 악기를 두드리거나 쳐서 소리를 내는 놀이이다.
            사물놀이에서는 서로 다른 악기들의 소리가 한데 어우러져 경쾌하고 흥겨운 가락을 만든다. 
            사물놀이의 네 가지 악기 소리는 천둥, 비, 구름, 바람과 같은 자연의 소리를 나타낸다.
            꽹과리의 소리는 천둥을 나타낸다. 
            꽹과리는 네 악기 중에 가장 작지만 가장 크고 높은 소리를 낸다. 
            이 요란한 울림이 천둥을 닮았다. 
            꽹과리는 사물놀이의 시작을 알리며 음악을 이끌어 가는 역할을 한다.
            장구 소리는 비를 나타낸다. 
            장구채로 양쪽에 있는 면을 두드리는 소리가 마치 장독대에 떨어지는 경쾌한 빗방울 소리와 비슷하다. 
            장구는 사물놀이에서 박자를 조절해 주는 역할을 한다.
            북소리는 구름을 나타낸다. 
            북을 치면 힘찬 소리가 널 리 울려 퍼지는데, 그것이 하늘에 떠 있는 구름의 모습을 떠올리게 한다. 
            북은 사물놀이의 음악을 든든하게 받쳐 주는 기둥과 같은 역할을 한다.
            징 소리는 바람을 나타낸다. 
            징의 소리는 낮고 부드러우며 웅장한데 그 소리가 마치 바람과 닮았다. 
            징의 소리는 긴 울림이 있어서 전체적으로 다른 악기들의 소리를 감싸안아 주는 역할을 한다.
            """;

    private final Map<String, SampleMeta> metas = Map.of(
            "spacesuit", new SampleMeta(
                    "spacesuit",
                    "몸에 맞는 우주복이 필요한 이유",
                    "spacesuit.png",
                    "text/plain",
                    "우주복과 안전/포용 관련 짧은 지문"
            ),
            "umbrella", new SampleMeta(
                    "umbrella",
                    "우산에 대한 에피소드",
                    "umbrella.png",
                    "test/plain",
                    "노랑우산에 대한 짧은 에피소드에 대한 지문"
            ),
            "trashcan", new SampleMeta(
                    "trashcan",
                    "쓰레기통에 대한 고찰",
                    "trashcan.png",
                    "text/plain",
                    "쓰레기통을 더 많이 설치하면 수거 및 처리도 편리해진다는 주장"
            ),
            "hairstory", new SampleMeta(
                    "hairstory",
                    "털의 역할",
                    "hairstory.png",
                    "text/plain",
                    "털은 우리 몸에 많은 이점이 있다는 주장"
            ),
            "samulnori", new SampleMeta(
                    "samulnori",
                    "사물놀이의 종류",
                    "samulnori.png",
                    "text/plain",
                    "사물놀이는 다양하게 조화로운 연주를 이끌어갑니다."
            )
    );

    private final Map<String, String> contents = Map.of(
            "spacesuit", SAMPLE_TEXT_SPACESUIT,
            "umbrella", SAMPLE_TEXT_YELLOW_UMBRELLA,
            "trashcan", SAMPLE_TEXT_TRASHCAN,
            "hairstory", SAMPLE_TEXT_HAIR_STORY,
            "samulnori", SAMPLE_TEXT_SAMULNORI
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
