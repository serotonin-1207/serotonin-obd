# TODO.md — LeafOBD

표기: `- [ ]` 해야 할 일 · `- [x]` 완료 · `- [!]` 차단됨(이유 포함)

## Phase 0 — 저장소 점검

- [x] Android Studio 생성 프로젝트를 작업 폴더로 이전
- [x] JDK(내장 JBR 21), Android SDK, AGP 9.3.1, Gradle 9.5.0, Kotlin 2.2.10 확인
- [x] compileSdk 37 자동 설치 및 적용 (최신 AndroidX 요구사항)
- [x] 기준 `assembleDebug` 성공
- [x] 한글 경로 문제 해결 (`android.overridePathCheck` + 빌드 출력 폴더 ASCII 이전)

## Phase 1 — 기본 UI

- [x] Material 3 테마 (Leaf 녹색, 다이내믹 컬러 비활성)
- [x] Navigation Compose 내비게이션
- [x] 홈 / 장치 / 진단 / 삭제 / 로그 / 설정 / 사용절차 화면
- [x] 모든 문자열 한국어
- [x] 안전 경고 카드 (홈·삭제·진단·사용절차)

## Phase 2 — 권한과 장치 목록

- [x] Android 12+ `BLUETOOTH_CONNECT` 런타임 권한
- [x] Android 11 이하 설치 시 권한 분기
- [x] 권한 확인 전 Bluetooth API 호출 금지
- [x] 페어링된 장치 목록 (OBD 추정 장치 우선 정렬)
- [x] MAC 주소 마스킹 표시
- [x] 시스템 Bluetooth 설정 / 앱 설정 이동 버튼
- [x] 권한 거부 시 크래시 없이 안내

## Phase 3 — Bluetooth Classic 연결

- [x] SPP UUID RFCOMM 소켓 연결
- [x] 15초 연결 타임아웃
- [x] 연결 전 `cancelDiscovery()`
- [x] 논블로킹 읽기 통로 (`available()` 기반)
- [x] 스트림·소켓 정리 (`onCleared`, 재연결 시)
- [x] 실패 유형별 한국어 안내 (연결 거부 / 사용 중 / SPP 없음 / 무응답)
- [!] 실제 어댑터 연결 검증 — 어댑터가 아직 없어 실기기 확인 불가

## Phase 4 — ELM327 초기화

- [x] `Mutex` 직렬 명령 큐
- [x] `>` 프롬프트 기반 응답 리더 + 타임아웃
- [x] 전송 전 잔여 버퍼 비우기
- [x] ATZ → ATE0 → ATL0 → ATS0 → ATH0 → ATSP0 → ATI → ATRV
- [x] ATDP(선택) 프로토콜 확인
- [x] ATZ 후 안정화 대기
- [x] 실패한 명령 이름 표시
- [x] `FakeElm327Transport` (5가지 시나리오)

## Phase 5 — 표준 DTC 읽기

- [x] Mode 03 / 07 / 0A
- [x] echo · SEARCHING · 프롬프트 · 공백 · CRLF · 다중 프레임 정규화
- [x] CAN 개수 바이트 / KWP 무개수 형식 자동 판별
- [x] 0000 패딩 제거, 잘린 응답 표시
- [x] `NO DATA` 를 오류가 아닌 "데이터 없음"으로 구분
- [x] 단위 테스트 24건

## Phase 6 — 로그 저장

- [x] `DiagnosticSession` / `CommandLog` 모델
- [x] TXT (사람이 읽는 형식)
- [x] JSON (구조화)
- [x] 앱 내부 저장소 `files/logs/`
- [x] FileProvider 공유 (TXT / JSON / 둘 다)
- [x] 결과 요약 클립보드 복사 (지시서 22장 양식)
- [x] MAC 마스킹 검증 테스트

## Phase 7 — 안전한 표준 DTC 삭제

- [x] 사전조건 6가지 검사 (`ClearEligibility`)
- [x] 체크박스 3개 + 확인 문구 입력
- [x] 조건 미충족 시 버튼 비활성 + 미충족 사유 표시
- [x] Mode 04 1회 실행 (반복 삭제 금지, 재시도 없음)
- [x] 3초 대기 후 자동 재조회
- [x] 삭제 전후 비교 (사라진 / 남은 / 새로 나타난 코드)
- [x] 재발 코드 강조 + 활성 고장 경고
- [x] 결과 자동 저장

