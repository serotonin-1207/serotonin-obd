# screenshots/

실제 기기(갤럭시 A23, SM-A235N / Android 14)에서 찍은 화면입니다.

## 현재 포함된 화면 (2026-07-30, 테스트 모드 "삭제 후 재발")

| 파일 | 내용 |
|---|---|
| `01_home.png` | 홈 — 상태 카드와 안전 경고 |
| `02_settings_vehicle.png` | 설정 — 차량 이름 편집, 조회 항목 |
| `03_diagnosis_vehicle_info.png` | 진단 — 10단계 완료, 차량 정보(VIN·경고등·지원 PID) |
| `04_freeze_frame_and_dtc.png` | 프리즈 프레임(원인 DTC P0AA6)과 저장 코드 4건 |
| `05_clear_locked.png` | 삭제 화면 — 조건 4충족 2미충족으로 버튼 잠김 |
| `06_settings_options.png` | 설정 — 프리즈 프레임/실시간 값/CAN 헤더/VIN 마스킹 |

> 표시된 코드와 VIN은 모두 **모의 데이터**입니다. 실제 차량 값이 아닙니다.

## 추가로 찍으면 좋은 화면

권장 파일명:

```
01_home.png          홈 화면 (상태 + 안전 경고)
02_devices.png       어댑터 선택 (주소 마스킹 확인)
03_permission.png    권한 요청 대화상자
04_diagnosis.png     진단 진행 단계
05_dtc.png           읽은 오류코드 목록
06_raw.png           원시 응답 펼친 상태
07_clear_locked.png  삭제 조건 미충족 (버튼 비활성)
08_clear_result.png  삭제 전후 비교
09_logs.png          진단 기록 목록
10_settings.png      설정 (테스트 모드)
```

캡처 방법: 전원 버튼 + 볼륨 하 동시 누름, 또는

```bash
adb exec-out screencap -p > 01_home.png
```

주의: 캡처에 Bluetooth MAC 주소 전체나 개인 식별 정보가 보이지 않는지 확인한 뒤 공유하십시오.
