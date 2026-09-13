# 니로·리프 배터리 읽기 시험

대상: 사용자 확인 2019 LEAF ZE1 40kWh급, 2019 니로 EV DE 64kWh급. 배터리 교체/용량 변경 없음.
어댑터: Vgate iCar Pro BT3.0, Android Bluetooth Classic. 등록증 개인 식별정보는 프로파일에 저장하지 않는다.

## 출처와 상태

OVMS MIT 라이선스 소스, 검토 리비전 `85074a0ae7a983b308c6e2e081185492527ee073`.
- [리프](https://github.com/openvehicles/Open-Vehicle-Monitoring-System-3/blob/85074a0ae7a983b308c6e2e081185492527ee073/vehicle/OVMS.V3/components/vehicle_nissanleaf/src/vehicle_nissanleaf.cpp)
- [니로 요청 목록](https://github.com/openvehicles/Open-Vehicle-Monitoring-System-3/blob/85074a0ae7a983b308c6e2e081185492527ee073/vehicle/OVMS.V3/components/vehicle_kianiroev/src/vehicle_kianiroev.cpp)
- [니로 해석](https://github.com/openvehicles/Open-Vehicle-Monitoring-System-3/blob/85074a0ae7a983b308c6e2e081185492527ee073/vehicle/OVMS.V3/components/vehicle_kianiroev/src/kn_can_poll.cpp)

법적 고지: THIRD_PARTY_NOTICES.md 및 APK assets/OVMS_LICENSE.txt.
공개 구현에 근거한 후보이며 사용자 차량·어댑터에서 아직 실차 검증하지 않았다. OVMS의 CAN 연결 환경이 OBD 어댑터와 같다는 보장은 없다.
JejuSoul 자료는 참고 조사만 했고 이 구현에 복제하지 않았다.

## 요청과 해석

모든 오프셋은 응답 서비스+PID를 제거한 데이터의 0 기준 인덱스. big endian.

| 차량 | 송신/수신 | 요청 | 해석 |
|---|---|---|---|
| 리프 | 79B/7BB | 2101 | 정확히 51 bytes: SOC d31..33 /10000 |
| 리프 | 79B/7BB | 2102 | 정확히 196 bytes: 96채널 d0..191, 16bit mV |
| 리프 | 79B/7BB | 2104 | 정확히 29 bytes: 4개 thermistor 후보 d0/3/6/9의 16bit; FFFF 제외, −0.102×(raw−710)℃ |
| 리프 | 79B/7BB | 2161 | 정확히 329 bytes: d2..3 /100 SOH |
| 니로 | 7E4/7EC | 220101 | 최소 20 bytes: BMS SOC d4/2, 온도 d16..19 |
| 니로 | 7E4/7EC | 220102~04 | 최소 36 bytes: 각 d4..35 ×0.02V, 32채널씩 |
| 니로 | 7E4/7EC | 220105 | 최소 36 bytes: d25..26 /10 SOH, d34..35 ×0.02V 채널 97/98 |

니로 OVMS의 첫 데이터 조각은 3 bytes, CF1은 데이터 d3부터 시작한다. 프레임별 인덱스를 연속 오프셋으로 변환했다.
니로 온도 원본은 unsigned: 음수 온도의 인코딩은 미확인으로 범위 밖 값은 표시하지 않는다.
리프 온도는 thermistor 환산값이며 실차 기준 진단기와 대조가 필요하다.
SOC는 BMS 보고 잔량으로 계기판 표시와 다를 수 있다. SOH는 BMS 추정값이며 실측 용량 인증이 아니다.
전체 채널이 유효할 때만 전압 편차 계산. 여러 페이지는 순차 수집하므로 동시 측정이 아니다.

## 실행 제한과 기록

기본 꺼짐. 사용자가 차종/정차 상태를 확인하고 켠 뒤 직접 한 번 조회한다. 자동 탐지·반복·재시도 없음.
실제 연결이 없거나 가상 모드면 실행하지 않는다. 요청 목록은 enum 테이블에 고정한다.
설정 명령 하나라도 거절되면 차량 조회를 중단한다. ISO-TP 길이/순서/ECU/서비스/PID 불일치는 거절한다.
ATZ로 어댑터 초기화 후 11bit 500kbps와 명시적 흐름 제어를 사용한다. 종료 시 필터/주소 복원 후 연결을 닫고 초기화 상태를 무효화한다.
모든 어댑터 접근은 기존 작업 Mutex와 Elm327Client Mutex를 통한다.
이 기능에는 코드 삭제/보안 접근/세션 변경/제어/배터리 강제 작동 요청이 없다.
원시 응답은 별도 로컬 세션에 기록하며 VIN은 조회하지 않는다. 정비사 상세에서도 원시 응답 확인 가능.
화면 값은 ViewModel 메모리에 보관하며 프로세스 종료 후에는 남지 않는다. 저장 로그에서 그래프 복원은 후속 작업이다.

## 실차 완료 조건

차종별 연결/전체 응답 성공, 기준 진단기와 SOC/SOH/전압/온도 대조, 실패/프레임 누락 구분, 재연결 후 기존 진단 회귀 확인.
이 조건을 통과하기 전에는 지원 완료 또는 배터리 정상으로 표시하지 않는다.

검증 결과: testDebugUnitTest 193건 실패/오류 0, debug/release 빌드 및 lintVitalRelease 통과. Galaxy A23 업데이트 설치와 기본 꺼짐/차량 선택/폰트 150%/화면 재생성 후 가상 값 유지 확인. 실차 명령 미실행. 상세 ../qa/ev-battery-20260911/QA.md.

## 2026-09-11 실차 검증 전 사용성 보강

- 조회 항목/진행률/실패 후 행동을 표시하고 현재 명령 종료 후 중단하는 버튼 추가. 중단 전 응답과 설정 복원 로그 보존.
- 배터리 직접 조회 결과 자동 저장. CSV는 수동 저장. 앱 종료 후 저장 기록에서 그래프/원시 응답 다시 열기.
- Android noBackupFilesDir에 원자적 교체로 저장하여 클라우드 자동 백업에서 제외. 새 배터리 기록의 외부 공유 경로 없음.
- 리프/니로 기록과 선택 설정 분리. 외부 CSV는 별도 구분이며 이름만 보고 자동으로 차량에 연결하지 않음. 가상 예시 저장 금지.
- 최저/최고 채널, 온도 차이, 전압 차이 확대 눈금 추가. 미수신 채널은 정상값으로 보정하지 않음.
- 이전 측정값/미래 시각 안내 및 같은 프로파일의 이전 직접 측정과 SOC/SOH 수치 비교. 차이는 열화·수리 효과 판정이 아님.
- 손상된 저장 파일은 다른 기록과 분리하여 오류 건수 표시. 저장 파일의 형식 버전/크기/채널/숫자/차량 일치 검사.
- 기존 원시 진단 로그의 공유·백업 설정은 이번 새 배터리 기록 저장소와 별개이며, 공유 개인정보 보호 전반은 후속 검토 대상.