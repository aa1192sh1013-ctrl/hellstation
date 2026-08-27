# 앱 구조와 인계 사항 (architecture)

1단계(설계)에서 만든 것과, 다음 사람이 알아야 할 것을 정리합니다.

관련 문서: [`api-validation.md`](api-validation.md) · [`data-model.md`](data-model.md) · [`crowding-levels.md`](crowding-levels.md)

---

## 1. 만들어진 것

```
HellStation/
├── settings.gradle.kts              모듈 목록, 저장소 설정
├── build.gradle.kts                 최상위 빌드 파일 (플러그인 버전 선언만)
├── gradle.properties                Gradle/AndroidX 설정
├── gradle/
│   ├── libs.versions.toml           ★ 라이브러리 버전은 전부 여기서만 관리
│   └── wrapper/
│       └── gradle-wrapper.properties
├── docs/
│   ├── api-validation.md            어떤 API를 쓸 수 있는지 (실제 호출 검증 결과)
│   ├── data-model.md                노선/역/방향/열차/구간 데이터 구조
│   ├── crowding-levels.md           혼잡도 5단계 + 신뢰도 3단계 기준
│   └── architecture.md              이 문서
└── app/
    ├── build.gradle.kts             앱 모듈 빌드 설정, BuildConfig(API 주소·키)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/hellstation/
            ├── navigation/          ← 설계 담당이 만든 부분
            │   ├── MainActivity.kt
            │   ├── HellDestination.kt
            │   ├── HellNavHost.kt
            │   └── PlaceholderScreens.kt
            ├── data/                (비어 있음 — 데이터·기능 담당)
            ├── domain/              (비어 있음 — 데이터·기능 담당)
            └── ui/                  (비어 있음 — 화면 담당)
```

### 기술 선택

| 항목 | 값 | 이유 |
|---|---|---|
| Gradle | 8.14.3 | AGP 8.13 요구사항 충족 |
| Android Gradle Plugin | 8.13.2 | 2026-08 기준 최신 안정판 |
| Kotlin | 2.4.10 | 최신 안정판. Compose Compiler 플러그인 버전과 동일하게 유지할 것 |
| Compose BOM | 2026.08.00 | Compose 라이브러리 버전은 BOM이 맞춰 줌 |
| `compileSdk` / `targetSdk` | 36 | |
| **`minSdk`** | **26** | `java.time`을 desugaring 없이 쓰기 위한 하한. 데이터 모델이 `Instant`를 쓰므로 이 값을 낮추려면 `coreLibraryDesugaring`을 켜야 합니다 |
| 화면 이동 | navigation-compose 타입 안전 경로 | 문자열 경로 오타를 컴파일 시점에 잡음 |
| 네트워크 | OkHttp + Retrofit + kotlinx.serialization | 이미 의존성에 넣어 두었습니다 |

---

## 2. 처음 빌드하는 방법

> ⚠️ **이 프로젝트는 아직 한 번도 빌드된 적이 없습니다.**
> 설계 담당이 작업한 환경에는 JDK·Android SDK·Gradle이 전혀 설치되어 있지 않아 빌드를 확인하지 못했습니다.
> **처음 여는 사람이 아래 순서대로 확인해 주세요.**

1. **Android Studio를 설치하고 이 폴더를 엽니다.**
   Android Studio가 JDK와 Android SDK를 함께 설치하고, `local.properties`(SDK 경로)와
   Gradle Wrapper 실행 파일(`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`)을 자동으로 만들어 줍니다.

   Gradle이 이미 설치되어 있다면 명령줄에서 아래로 wrapper를 만들 수도 있습니다.

   ```bash
   gradle wrapper --gradle-version 8.14.3
   ```

2. **Gradle Sync**가 끝나면 빌드합니다.

   ```bash
   ./gradlew assembleDebug
   ```

3. 에뮬레이터나 기기에서 실행하면 Splash → Heatmap 순으로 뜨고,
   버튼을 눌러 **검색 · 역 상세 · 결과** 화면 사이를 오갈 수 있습니다.
   전부 회색 임시 화면이며 디자인은 없습니다. 이 단계에서는 그게 정상입니다.

### 인증키 넣기 (선택)

키가 없어도 빌드와 실행은 됩니다. 실시간 데이터를 붙이려면 `local.properties`에 아래를 추가하세요.
이 파일은 git에 올라가지 않습니다.

```properties
SEOUL_OPENAPI_KEY=열린데이터광장_일반_인증키
SEOUL_REALTIME_SUBWAY_KEY=열린데이터광장_실시간_지하철_전용키
```

