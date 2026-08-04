# Serotonin 범용 OBD

안드로이드 차량 진단 앱. **Vgate iCar Pro ELM327 BT3.0** 등 Bluetooth Classic 어댑터로
표준 OBD-II 오류코드를 읽고, 표준 OBD가 통하지 않는 차량(전기차 등)은 ECU 를 직접 찾아 읽습니다.

> 원래 2019년식 Nissan Leaf 진단용으로 시작했으나(구 이름 LeafOBD),
> 표준 OBD-II 차량과 UDS 응답 차량을 두루 다루게 되어 범용으로 확장되었습니다.
> 패키지 ID(`com.eunho.leafobd`)는 호환을 위해 유지합니다.
> 제작자: 이은호 (serotonin.1207@gmail.com)

> ⚠️ 이 앱은 개인 진단 보조 도구이며 정비소의 전문 진단을 대체하지 않습니다.
> 오류코드 삭제는 고장을 수리하지 않습니다.
> 고전압 배터리, 절연, 충전기, 브레이크, 에어백 관련 이상이 있으면 차량을 운행하거나 충전하지 말고 전문 점검을 받으십시오.
>
> 작업 전 반드시 [SAFETY.md](SAFETY.md) 를 읽으십시오.

---

## 1. 앱 목적

다음 증상의 원인을 찾기 위한 1차 자료(오류코드 + 원시 응답)를 확보합니다.

- 계기판에 `EV SYSTEM` 경고 표시
- 충전기를 연결해도 충전이 시작되지 않음
- 충전 접촉기 작동음이 들리지 않음
- 12V 배터리 음극 분리 리셋으로 해결되지 않음

## 2. 지원 범위

| 기능 | 상태 |
|---|---|
| Bluetooth Classic(SPP) 어댑터 연결 | ✅ |
| ELM327 초기화 (ATZ·ATE0·ATL0·ATS0·ATH0·ATSP0·ATI·ATRV) | ✅ |
| 어댑터 식별정보 / 차량 측 전압 확인 | ✅ |
| 연결 프로토콜 확인 (ATDP) | ✅ |
| Mode 01 `0101` 경고등 상태 · DTC 개수 | ✅ |
| Mode 01 지원 PID 조회 및 실시간 값 | ✅ |
| **Mode 02 프리즈 프레임** (삭제 전 자동 저장) | ✅ |
| Mode 03 저장 DTC 읽기 | ✅ |
| Mode 07 보류 DTC 읽기 | ✅ |
| Mode 0A 영구 DTC 읽기 | ✅ |
| Mode 09 VIN 읽기 (기본 꺼짐 · 마스킹 저장) | ✅ |
| CAN 헤더 표시로 응답 ECU 구분 (ATH1) | ✅ |
| 원시 응답 저장 (TXT + JSON) | ✅ |
| Mode 04 표준 DTC 삭제 (이중 확인 + 1회) | ✅ |
| 삭제 후 자동 재조회 및 전후 비교 | ✅ |
| 어댑터 없이 쓰는 테스트 모드 | ✅ |
| 차량 이름 변경 (다른 OBD-II 차량에도 사용) | ✅ |
| 완전 오프라인 동작 | ✅ |

### 프리즈 프레임이 왜 중요한가

프리즈 프레임은 **고장이 확정되던 순간의 데이터 스냅샷**입니다.
Mode 04(표준 삭제)를 실행하면 차량에서 **함께 지워집니다.**

그래서 이 앱은 DTC를 읽기 **전에** 프리즈 프레임을 먼저 읽어 로그에 저장합니다.
삭제 후에는 이 로그 파일이 유일한 사본이 됩니다.

### 다른 차량에도 쓸 수 있습니다

앱은 표준 OBD-II 명령만 보내므로 차종을 가리지 않습니다.
설정에서 차량 이름을 바꾸면 로그에도 그대로 반영됩니다.

실제로는 **일반 내연기관 차량에서 더 많은 정보가 나옵니다.**
표준 OBD-II 가 배출가스 진단 규격이라, 가솔린 차에서는 RPM·냉각수 온도·
연료 트림·프리즈 프레임이 표준으로 제공되지만 전기차에는 해당 항목이 아예 없습니다.

단, ABS · 에어백 · TPMS · 변속기 같은 제조사 전용 계통은
**어느 차량에서도** 표준 OBD-II 로 읽을 수 없습니다.

## 3. 지원하지 않는 범위

- **Nissan Leaf EV 전용 진단 (VCM · LBC · OBC/PDM)** — 검증된 공식 자료와 실제 통신 로그가 확보되기 전까지 구현하지 않습니다. ([배경](app/src/main/java/com/eunho/leafobd/nissan/README.md))
- 실시간 데이터(PID) 모니터링, 배터리 셀 전압, SOH 등 LeafSpy 기능
- ECU 코딩, 펌웨어 변경, 보안 접근 우회
- 고전압 시스템·접촉기·충전 강제 제어
- 에어백 · ABS · 브레이크 관련 명령
- 인터넷 기능, 계정, 광고, 분석 SDK (인터넷 권한 자체가 없습니다)

