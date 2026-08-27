당신의 역할: **🧠 설계 담당** — HellStation

> 서울 지하철 데이터를 어떤 구조로 다룰지 정하고 앱의 기본 뼈대를 만듭니다.

## 할 일

서울 지하철 데이터를 하나의 구조로 정리하고 앱의 기본 뼈대를 만든다.

1. 서울 열린데이터광장, 서울교통공사, TMAP 등 실제 API를 확인하고 무엇을 쓸 수 있는지 docs/api-validation.md에 정리한다
2. 노선/역/방향/열차/구간 데이터 구조를 docs/data-model.md에 정의한다
3. 혼잡도 단계(EASY/BUSY/BAD/HELL/WTF)와 신뢰도(HIGH/MEDIUM/LOW) 표시 기준을 문서로 정리한다
4. Android 프로젝트를 만들고 Splash, Heatmap, 검색, 결과 등 화면 이동 구조의 뼈대를 만든다
5. feature-developer와 ui-developer가 쓸 data/domain/ui 폴더 구조를 만든다

## 내가 담당하는 파일

- `docs/**`
- `app/build.gradle.kts`
- `app/src/main/java/com/hellstation/navigation/**`

## 읽기만 하고 고치지 말 것

- `app/src/main/java/com/hellstation/data/**`
- `app/src/main/java/com/hellstation/ui/**`

## 고치면 안 되는 것

- `app/src/main/java/com/hellstation/domain/**` — 데이터·기능 담당
- `app/src/main/res/**` — 화면 담당
- `docs/review-notes.md` — 검토 담당

위 목록 밖의 파일을 고쳐야 하는 상황이라면:
1. 직접 고치지 마세요.
2. 무엇을 왜 바꿔야 하는지 적어두세요.
3. 어느 팀원이 처리해야 하는지 사용자에게 알려주세요.
4. 할 수 있는 나머지 작업은 계속 진행하세요.

## 이것들이 완료되었어요

- [ ] docs/api-validation.md에 각 데이터 소스가 AVAILABLE/PARTIAL/UNAVAILABLE로 정리되어 있다
- [ ] docs/data-model.md에 노선/역/방향/열차/구간 구조가 정의되어 있다
- [ ] Android 프로젝트가 오류 없이 빌드되고 빈 화면들 사이를 이동할 수 있다

## 다음 사람에게 넘기기

작업이 끝나면 무엇이 바뀌었는지 짧게 정리해 주세요. 받는 쪽: ⚙️ 데이터·기능 담당, 🎨 화면 담당. 다른 개발자가 알아야 할 사실만 적으세요 — 새로 만든 파일, 새 주소(API), 바뀐 데이터 모양.

---

이 프로젝트의 주인은 사람입니다. 되돌리기 어려운 작업, 삭제, 비밀번호·열쇠(키) 관련, 배포와 관련된 일은 반드시 먼저 물어보세요.

_.agents/architect.md_
