# ARCHITECTURE.md — LeafOBD 구조

## 1. 계층 구조

```
┌─────────────────────────────────────────────┐
│ ui/            Jetpack Compose (Material 3)  │  화면은 상태를 그리기만 한다
│  screen/, component/, navigation/, theme/    │  BluetoothSocket을 직접 다루지 않는다
└───────────────────┬─────────────────────────┘
                    │ StateFlow<MainUiState>  /  이벤트 호출
┌───────────────────▼─────────────────────────┐
│ viewmodel/MainViewModel                      │  화면 상태 보관, 중복 실행 방지(Mutex)
└───────┬───────────────┬──────────────┬──────┘
        │               │              │
┌───────▼──────┐ ┌──────▼───────┐ ┌───▼──────────────┐
│ obd/         │ │ elm327/      │ │ log/             │
│ ObdService   │ │ Elm327Client │ │ LogRepository    │
│ DtcParser    │ │ Initializer  │ │ LogExporter      │
│ VoltageParser│ │ Command      │ │ Session/Command  │
└───────┬──────┘ └──────┬───────┘ └──────────────────┘
        │               │
        │      ┌────────▼─────────┐
        │      │ Elm327Transport  │  ← 인터페이스
        │      └───┬──────────┬───┘
        │          │          │
        │  ┌───────▼──────┐ ┌─▼──────────────────┐
        │  │ Bluetooth    │ │ FakeElm327Transport│
        │  │ Transport    │ │ (테스트 모드)       │
        │  └───────┬──────┘ └────────────────────┘
        │          │
┌───────▼──────────▼──────────────────────────┐
│ bluetooth/  BluetoothClassicManager          │
│             Repository / State / SppConstants│
└──────────────────────────────────────────────┘

nissan/  ← 1차 버전에서는 인터페이스와 문서만 (기능 플래그로 비활성)
util/    ← 순수 함수 (ResponseText, MacMasking, JsonWriter)
```

## 2. 데이터 흐름

### 진단 실행

```
사용자 "진단 시작"
  → MainViewModel.runDiagnosis()
      → BluetoothClassicManager.connect(device)        [Dispatchers.IO]
      → BluetoothElm327Transport(socket)
      → Elm327Client.open()
      → Elm327Initializer.initialize()                 ATZ … ATRV, ATDP
      → ObdService.readMonitorStatus()                 0101 경고등·DTC 개수
      → ObdService.readSupportedPids()                 0100 → 0120 → 0140 …
      → ObdService.readLiveValues(supported)           지원 PID만 조회
      → ObdService.readVin()                           0902 (설정에서 켰을 때만)
      → ObdService.readFreezeFrame()                   Mode 02  ★ 삭제 전 필수
      → ObdService.readAllDtcs()                       Mode 03 → 07 → 0A
      → DiagnosticLogRepository.save(session)          TXT + JSON
  → MainUiState 갱신 → Compose 재구성
```

**프리즈 프레임을 DTC 읽기보다 먼저 실행하는 이유**
Mode 04(삭제)를 실행하면 프리즈 프레임도 차량에서 함께 지워진다.
삭제 후에는 이 로그 파일이 유일한 사본이므로, 어떤 경우에도 먼저 확보한다.
순서를 바꾸는 변경은 안전 요구사항 위반이다.

각 단계의 원시 응답은 `CommandLog` 로 그대로 보관되어 세션 파일에 기록된다.
앱이 해석한 결과가 아니라 어댑터가 실제로 보낸 바이트가 진단의 근거이기 때문이다.

### 삭제 실행

```
사전조건 6가지 모두 충족 (ClearEligibility)
  → ObdService.clearDtcs()      Mode 04, 1회만
  → 응답 저장
  → 3초 대기
  → ObdService.readAllDtcs()    재조회
  → DiagnosticSession 의 clearedCodes / remainingCodes / newCodes 계산
  → 재발 코드 강조 표시
```

## 3. 명령 큐

- `Elm327Client` 는 `Mutex` 하나로 모든 명령을 직렬화한다.
- 두 명령이 동시에 나가면 응답이 섞여 해석이 불가능하므로 **동시 전송을 구조적으로 막는다.**
- 명령은 항상 `\r` 로 끝낸다.
- 응답은 프롬프트 `>` 가 나올 때까지 읽는다.
- 전송 직전 잔여 버퍼를 비운다(`drain`). 이전 응답 찌꺼기와 섞이는 것을 막는다.
- `Elm327Transport.read()` 는 **논블로킹**이다. 데이터가 없으면 0을 돌려주고,
  대기는 취소 가능한 `delay()` 로 처리한다. 그래서 타임아웃이 정확히 동작하고
  화면을 벗어나면 코루틴이 즉시 취소된다.

