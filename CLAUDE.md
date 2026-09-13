# CLAUDE.md — 이 저장소에서 작업할 때의 규칙

LeafOBD는 실제 차량(2019 Nissan Leaf)에 명령을 보내는 앱입니다.
잘못된 코드가 사람과 차량을 위험에 빠뜨릴 수 있으므로 아래 규칙을 반드시 지킵니다.

## 절대 규칙 (안전)

1. **검증되지 않은 Nissan 전용 CAN 명령, ECU 주소, 보안 접근 키, 진단 세션 명령을 추측해서 추가하지 않는다.**
   추가 조건은 `app/src/main/java/com/eunho/leafobd/nissan/README.md` 참조.
2. 고전압 시스템 강제 활성화, 접촉기 강제 작동, 충전 강제 시작, ECU 코딩,
   펌웨어 변경, 보안 접근 우회, 에어백·ABS·브레이크 관련 명령을 구현하지 않는다.
3. 자동 삭제, 앱 실행과 동시 삭제, 반복 삭제, 무한 재시도를 만들지 않는다.
4. 삭제(Mode 04)는 `ClearEligibility` 6가지 조건을 모두 통과해야만 실행되며,
   세션당 1회로 제한한다. 이 검증을 우회하는 경로를 만들지 않는다.
4-1. **프리즈 프레임(Mode 02)은 DTC 읽기보다 먼저 조회한다.**
   Mode 04 로 삭제하면 차량에서 함께 사라지므로, 로그 파일이 유일한 사본이 된다.
   진단 순서를 바꿀 때 이 순서를 깨뜨리지 않는다.
4-2. **Mode 08(온보드 시스템 제어)을 구현하지 않는다.** 읽기가 아니라 장치를 작동시키는 명령이다.
4-3. ECU 오류코드 삭제(UDS `14`)도 같은 절차를 따른다. `UdsClearEligibility` 6조건,
   확인 문구, 세션당 1회, 삭제 후 자동 재조회와 전후 비교.
   삭제는 **고장 기록을 지울 뿐 고장 감지를 끄지 않는다.** 이 구분을 화면 문구에서 흐리지 않는다.
   접촉기 강제 작동·충전 강제 시작·보안 접근(`27`)·데이터 쓰기(`2E`)·루틴 실행(`31`)은
   여전히 구현하지 않는다.
5. 어떤 오류코드도 "안전하다", "무시해도 된다"고 안내하지 않는다.
6. 안전 경고 문구를 삭제하거나 약화시키지 않는다.

## 개인정보 규칙

7. 광고·분석 SDK·계정 기능을 넣지 않는다.
   인터넷 권한은 **GitHub `version.json`의 앱 버전 확인과 사용자가 직접 누른 서명된 공개 오류코드 데이터 팩 갱신**에만 쓴다.
   진단 데이터(오류코드·원시 응답·로그·VIN)를 외부로 전송하는 코드를 넣지 않는다.
   데이터 팩은 고정 주소·크기·SHA-256·ECDSA 서명·스키마·출처 원장을 검증하며 실패하면 기존 팩을 유지한다.
   업데이트 확인은 설정에서 끌 수 있어야 하며, 끄면 인터넷을 전혀 쓰지 않는다.
   (2026-08-04 제작자 승인 하에 규칙 개정. 원래는 인터넷 권한 자체를 금지했다.)
8. 위치 권한을 요청하지 않는다. `BLUETOOTH_SCAN` 은 `neverForLocation` 을 유지한다.
9. Bluetooth MAC 주소 전체를 파일이나 화면에 남기지 않는다. (`MacMasking.mask` 사용)
9-1. VIN 도 식별정보다. 기본값은 읽지 않음이며, 파일에 저장할 때는 `VinMasking.mask` 를 거친다.
10. 로그는 사용자가 직접 공유 버튼을 눌렀을 때만 앱 밖으로 나간다.

## 코드 규칙

11. 사용자 화면, 버튼, 설명, 오류 메시지, 로그 라벨은 **모두 한국어**로 작성한다.
12. 메인 스레드에서 소켓 연결이나 입출력을 실행하지 않는다. (`Dispatchers.IO`)
13. UI는 `BluetoothSocket` 을 직접 다루지 않는다. `ViewModel → Service → Transport` 순서를 지킨다.
14. 어댑터 명령은 `Elm327Client` 의 `Mutex` 를 통해 **한 번에 하나씩** 보낸다.
15. `Elm327Transport.read()` 는 논블로킹이어야 한다. 블로킹 읽기를 넣으면
    코루틴 타임아웃이 동작하지 않고 앱이 멈춘다.
16. 모든 예외는 앱이 종료되지 않도록 처리하고 한국어 메시지로 바꿔 보여준다.
17. 중요한 판단 근거와 안전 제한은 주석으로 남긴다.
18. 외부 라이브러리를 최소화한다. Bluetooth는 Android 공식 API만 사용한다.
19. 원시 응답(`CommandLog.rawResponse`)은 가공하지 않고 그대로 보관한다.
    진단의 근거는 앱의 해석이 아니라 어댑터가 실제로 보낸 바이트다.

## 작업 절차

20. 변경 후 반드시 실행한다.

```bash
.\gradlew.bat testDebugUnitTest
```

```bash
.\gradlew.bat assembleDebug
```

21. 파서·삭제 조건·로그 형식을 바꾸면 해당 단위 테스트를 함께 갱신한다.
22. `README.md`, `TODO.md`, `CHANGELOG.md` 를 갱신한다.

## 환경 메모

- JDK: Android Studio 내장 JBR 21 → `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`
- 프로젝트 경로에 한글·공백이 있어 두 가지 대응이 되어 있다.
  - `gradle.properties` 의 `android.overridePathCheck=true`
  - 루트 `build.gradle.kts` 가 빌드 출력 폴더를 `C:\python\leafobd-build` 로 이전
    (제거하면 단위 테스트가 전부 `ClassNotFoundException` 으로 실패한다)
- APK 경로: `C:\python\leafobd-build\app\outputs\apk\debug\app-debug.apk`
- 검증 기기: 삼성 갤럭시 A23 (SM-A235N, Android 14 / API 34)
  - 설치: `adb -s <serial> install -r "C:\python\leafobd-build\app\outputs\apk\debug\app-debug.apk"`
  - 다른 기기가 함께 붙어 있을 수 있으므로 `-s` 로 기기를 반드시 지정한다