키를 넣지 않으면 `"sample"`이 들어가고, 실시간 도착정보는 **서울역만** 조회됩니다.
발급 방법과 소요 시간은 [`api-validation.md`](api-validation.md)의 "앱 설정에 미치는 영향"에 있습니다.
**실시간 전용키는 승인에 1~2일 걸리므로 지금 신청해 두세요.**

---

## 3. 화면 이동 구조

```
        Splash
          │  (역 목록 로딩 완료)
          ▼
     ┌─ Heatmap ─────────────┐        ← 홈. 앱을 열면 여기가 먼저 보입니다
     │      │                │
     │  역 누름           검색 누름
     │      ▼                ▼
     │  StationDetail     Search(ORIGIN)
     │      │                │
     │  "여기서 출발"      출발역 선택
     │      ▼                ▼
     │   Search(DESTINATION) ◄┘
     │            │
     │        도착역 선택
     │            ▼
     └────────  Result  ─────┘
              (Ride or Wait)
```

목적지는 `HellDestination.kt`에, 이동 규칙은 `HellNavHost.kt`에 있습니다.
**화면 안에서 `navController`를 직접 호출하지 마세요.** 콜백(`onStationClick` 등)을 받아서 쓰고,
실제 이동은 `HellNavHost`가 정하도록 두면 흐름을 바꿀 때 한 곳만 고치면 됩니다.

### 경로 인자에 domain 타입을 쓰지 않는 이유

`navigation`은 `domain`을 모릅니다. 그래서 경로 인자는 `lineCode: String`, `stationCode: String` 같은
원시 타입뿐입니다. 화면 안에서 `StationId`로 복원해서 쓰세요. ([`data-model.md`](data-model.md) 12절)

---

## 4. 🎨 화면 담당(ui-developer)에게 넘기는 일

### 바로 시작할 수 있는 것

- `com.hellstation.ui.theme.HellStationTheme` 만들기
  → `MainActivity.kt`의 `MaterialTheme { }`을 이것으로 바꿔 주세요 (그 파일에 주석으로 표시해 두었습니다)
- 혼잡도 5단계 색 정의 — 제약 조건은 [`crowding-levels.md`](crowding-levels.md) 4절에 있습니다.
  특히 **`UNKNOWN`은 무채색 회색**이어야 하고, **색만으로 구분하면 안 됩니다**
- `com.hellstation.ui.{splash,heatmap,search,station,result}` 아래에 진짜 화면 만들기
  → 다 만들면 `HellNavHost.kt`에서 `Placeholder*` 호출을 바꾸고 `PlaceholderScreens.kt`를 지우세요

### 반드시 처리해야 할 것 3가지

`res/**`는 화면 담당 소유라서 설계 담당이 만들 수 없었습니다. 아래는 **화면 담당만 할 수 있는 일**입니다.

**① 앱 아이콘** — 지금 앱에 아이콘이 없어서 안드로이드 기본 아이콘이 뜹니다.
`res/mipmap-*/`에 아이콘을 만들고 `AndroidManifest.xml`의 `<application>`에 `android:icon`을 추가해 주세요.
(지하철 + 악마를 귀엽게 합친 대표 캐릭터가 여기에 들어갑니다.)

**② 앱 테마** — 지금 `android:theme="@android:style/Theme.Material.Light.NoActionBar"`(안드로이드 기본값)를 쓰고 있어서
앱을 켤 때 흰 화면이 잠깐 번쩍입니다. `res/values/themes.xml`에 앱 테마를 만들고 매니페스트에서 바꿔 주세요.
`androidx.core:core-splashscreen`은 이미 의존성에 들어 있으니 시스템 Splash도 여기서 붙일 수 있습니다.

**③ 평문 HTTP 허용 범위 좁히기** ⚠️ **보안 항목**
서울 열린데이터광장 API가 HTTPS를 지원하지 않아서, 지금은 `AndroidManifest.xml`에
`android:usesCleartextTraffic="true"`로 **앱 전체의 평문 통신을 열어 둔 상태**입니다.
아래 파일을 만들어 두 도메인만 허용하도록 좁혀 주세요.

```xml
<!-- app/src/main/res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">swopenapi.seoul.go.kr</domain>
        <domain includeSubdomains="false">openapi.seoul.go.kr</domain>
    </domain-config>
</network-security-config>
```

그리고 `AndroidManifest.xml`의 `<application>`에서 `android:usesCleartextTraffic="true"`를 지우고
`android:networkSecurityConfig="@xml/network_security_config"`로 바꿔 주세요.
매니페스트는 소유자가 정해져 있지 않으니 이 변경은 직접 하셔도 됩니다.