## 3-1. CAN 헤더 표시와 ISO-TP 재조립

설정에서 **CAN 헤더 표시**를 켜면 초기화가 `ATH0` 대신 `ATH1` 을 보낸다.

| 헤더 | ELM327 표시 | 앱이 하는 일 |
|---|---|---|
| 꺼짐 `ATH0` | 다중 프레임을 어댑터가 합쳐 `0:` `1:` 형식으로 표시 | 줄머리만 떼고 이어 붙임 |
| 켜짐 `ATH1` | 원본 CAN 프레임이 그대로 노출 | `ObdFrameParser` 가 PCI를 해석해 직접 재조립 |

`ObdFrameParser` 가 처리하는 ISO 15765-2 흐름 제어 바이트:

- `0N` 단일 프레임 — 하위 4비트가 데이터 길이
- `1N NN` 최초 프레임 — 하위 4비트 + 다음 바이트가 전체 길이
- `2N` 연속 프레임 — 하위 4비트는 순번

CAN ID 길이는 줄 전체의 홀짝으로 판별한다.
데이터는 항상 짝수 자리이므로 11비트 헤더(3자리)면 전체가 홀수, 29비트(8자리)면 짝수가 된다.

결과는 ECU별 `EcuResponse` 목록이 되고, 각 `DtcCode` 에 보고 ECU가 기록된다.

## 4. Bluetooth 수명주기

```
Idle
 ├─ 권한 없음        → PermissionRequired
 ├─ 어댑터 꺼짐      → BluetoothOff
 └─ connect()        → Connecting ──┬─→ Connected(deviceName)
                                    └─→ Error(한국어 메시지)
Connected ─ disconnect() / 소켓 오류 ─→ Disconnected
```

- 권한을 확인하기 **전에는** `bondedDevices`, `name`, `address`, 소켓 생성 API를 호출하지 않는다.
  (호출하면 Android 12+ 에서 `SecurityException` 이 발생한다)
- 연결 전 `cancelDiscovery()` 를 호출한다. 검색 중에는 RFCOMM 연결이 매우 느려진다.
- 연결 타임아웃은 15초.
- `ViewModel.onCleared()` 와 화면 이탈 시 소켓과 스트림을 모두 닫는다.
- 페어링은 앱에서 구현하지 않고 시스템 Bluetooth 설정으로 보낸다.

## 5. 예외 처리 원칙

| 계층 | 원칙 |
|---|---|
| Transport | `IOException`/`SecurityException` 를 그대로 던진다 |
| `Elm327Client` | 모든 예외를 잡아 **실패한 `CommandLog`** 로 변환한다. 밖으로 던지지 않는다 |
| `ObdService` | 실패 로그를 그대로 전달하고 파서가 상태를 판정한다 |
| `MainViewModel` | 남은 예외를 잡아 `userMessage`(한국어)로 바꾼다 |
| UI | 상태만 그린다. 예외를 알지 못한다 |

결과적으로 **어떤 통신 실패도 앱을 종료시키지 않는다.**

## 6. 테스트 구조

| 대상 | 방법 | 위치 |
|---|---|---|
| `DtcParser` | 순수 JVM 단위 테스트 | `app/src/test/.../obd/DtcParserTest.kt` |
| `VoltageParser` | 순수 JVM 단위 테스트 | `app/src/test/.../obd/VoltageParserTest.kt` |
| `ResponseText` | 순수 JVM 단위 테스트 | `app/src/test/.../util/ResponseTextTest.kt` |
| 통신 계층 | `FakeElm327Transport` 통합 테스트 | `app/src/test/.../elm327/Elm327ClientTest.kt` |
| 삭제 조건 | 순수 로직 테스트 | `app/src/test/.../viewmodel/ClearEligibilityTest.kt` |
| 로그 형식 | 순수 로직 테스트 | `app/src/test/.../log/LogFormatTest.kt` |

`FakeElm327Transport` 는 앱 본체(`src/main`)에 있다.
설정 화면의 **테스트 모드** 토글로 실제 기기에서도 어댑터 없이 전체 흐름을 확인하기 위해서다.
이 모드에서는 화면에 "모의 데이터" 배지가 항상 표시된다.

## 7. 의존 규칙

- `ui` → `viewmodel` → (`obd`, `elm327`, `log`, `bluetooth`) → `util`
- 역방향 의존은 없다.
- `obd`, `elm327`, `util` 은 Android API에 의존하지 않으므로 JVM 테스트가 가능하다.
  (`log` 의 파일 저장부와 `bluetooth` 만 Android API를 사용한다)
- 네트워크 라이브러리, 분석 SDK, 광고 SDK를 사용하지 않는다.
  `AndroidManifest.xml` 에 `INTERNET` 권한 자체가 없다.
