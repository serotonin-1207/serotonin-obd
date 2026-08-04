package com.eunho.leafobd.viewmodel

/**
 * 표준 DTC 삭제(Mode 04) 사전조건.
 *
 * 지시서 2.2의 6가지 조건을 그대로 옮긴 것이다.
 * 하나라도 충족되지 않으면 삭제 버튼이 활성화되지 않는다.
 *
 * Android API에 의존하지 않는 순수 로직이므로 단위 테스트로 검증한다.
 */
data class ClearEligibility(
    /** 어댑터 Bluetooth 연결 완료 */
    val connected: Boolean = false,
    /** ELM327 초기화 완료 */
    val initialized: Boolean = false,
    /** DTC 읽기 최소 1회 완료 */
    val dtcRead: Boolean = false,
    /** 읽은 원시 응답이 파일로 저장됨 */
    val logSaved: Boolean = false,
    /** 안전 경고 체크박스 3개 모두 선택 */
    val safetyChecked: Boolean = false,
    /** 확인 문구를 정확히 입력 */
    val confirmationTyped: Boolean = false,
    /** 이번 세션에서 이미 삭제를 실행했는지 (반복 삭제 금지) */
    val alreadyCleared: Boolean = false
) {

    val eligible: Boolean
        get() = connected && initialized && dtcRead && logSaved &&
            safetyChecked && confirmationTyped && !alreadyCleared

    /** 아직 충족되지 않은 조건을 한국어로 설명한다. UI에 그대로 표시한다. */
    val unmetReasons: List<String>
        get() = buildList {
            if (!connected) add("어댑터가 연결되어 있지 않습니다.")
            if (!initialized) add("ELM327 초기화가 완료되지 않았습니다.")
            if (!dtcRead) add("오류코드를 아직 한 번도 읽지 않았습니다.")
            if (!logSaved) add("읽은 원시 응답이 파일로 저장되지 않았습니다.")
            if (!safetyChecked) add("안전 확인 항목 3개를 모두 선택해야 합니다.")
            if (!confirmationTyped) add("확인 문구를 정확히 입력해야 합니다.")
            if (alreadyCleared) add("이번 진단 세션에서 이미 삭제를 실행했습니다. 반복 삭제는 허용하지 않습니다.")
        }
}

/** 삭제 실행 전 사용자가 직접 입력해야 하는 확인 문구. */
object ClearConfirmation {

    const val PHRASE: String = "오류코드를 저장했습니다"

    /** 앞뒤 공백만 무시하고 정확히 일치해야 한다. */
    fun matches(input: String): Boolean = input.trim() == PHRASE

    /** 삭제 화면 최상단에 표시할 경고 문구. */
    const val WARNING: String =
        "오류코드 삭제는 고장을 수리하지 않습니다.\n" +
            "삭제하면 고장 발생 당시의 진단 정보(프리즈 프레임 등)가 사라져 원인 추적이 어려워질 수 있습니다.\n" +
            "고전압 배터리, 절연, 충전기, 브레이크, 에어백 관련 이상이 있으면 차량을 운행하거나 충전하지 말고 전문 점검을 받으십시오."

    /** 반드시 선택해야 하는 체크박스 문구 3개. */
    val CHECKLIST: List<String> = listOf(
        "삭제 전 오류코드와 원시 응답을 저장했습니다.",
        "오류 삭제가 실제 고장을 수리하지 않는다는 점을 이해했습니다.",
        "고전압 관련 오류가 다시 나타나면 운행·충전을 중단하겠습니다."
    )

    /** Mode 04의 범위 한계를 알리는 문구. */
    const val SCOPE_NOTICE: String =
        "표준 OBD 삭제 명령은 일부 오류만 삭제할 수 있습니다.\n" +
            "Nissan Leaf의 EV 전용 오류는 남아 있을 수 있습니다."
}
