# AI Maid

[简体中文](README.md) | [한국어](README.ko.md) | [English](README.en.md)

Minecraft Fabric용 메이드 동료 모드입니다. Core는 주인 관계, Brain AI, 35칸 인벤토리와 장비 슬롯 6개, GUI, 음식을 통한 체력 회복, 애니메이션, 근접 무기·활·쇠뇌를 사용하는 PvE 전투를 제공합니다. 정식 Addon은 각각 벌목, 농사, 동물 먹이 주기, 아이템 분류 및 운반을 담당합니다.

현재 버전: **Core / Farming / Lumber 1.3.0, Feeding / Hauling 1.2.1, LLM Addon 0.3.0**. 실행 환경: **Minecraft 26.2, Java 25, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2**, Loom 1.17-SNAPSHOT, unobfuscated / no-remap.

아래에 연결된 상세 문서는 현재 중국어로 제공됩니다.

## 사용 방법

- 쿠키를 주면 일정 확률로 길들일 수 있습니다. 주인은 Shift + 우클릭으로 메뉴를 열 수 있으며, 손에 아이템을 들고 있어도 됩니다.
- 주인이 쿠키, 사과 또는 빵을 들고 일반 우클릭하면 체력이 4 회복됩니다. 체력이 가득 찼거나 크리에이티브 모드인 경우 음식은 소모되지 않습니다.
- Core는 자유 행동, 따라가기 및 주인 보호, 대기, 적극 전투 모드를 제공합니다. 따라가기 모드에서는 자신을 방어하고 주인을 보호하며 주인의 공격을 돕습니다. 적극 전투 모드에서는 주변의 적대적인 몹도 공격합니다. PvE 규칙은 플레이어, 같은 팀의 대상, 길들인 반려동물을 보호합니다.
- 작업 Addon을 설치하면 해당 작업 명령이 GUI에 자동으로 추가됩니다. 작업 중 공격을 받으면 자신을 방어하고, 전투가 끝나면 작업을 재개합니다.
- 벌목은 한 나무에서 걸어서 접근할 수 있고 손이 닿는 원목을 처리합니다. 농사는 일반 작물 4종을 관리하며, 동물 먹이 주기는 실제 먹이 아이템을 소모합니다. 분류 및 운반은 구리 상자에서 아이템을 꺼내 빈 상자나 같은 종류의 아이템이 있는 상자로 옮깁니다.
- 선택 사항인 LLM Addon은 채팅과 자율 과업을 제공합니다. 모델이 환경을 관측하고 제한된 행동 집합에서 하나를 선택한 뒤, 실제 실행 결과를 기다렸다가 다음 행동을 결정합니다. 이동, 도구 장착, 지정한 나무 벌목, 작물 4종 수확, 지정한 상자에 아이템 보관, 주인에게 복귀를 지원합니다. 기존의 지속 작업 모드도 사용할 수 있습니다.
- `/maid chat`에서 “밀 8개를 모아서 좌표 x y z의 상자에 넣고 돌아와”와 같이 요청할 수 있습니다. 수집할 때는 인벤토리에 이미 있는 아이템도 활용합니다. 상자가 가득 차는 등의 실패가 발생하면 실제로 보관한 수량을 함께 반환합니다. 모델은 주인이 허용한 경우에만 예비 상자를 사용해야 합니다. LLM Addon은 기본적으로 비활성화되어 있으며, 설정 방법은 [LLM 사용 안내](addon/llm/README.md)를 참고하세요.
- 관리자가 오버월드에서 `/maid test-scene`을 실행하면 위쪽 공중에 31×31 테스트 플랫폼을 만들고 그곳으로 이동합니다. 플랫폼에는 관리자에게 연결된 대기 상태의 메이드, 도구와 재료, 작업 시나리오 4종이 준비되어 있습니다. 실행할 때마다 새 플랫폼을 만들며 기존 지형을 덮어쓰지 않습니다. 사용 방법은 [테스트 공간 안내](docs/TEST_SCENE.md)를 참고하세요.