## Phase 8 — 검증과 산출물

- [x] `assembleDebug` 성공
- [x] `testDebugUnitTest` 전체 통과
- [x] README / TODO / CHANGELOG / ARCHITECTURE / SAFETY / TESTING 작성
- [x] 갤럭시 A23(SM-A235N, Android 14 / API 34) 설치 및 실행 확인 — 크래시 없음
- [x] 테스트 모드 "삭제 후 재발" 시나리오 실기기 확인
      (진단 8단계 → 코드 표시 → 삭제 조건 잠금 → 삭제 → 재조회 → 재발 경고 → 로그 공유)
- [x] 무응답 시나리오 실기기 확인 — 타임아웃 처리 정상, 크래시 없음
      (이 과정에서 프리즈 프레임 단계가 실패를 성공으로 표시하던 버그를 발견·수정)
- [ ] 나머지 테스트 시나리오 3종 실기기 확인 (표준 응답 / 데이터 없음 / 통신 오류)
- [ ] 확인 문구 입력 후 실제 삭제 실행 — adb 한글 입력 불가로 직접 확인 필요
- [ ] 권한 **거부** 시 크래시 없이 안내되는지 실기기 확인
- [ ] Bluetooth **꺼짐** 상태 안내 실기기 확인
- [ ] 화면 회전 · 백그라운드 복귀 후 상태 유지 확인
- [!] 실제 어댑터 페어링·연결·ATI·ATRV 검증 — 어댑터 도착 후 진행
- [!] 실제 차량 Mode 03/07/0A 읽기 — 어댑터 도착 후 진행
- [ ] `screenshots/` 폴더에 화면 캡처 추가

## 표준 명령 확장 (2026-07-30)

- [x] Mode 02 프리즈 프레임 — DTC 읽기보다 먼저 실행, 원시 응답 전량 보존
- [x] `0101` 경고등 상태와 ECU 보고 DTC 개수
- [x] `0100`/`0120`/`0140` 지원 PID 비트맵 연속 조회
- [x] 표준 PID 15종 해석 (SAE J1979 공개 계산식)
- [x] Mode 09 `0902` VIN — 기본 꺼짐, 마스킹 저장
- [x] `ATH1` CAN 헤더 표시 + ISO-TP 프레임 재조립 → 응답 ECU 기록
- [x] 설정 영속화(SharedPreferences), 차량 이름 변경
- [x] 진단·삭제 화면에서 진단 기록으로 바로 이동
- [x] 단위 테스트 113건 통과
- [ ] 실제 차량에서 프리즈 프레임이 실제로 나오는지 확인 (전기차는 없을 가능성 높음)
- [ ] 실제 차량에서 ATH1 재조립이 맞게 동작하는지 확인
- [!] `ATMA` 읽기 전용 CAN 모니터링 — 실제 차량 응답을 본 뒤 필요성 판단

## UDS 오류코드 읽기 (2026-08-04)

- [x] **실차에서 23개 ECU 응답 확인** — 원인은 방송 주소만 쓴 것이었다
- [x] 흐름 제어(`ATFCSH`/`ATFCSD`/`ATFCSM`)로 다중 프레임 완성
- [x] `UdsDtcParser` — 3바이트 DTC + 상태 비트 해석
- [x] ECU 오류코드 화면, 확정/현재 고장 코드 강조, 정비소 전달용 복사
- [x] 실차 응답을 단위 테스트 검증 자료로 사용 (156건 통과)
- [ ] **실차에서 오류코드 읽기 실행** — 764의 12건을 완전히 받는지
- [ ] 받은 코드로 증상 원인 좁히기

## ECU 응답 스캔 (2026-08-04)