### 임시 데이터로 시작하세요

2단계에서는 실제 데이터가 아직 없습니다. `CrowdIndex` 모양([`data-model.md`](data-model.md) 9절)에 맞는
가짜 데이터를 만들어 화면을 완성하세요. 3단계에서 데이터·기능 담당이 진짜 값으로 갈아끼웁니다.
**5단계 전부와 `UNKNOWN`, 그리고 신뢰도 `LOW`까지 포함한 가짜 데이터를 쓰세요.** 잘 나오는 경우만 만들면 나중에 깨집니다.

---

## 5. ⚙️ 데이터·기능 담당(feature-developer)에게 넘기는 일

### 먼저 읽을 것

[`api-validation.md`](api-validation.md)의 **"반드시 알아야 할 함정"** 4가지를 먼저 읽어 주세요.
특히 `barvlDt == "0"` 문제와 `recptnDt` 지연 보정은 모르고 짜면 반드시 틀립니다.

### 만들어야 할 것

1. **`domain/model/`** — [`data-model.md`](data-model.md) 1~10절의 Kotlin 코드를 그대로 만드세요.
   설계 담당은 `domain/`을 수정할 수 없어서 문서에 명세로만 남겼습니다.
2. **`domain/repository/`** — [`data-model.md`](data-model.md) 11절의 인터페이스 4개
3. **`data/remote/`** — API 호출과 DTO→domain 변환.
   **역명·노선 정규화는 전부 여기서 끝내세요.** ([`data-model.md`](data-model.md) 4절)
4. **`data/local/baseline/`** — 서울교통공사 혼잡도 CSV 로더.
   CSV는 `app/src/main/assets/`에 넣는 것을 권장합니다 ([`data-model.md`](data-model.md) 13절)
5. **`domain/usecase/`** — 혼잡도 계산과 Ride or Wait 판단.
   등급·신뢰도 기준은 [`crowding-levels.md`](crowding-levels.md)에 있고, **그 판정은 한 함수에만 존재해야 합니다.**

### 이미 준비해 둔 것

`app/build.gradle.kts`에 아래가 이미 들어 있습니다. **이 파일은 설계 담당 소유이니 직접 고치지 말고,
필요한 라이브러리가 더 있으면 알려 주세요.**

```kotlin
BuildConfig.SEOUL_REALTIME_BASE_URL  // "http://swopenapi.seoul.go.kr/api/subway/"
BuildConfig.SEOUL_OPENAPI_BASE_URL   // "http://openapi.seoul.go.kr:8088/"
BuildConfig.SEOUL_REALTIME_SUBWAY_KEY
BuildConfig.SEOUL_OPENAPI_KEY
```

의존성: OkHttp, Retrofit, kotlinx.serialization, Coroutines, DataStore, `kotlinx-coroutines-test`.

### 설계상 지켜 주셨으면 하는 것

- **`domain`은 Android를 import하지 않습니다.** 그래야 에뮬레이터 없이 단위 테스트가 됩니다.
- **저장소는 예외를 던지지 않습니다.** 실패하면 `UNKNOWN`이나 빈 리스트를 돌려주세요.
  지하철 안에서 신호가 끊기는 것은 예외가 아니라 정상 상황입니다.
- **데이터가 없을 때 `EASY`나 `0%`를 기본값으로 넣지 마세요.** 반드시 `UNKNOWN`입니다.

---

## 6. 아직 해결되지 않은 것

| 항목 | 내용 | 누가 |
|---|---|---|
| 빌드 미검증 | 이 환경에 JDK/Android SDK가 없어 한 번도 빌드하지 못했습니다 | 처음 여는 사람 |
| 실시간 전용키 | 승인에 1~2일. 없으면 서울역만 조회됨 | 사람 (직접 신청) |
| `realtimePosition` 응답 구조 | 전용키가 없어 확인하지 못했습니다 ([`api-validation.md`](api-validation.md) 5번) | 데이터·기능 담당 |
| 혼잡도 CSV 실물 | 다운로드해서 컬럼명·인코딩(EUC-KR 여부) 확인 필요 | 데이터·기능 담당 |
| 평문 HTTP 범위 | 지금 앱 전체가 열려 있음 | 화면 담당 |
| 앱 아이콘 / 테마 | 안드로이드 기본값 | 화면 담당 |
| 칸별 혼잡도 지원 여부 | TMAP 키가 필요. 1차에서는 빼는 것을 권장 | 사람 (결정) |
| 경로 탐색 범위 | 환승 경로를 직접 계산할지 | 사람 (결정) |