표준 OBD-II는 배출가스 관련 진단을 위한 규격입니다.
**Leaf의 EV 계통 오류가 표준 명령으로 읽히지 않을 수 있으며, 이는 앱의 결함이 아닙니다.**

## 4. 요구 장비

| 항목 | 사양 |
|---|---|
| 스마트폰 | Android 8.0(API 26) 이상, Bluetooth Classic 지원 |
| 검증 기기 | 삼성 갤럭시 A23 (SM-A235N, Android 14 / API 34) |
| 어댑터 | Vgate iCar Pro ELM327 V2.3 **BT3.0** (Bluetooth Classic / Android용) |
| 차량 | 2019년식 Nissan Leaf (표준 OBD-II 포트) |

> BLE(BT4.0) 전용 어댑터나 iOS 전용 모델은 이 앱과 호환되지 않습니다. 반드시 **BT3.0(Classic)** 모델이어야 합니다.

## 5. Android Studio에서 여는 방법

1. Android Studio 실행 → **Open**
2. `C:\python\leaf EV 프로젝트` 선택
3. Gradle Sync 완료까지 대기 (첫 실행 시 SDK Platform 37 자동 설치)
4. 좌측 상단 구성이 `app` 인지 확인

프로젝트 설정:

| 항목 | 값 |
|---|---|
| 패키지 | `com.eunho.leafobd` |
| minSdk | 26 |
| targetSdk | 36 |
| compileSdk | 37 |
| AGP | 9.3.1 |
| Gradle | 9.5.0 |
| Kotlin | 2.2.10 |
| UI | Jetpack Compose + Material 3 |

### 한글 경로에 대한 참고

프로젝트 경로에 한글과 공백이 있어(`leaf EV 프로젝트`) 두 가지 조치를 해 두었습니다.

1. `gradle.properties` 의 `android.overridePathCheck=true` — AGP의 비ASCII 경로 차단 해제
2. `build.gradle.kts` 가 빌드 출력 폴더를 자동으로 **`C:\python\leafobd-build`** 로 옮김
   (한글 경로에서는 Gradle 테스트 워커가 클래스패스를 해석하지 못해 단위 테스트가 전부 실패합니다)

경로를 ASCII 폴더로 옮기면 두 조치 모두 자동으로 비활성화되고 기본 `app/build/` 를 사용합니다.

## 6. 빌드 방법

명령줄에서 빌드할 때는 JDK를 먼저 지정합니다.

PowerShell:

```bash
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
```

디버그 APK 빌드:

```bash
.\gradlew.bat assembleDebug
```

단위 테스트:

```bash
.\gradlew.bat testDebugUnitTest
```

기기에 바로 설치:

```bash
.\gradlew.bat installDebug
```

## 7. APK 위치

```text
개발용  C:\python\leafobd-build\app\outputs\apk\debug\app-debug.apk
배포용  C:\python\leafobd-build\app\outputs\apk\release\app-release.apk
```

> 한글 경로 대응으로 빌드 출력이 `C:\python\leafobd-build` 로 이동되어 있습니다.
> 일반적인 `app\build\outputs\...` 경로가 **아닙니다.**

### 남에게 줄 때는 반드시 release 빌드로

debug APK 는 `android:debuggable=true` 라서 USB 디버깅이 켜진 폰에서는
`adb run-as` 로 앱 내부의 진단 로그를 꺼낼 수 있습니다. 배포에 쓰지 마십시오.

**1단계 — 서명 키 만들기 (최초 1회)**

프로젝트 폴더에서 실행합니다. 비밀번호는 직접 정하십시오.

```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore leafobd-release.jks -alias leafobd -keyalg RSA -keysize 2048 -validity 10000
```

**2단계 — `keystore.properties` 작성**

`keystore.properties.example` 를 복사해 `keystore.properties` 로 저장하고 값을 채웁니다.
이 파일과 `.jks` 는 `.gitignore` 에 등록되어 있어 저장소에 올라가지 않습니다.

**3단계 — 빌드**

```bash
.\gradlew.bat assembleRelease
```

> ⚠️ **키스토어 파일과 비밀번호를 잃어버리면 같은 앱으로 업데이트할 수 없습니다.**
> 지인들이 기존 앱을 지우고 새로 설치해야 합니다. 반드시 따로 백업하십시오.

배포·설치 안내는 [INSTALL.md](INSTALL.md) 를 그대로 전달하시면 됩니다.

## 8. 실제 폰에 설치하는 방법 (갤럭시 A23 / SM-A235N)

1. 폰에서 **설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호** 를 7번 탭 → 개발자 옵션 활성화
2. **설정 → 개발자 옵션 → USB 디버깅** 켜기
3. USB 케이블로 PC 연결 → 폰에 뜨는 "USB 디버깅 허용" 대화상자에서 허용
4. 연결 확인:

```bash
adb devices
```