- [x] `ATSH` 로 개별 ECU 주소 지정 (지금까지는 방송 7DF 만 사용했다)
- [x] 표준 UDS 읽기 요청 3종 (`3E00`, `22F190`, `1902FF`)
- [x] 빠른 스캔(8개) / 전체 스캔(240개) 2단계 방식
- [x] 부정 응답(7F)을 "ECU 존재"로 구분, NRC 한국어 설명
- [x] 쓰기·보안접근·세션제어 미전송을 테스트로 강제
- [x] 단위 테스트 143건 통과
- [ ] **실차에서 빠른 스캔 실행** — 응답하는 주소가 있는지
- [ ] 응답이 있으면 전체 스캔으로 범위 확대

## CAN 버스 확인 및 조사 (2026-08-03)

- [x] `ATMA` 읽기 전용 버스 모니터 (전송 없음)
- [x] CAN ID별 집계, 프레임 0건을 "차량 꺼짐"으로 단정하지 않는 안내
- [x] 공개 자료 조사 결과를 `nissan/README.md` 에 출처와 함께 정리
- [x] 단위 테스트 130건 통과
- [ ] **실차에서 CAN 버스 확인 실행** — 프레임이 잡히는지
- [!] Nissan 전용 UDS(service 0x21) 구현 — ECU 주소를 검증할 방법이 없어 보류.
      현재 차량이 통신 자체가 안 되므로 검증도 불가능하다.

## 프로토콜 탐색 (2026-08-03)

실차 진단에서 `UNABLE TO CONNECT` 만 반복된 문제 대응.

- [x] `ObdProtocol` — ELM327 표준 프로토콜 11종
- [x] `ProtocolProbe` — `0100` 으로 실제 통신 확인, 실패 시 6→7→8→9→5 순차 시도
- [x] `NO DATA`(연결됨)와 `UNABLE TO CONNECT`(연결 실패) 구분
- [x] 설정에 프로토콜 선택 + 순차 시도 토글
- [x] 시도 내역을 로그(`protocolAttempts`)와 화면에 기록
- [x] 모의 어댑터에 자동 프로토콜 실패 시나리오 추가
- [x] 단위 테스트 123건 통과
- [ ] **실차 재시도** — READY 상태 확인 후 진단, 프로토콜 6으로 통신되는지
- [ ] 통신 성공 시 실제 Leaf 가 표준 모드에 무엇을 돌려주는지 기록

## 배포 준비 (2026-07-30)

- [x] release 서명 설정 (`keystore.properties` 분리, `.gitignore` 등록)
- [x] release 빌드 검증 — 8.2MB, 서명됨, `debuggable` 없음, `run-as` 차단 확인
- [x] insecure RFCOMM 폴백 (ELM327 클론 어댑터 호환)
- [x] 앱 아이콘 교체 (잎 + OBD 커넥터, 적응형/monochrome)
- [x] `INSTALL.md` 배포·설치 안내문
- [x] 다크 모드 확인
- [ ] **실제 서명 키스토어 생성** — 비밀번호를 직접 정해야 하므로 사용자가 실행
      (`README.md` 7절의 keytool 명령)
- [ ] Android 8~11 기기에서 권한 흐름 확인 (A23 은 Android 14 라 미확인)
- [ ] 다른 제조사 폰에서 RFCOMM 연결 확인
- [ ] 지인 배포 시 versionCode 관리 규칙 정하기

## 2차 목표 (검증 자료 확보 전까지 착수 금지)

- [!] Nissan Leaf ECU별 DTC 조회 — 공식 자료/실제 로그 없음
- [!] VCM / LBC / OBC·PDM 모듈별 진단 — 검증된 ECU 주소 없음
- [!] Nissan 전용 CAN 헤더 및 서비스 — 추측 구현 금지
- [!] 확장 진단 세션 — 안전 위험, 검증 자료 없음
- [!] Leaf 전용 오류 설명 데이터베이스 — 출처 검증 필요

조건은 [app/src/main/java/com/eunho/leafobd/nissan/README.md](app/src/main/java/com/eunho/leafobd/nissan/README.md) 참조.

## 개선 후보 (안전과 무관, 우선순위 낮음)

- [ ] 진단 기록 화면에 코드 개수·삭제 시도 여부 요약 배지 추가
- [ ] Compose UI 계측 테스트 (`androidTest`)
- [ ] 다중 프레임 응답 실제 로그로 파서 재검증
- [ ] 표준 DTC 설명 데이터(SAE J2012 공개 범위) 추가 검토
