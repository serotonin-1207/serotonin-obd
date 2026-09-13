# HANDOFF.md — 작업 인계 문서

> 이 문서는 다음 세션이 컨텍스트 없이 이어받기 위한 것이다.
> **먼저 이 파일 전체를 읽고, `CLAUDE.md` → `CHANGELOG.md`(최신 항목) → `TODO.md` 순으로 확인한다.**
> 마지막 갱신: 2026-08-04

---

## 0. 프로젝트 한 줄 요약

**Serotonin 범용 OBD** (구 LeafOBD) — 안드로이드 차량 진단 앱.
2019 Nissan Leaf 진단용으로 시작했으나, 표준 OBD-II 차량과 UDS 응답 차량을
두루 다루는 범용 앱으로 확장되었다. 패키지 ID `com.eunho.leafobd` 는 유지한다.

- 제작자: 이은호 (serotonin.1207@gmail.com)
- GitHub(public): https://github.com/serotonin-1207/serotonin-obd
- 현재 버전: v1.1 (versionCode 2)
- 테스트: 171건 전부 통과

---

## 1. 환경 (반드시 지킬 것)

- JDK: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` (내장 JBR 21)
- 프로젝트 경로에 **한글·공백**이 있음: `C:\python\leaf EV 프로젝트`
  - 그래서 `gradle.properties` 에 `android.overridePathCheck=true`
  - `build.gradle.kts` 가 빌드 출력을 **`C:\python\leafobd-build`** 로 옮김
    (이걸 제거하면 단위 테스트가 전부 ClassNotFoundException 으로 실패)
- **APK 위치**(프로젝트 폴더가 아님에 주의):
  - 개발: `C:\python\leafobd-build\app\outputs\apk\debug\app-debug.apk`
  - 배포: `C:\python\leafobd-build\app\outputs\apk\release\SerotoninOBD-1.1.apk`
- 검증 기기: 삼성 갤럭시 A23 (SM-A235N, Android 14 / API 34)
  - USB 시리얼: `R59TA00W3JV`
  - **adb 가 unauthorized 로 잡히면 폰이 아니라 PC adb 서버부터 재시작**
    (`adb kill-server` → `adb devices`). 메모리에도 기록됨.
  - 무선 연결도 됨: `adb mdns services` 로 현재 IP:포트 확인 후 `adb connect`

### 빌드/설치 명령

```bash
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest      # 테스트
.\gradlew.bat assembleDebug          # 개발 APK
.\gradlew.bat assembleRelease        # 배포 APK (keystore.properties 필요)
```

```bash
adb -s R59TA00W3JV install -r "C:\python\leafobd-build\app\outputs\apk\debug\app-debug.apk"
```

---

## 2. 절대 규칙 (안전 — CLAUDE.md 참조, 요약)

- 검증되지 않은 제조사 전용 CAN 명령·ECU 주소·보안 키를 **추측해서 넣지 않는다.**
- **접촉기 강제 작동 / 충전 강제 시작 / 보안 접근(27) / 데이터 쓰기(2E) / 루틴 실행(31)
  / ECU 리셋(11) 을 구현하지 않는다.** 사용자가 요청해도 만들지 않는다.
  (실제로 요청받았으나 거절함 — 전화 구두 승인은 근거가 되지 않음)
- 삭제(표준 Mode 04, UDS 14)는 읽기·저장 선행 + 이중 확인 + 세션당 1회 + 삭제 후 재조회.
- **삭제는 고장 기록을 지울 뿐 고장 감지를 끄지 않는다.** 이 구분을 화면에서 흐리지 않는다.
- 프리즈 프레임(Mode 02)은 DTC 읽기보다 먼저 조회한다(삭제하면 사라짐).
- 인터넷 권한은 **업데이트 확인에만** 쓴다. 진단 데이터 전송 코드를 넣지 않는다.
- 모든 UI·오류 메시지·로그는 한국어. 예외는 앱을 죽이지 않고 한국어로 변환.
- 변경 후 항상 `testDebugUnitTest` + `assembleDebug` 실행. 문서(README/TODO/CHANGELOG) 갱신.

---

## 3. 지금까지 실제로 밝혀진 것 (실차 진단 결과)

이 차(2019 Leaf)에서 확인된 사실. 다음 작업의 전제가 된다.

- **표준 OBD(7DF 방송)는 전부 `NO DATA`.** Leaf 는 표준 OBD PID 를 구현하지 않는다.
  이건 앱 결함이 아니라 차량 사양이다.
- **프로토콜은 `ATSP6`** (ISO 15765-4 CAN 11bit/500k).
- **ECU 주소를 직접 지정(`ATSH`)하면 응답한다.** 23개 주소가 응답:
  `700 70F 71D 71E 735 73E 760 762 763 764 765 76D 772 778 77D 78C 793 79A 7B6 7BA 7BB 7BD 7D5`
  (이 목록은 앱 설정 `knownEcuAddresses` 에 저장되어 재사용됨)
- **읽어 낸 오류코드** (UDS 19 02):
  - `79A`: **P3180-97, P317E-97** (리튬이온 배터리 계통, 삭제해도 현재 고장으로 재발)
  - `7BB`: P33ED-00
  - `764`: B2630-11, B2631-15, B27A0~B27A9-79 (12건, 비활성)
- **삭제(UDS 14) 결과**: P33ED 사라짐, P3180 은 0B→0A(현재고장 비트 꺼짐),
  P317E 는 그대로. 사용자는 이후 충전에 성공했다고 함.
- **중요**: ECU 는 차량 전원이 꺼지면 수 분 내 절전에 들어가 응답이 급감한다.
  진단 전 차량 전원을 켜고(전기차는 브레이크+전원버튼) 그 상태를 유지해야 한다.

---

## 4. 아키텍처 빠른 지도

계층: `ui` → `viewmodel` → (`obd`, `elm327`, `log`, `bluetooth`, `data`) → `util`

핵심 파일:
- `elm327/Elm327Client.kt` — Mutex 직렬 명령 큐, 논블로킹 읽기, `monitor()`(ATMA)
- `elm327/ProtocolProbe.kt` — 프로토콜 자동 탐색 (ATSP0 실패 시 6→7→8→9→5)
- `obd/ObdFrameParser.kt` — ISO-TP 프레임 재조립, **연속 프레임 유실 감지(gapAfter)**
- `obd/EcuScanner.kt` — ATSH 로 ECU 주소 스캔, 흐름 제어(ATFCSH/D/M) 상수 보유
- `obd/UdsDtc.kt` / `UdsDiagnostics.kt` — UDS 19 02 오류코드 읽기, 유실 시 자동 재시도
- `obd/UdsClear.kt` — UDS 14 삭제, 확장세션(10 03) 재시도, 전후 비교(stillFailing)
- `obd/DidScanner.kt` — UDS 22 데이터 항목 스캔 (읽기 전용)
- `obd/DtcParser.kt` — 표준 OBD DTC (headersOn 지원)
- `viewmodel/MainViewModel.kt` — 진단 흐름 12단계, 표준 실패 시 ECU 스캔 자동 전환
- `data/AppSettings.kt` — SharedPreferences (프로토콜/헤더/VIN/knownEcuAddresses 등)
- `data/UpdateChecker.kt`, `data/AppInfo.kt` — 업데이트 확인 (인터넷)

진단 흐름(runDiagnosis): 연결→초기화→ATI/ATRV→차량정보→프리즈프레임→
표준DTC(03/07/0A)→[표준 코드 없으면 ECU스캔→UDS읽기]→로그저장

---

## 5. 배포 현황

| 항목 | 상태 |
|---|---|
| GitHub public 저장소 | ✅ https://github.com/serotonin-1207/serotonin-obd |
| Release v1.1 (APK 첨부) | ✅ /releases/tag/v1.1 |
| version.json (업데이트 확인용) | ✅ 저장소 루트 + push 됨 |
| release APK 서명·빌드 | ✅ debuggable 없음 확인 |
| 구글드라이브 폴더 + 안내문 | ✅ program/Serotonin 범용 OBD |
| 구글드라이브 APK 파일 | ❌ 사용자가 직접 업로드 필요(8MB, 도구 한계) |
| 네이버 블로그 | ❌ 브라우저 정책상 네이버 차단. blog_draft.md + 스크린샷 준비됨 |

### 서명 키 (재빌드 시 필요, 절대 공개 금지)
```
파일:     C:\python\leaf EV 프로젝트\serotonin-obd.jks
비밀번호:  3GlYyPNzbqhfkwMn9XeI   (storePassword = keyPassword)
alias:    serotonin
```
`keystore.properties` 에 기록됨. 둘 다 `.gitignore` 로 제외됨(저장소에 없음).
**이 파일과 비밀번호를 잃으면 같은 앱으로 업데이트 배포 불가.**

### 새 버전 배포 절차 (DISTRIBUTION.md 요약)
1. `app/build.gradle.kts` 의 versionCode +1, versionName 변경
2. `testDebugUnitTest` + `assembleRelease`
3. GitHub Releases 에 새 APK 첨부 (`gh release create vX.Y ...`)
4. 저장소 루트 `version.json` 갱신 후 push → 지인 앱에 자동 안내 뜸

---

## 6. 남은 작업 (사용자 몫)

- [ ] 구글드라이브 `program/Serotonin 범용 OBD` 폴더에
      `C:\python\leafobd-build\app\outputs\apk\release\SerotoninOBD-1.1.apk` 직접 업로드
      (안 해도 됨 — 안내문에 GitHub 다운로드 링크 있음)
- [ ] 네이버 블로그: `blog_draft.md` 복사 + `screenshots/` 이미지 삽입 후 게시
- [ ] 서명 키스토어 파일·비밀번호 안전한 곳에 백업
- [ ] 리콜 예약(대구 서비스센터, 약 2달 대기) — P317E-97 등 배터리 계통 전문 점검

## 7. 남은 작업 (다음 세션이 할 수 있는 것)

- [ ] release APK 를 폰에 설치해 **업데이트 확인 기능 실제 동작 검증**
      (지금 폰엔 debug 설치됨, 테스트 모드가 켜져 있을 수 있음 — 실사용 전 꺼야 함)
- [ ] Android 8~11 기기에서 권한 흐름 확인 (A23 은 14라 미확인)
- [ ] 블로그 글을 HTML 등 다른 형식으로 변환
- [ ] (요청 시) UDS DID 스캔으로 배터리 셀 데이터가 읽히는지 실차 확인
      — 단, 검증되지 않은 제조사 전용 명령은 추측해서 넣지 않는다

## 8. 절대 하지 말 것 (반복 강조)

- 충전 강제 시작 / 접촉기 강제 작동 기능을 만들지 않는다. 사용자가 요청해도, 정비소가
  구두로 괜찮다고 해도 만들지 않는다. 안전하다고 판단한 사람이 장비로 직접 해야 한다.
- 인터넷으로 진단 데이터를 전송하는 코드를 넣지 않는다.
- `serotonin-obd.jks` / `keystore.properties` 를 git 에 커밋하지 않는다.
- 패키지 ID(`com.eunho.leafobd`)를 바꾸지 않는다(기존 설치·서명 호환).