5. 설치:

```bash
adb install -r "C:\python\leafobd-build\app\outputs\apk\debug\app-debug.apk"
```

Android Studio에서는 기기를 선택하고 **Run ▶** 을 눌러도 됩니다.

## 9. Bluetooth 페어링 방법

앱은 **이미 페어링된 장치만** 표시합니다. 페어링은 시스템 설정에서 먼저 해야 합니다.

1. 차량을 안전한 상태(P, 주차 브레이크, 충전 케이블 분리)에 둡니다.
2. 어댑터를 운전석 하단 OBD2 포트에 꽂습니다. (어댑터 LED 점등 확인)
3. 폰에서 **설정 → 연결 → Bluetooth**
4. `V-LINK`, `OBDII`, `Vgate`, `iCar Pro` 등으로 표시되는 장치를 선택합니다.
5. PIN을 요구하면 **판매자 설명을 먼저 확인**하십시오. (앱은 PIN을 단정하지 않습니다)
6. 페어링이 끝나면 Serotonin OBD 앱 → **어댑터 연결** 화면에서 목록을 새로 고칩니다.

앱 안에서 무리하게 페어링을 구현하지 않고 시스템 Bluetooth 설정으로 보냅니다.

## 10. 진단 절차

```text
어댑터 연결
 → ELM327 초기화
 → 어댑터 정보(ATI) · 전압(ATRV) 확인
 → 차량 정보 (0101 경고등 · 지원 PID · VIN)
 → 프리즈 프레임 저장 (Mode 02)   ← 삭제하면 사라지므로 먼저 읽는다
 → Mode 03 / 07 / 0A 읽기
 → 원시 응답 저장 (TXT + JSON)
 → 코드 검토
 → (필요 시) 사용자 이중 확인
 → 표준 오류 삭제 1회
 → 3초 후 자동 재조회
 → 재발 코드 확인
 → 필요 시 전문 점검
```

자세한 순서는 앱 안의 **사용 절차** 화면과 [TESTING.md](TESTING.md) 를 참고하십시오.

### 어댑터가 없을 때 — 테스트 모드

**설정 → 테스트 모드** 를 켜면 실제 어댑터 없이 전체 흐름을 확인할 수 있습니다.
시나리오 5가지(표준 응답 / 삭제 후 재발 / 데이터 없음 / 통신 오류 / 무응답)를 제공하며,
이 모드에서는 모든 화면에 **"모의 데이터"** 배지가 표시됩니다.

## 11. 로그 파일

저장 위치는 앱 내부 저장소이며, 진단 기록 화면에서 공유·복사·삭제할 수 있습니다.

```text
LeafOBD_2026-07-29_223500.txt    사람이 읽는 형식
LeafOBD_2026-07-29_223500.json   구조화된 형식
```

- Bluetooth MAC 주소는 마지막 2바이트만 남기고 마스킹합니다. (`**:**:**:**:98:8B`)
- 차대번호(VIN)는 기본적으로 읽지 않으며, 켜더라도 파일에는 마스킹해 저장합니다. (`SJN**********2345`)
- 위치정보를 수집하지 않습니다.
- 인터넷으로 전송하지 않습니다. 공유는 사용자가 직접 눌렀을 때만 동작합니다.

## 12. 안전 주의사항

- 진단 중 충전 케이블을 분리하십시오.
- 고전압 케이블(주황색)과 배터리 팩을 절대 만지지 마십시오.
- 삭제 기능은 로그 저장 후에만 활성화되며, 한 세션에 **1회만** 실행됩니다.
- 코드가 삭제 직후 다시 나타나면 **활성 고장**일 수 있습니다. 반복 삭제하지 말고 점검을 받으십시오.
- 타는 냄새, 연기, 비정상 발열, 절연 오류, 브레이크·에어백 경고 시 **즉시 중단**하십시오.

전체 내용은 [SAFETY.md](SAFETY.md) 에 있습니다.

## 13. 문서

| 파일 | 내용 |
|---|---|
| [SAFETY.md](SAFETY.md) | 안전 지침, 삭제 제한, 법적 고지 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 계층 구조, 데이터 흐름, 명령 큐, 예외 처리 |
| [TESTING.md](TESTING.md) | 단위 테스트, 테스트 모드, 실기기·실차량 체크리스트 |
| [TODO.md](TODO.md) | 단계별 진행 상황 |
| [CHANGELOG.md](CHANGELOG.md) | 변경 이력 |
| [CLAUDE.md](CLAUDE.md) | 이 저장소에서 작업할 때의 규칙 |
| [nissan/README.md](app/src/main/java/com/eunho/leafobd/nissan/README.md) | Leaf 전용 기능을 추가할 수 있는 조건 |

## 14. 면책

이 소프트웨어는 있는 그대로 제공되며, 사용으로 인해 발생하는 차량 손상, 데이터 손실,
인적 피해에 대해 제작자는 책임지지 않습니다. 최종 판단과 책임은 사용자에게 있습니다.