지속 작업의 범위와 매개변수는 [작업 Addon 사용 안내](docs/ADDONS_1_2.md), 자율 과업의 행동·예산·생명주기는 [행동 인터페이스와 자율 과업](docs/AUTONOMOUS_TASKS.md)을 참고하세요. 클라이언트와 서버의 Core 및 선택한 Addon 버전을 맞춰 설치하세요. **기존 TestAddon JAR는 삭제하세요.** 작업 Addon 4개를 모두 설치할 필요는 없습니다.

## 개별 빌드

루트 프로젝트, 작업 Addon 4개, `addon/llm`, `dev/devrun`은 각각 독립적인 Gradle 빌드입니다. IntelliJ IDEA에서 따로 열 수 있습니다. Addon은 Maven 좌표로 Core를 의존성에 추가하고, 로컬 복합 빌드에서는 해당 의존성을 Core 소스 프로젝트로 대체합니다.

```powershell
.\gradlew.bat runClient                         # Core, 전투 포함
.\gradlew.bat -p addon/lumber runClient         # Core + 벌목
.\gradlew.bat -p addon/farming runClient        # Core + 농사
.\gradlew.bat -p addon/feeding runClient        # Core + 동물 먹이 주기
.\gradlew.bat -p addon/hauling runClient        # Core + 분류 및 운반
.\gradlew.bat -p addon/llm runClient            # Core + LLM
.\gradlew.bat -p dev/devrun runClient           # Core + 작업 Addon 4개 + LLM
.\gradlew.bat build
.\gradlew.bat -p addon/lumber build             # 다른 Addon도 같은 방식으로 빌드
.\gradlew.bat -p dev/devrun build
```

JAR 파일은 각 빌드의 `build/libs`에 생성됩니다. 테스트 픽스처는 배포 파일에 포함되지 않습니다. 작업 Addon 4개의 `build`에는 각 Addon의 전용 서버 GameTest가 포함되며, devrun에는 모드 간 회귀 테스트가 포함됩니다. 원격 Maven 배포 저장소는 아직 설정되지 않았습니다.

[LLM Addon 사용 및 설정 안내](addon/llm/README.md)를 참고하세요. 해당 Addon의 `build`에는 프로토콜 테스트, 모의 HTTP 테스트, 전용 서버 GameTest가 포함됩니다. `runClientGameTest`는 실제 클라이언트와 로컬 모의 모델로 전체 흐름을 검증합니다. 실제 모델 서비스의 접속 설정은 서버 운영자가 제공합니다.

## 검증

```powershell
.\gradlew.bat runGameTest
.\gradlew.bat runGameTest -PaddonFixtures
.\gradlew.bat -p addon/hauling runGameTest
.\gradlew.bat -p dev/devrun runGameTest
.\gradlew.bat runClientGameTest
.\gradlew.bat -p addon/lumber runClientGameTest
.\gradlew.bat -p dev/devrun runClientGameTest
```

임시 월드를 사용하세요. 실제 클라이언트 테스트의 스크린샷은 각 빌드의 `build/run/clientGameTest/screenshots`에 저장됩니다. 이후 빌드에서 이 파일들이 정리될 수 있습니다. 가장 최근에 기록된 실행 결과, 로그, 스크린샷은 [검증 기록](docs/ACCEPTANCE.md)을 참고하세요.

- [작업 스케줄링과 TLM 참고 사항](docs/WORK_SCHEDULING.md)
- [행동 인터페이스와 자율 과업 아키텍처](docs/AUTONOMOUS_TASKS.md)
- [LLM 아키텍처, TLM 참고 사항 및 검증](docs/LLM.md)
- [Addon 공개 API와 생명주기](docs/ADDON_API.md)
- [Core 1.1 개발 이력](docs/CORE_1_1.md)
- [전투 설계](docs/COMBAT.md)
- [블록의 지속 파괴, 나무 전체 벌목 및 사망 시 아이템 드롭](docs/LUMBER_AND_DEATH.md)
- [애니메이션 설계](docs/ANIMATION.md)
- [작업 중 자기방어와 작업 재개](docs/WORK_DEFENSE.md)

문제 제보: [GitHub Issues](https://github.com/ozozorz/ai-maid/issues). 라이선스: [CC0-1.0](LICENSE).
