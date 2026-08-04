package com.eunho.leafobd.viewmodel

/**
 * ECU 오류코드 삭제(UDS 14) 사전조건.
 *
 * 표준 OBD 삭제([ClearEligibility])와 같은 원칙을 따른다.
 * 삭제하기 전에 반드시 읽고 저장해야 하며, 세션당 1회만 실행된다.
 */
data class UdsClearEligibility(
    /** 어댑터 연결 완료 */
    val connected: Boolean = false,
    /** ECU 응답 스캔으로 대상 주소를 찾음 */
    val ecuFound: Boolean = false,
    /** 오류코드를 최소 1회 읽음 */
    val codesRead: Boolean = false,
    /** 읽은 결과가 파일로 저장됨 */
    val logSaved: Boolean = false,
    /** 안전 확인 항목 모두 선택 */
    val safetyChecked: Boolean = false,
    /** 확인 문구를 정확히 입력 */
    val confirmationTyped: Boolean = false,
    /** 이번 세션에서 이미 삭제했는지 */
    val alreadyCleared: Boolean = false
) {
    val eligible: Boolean
        get() = connected && ecuFound && codesRead && logSaved &&
            safetyChecked && confirmationTyped && !alreadyCleared

    val unmetReasons: List<String>
        get() = buildList {
            if (!connected) add("어댑터가 연결되어 있지 않습니다.")
            if (!ecuFound) add("응답하는 ECU 를 아직 찾지 못했습니다. 진단을 먼저 실행하십시오.")
            if (!codesRead) add("오류코드를 아직 읽지 않았습니다.")
            if (!logSaved) add("읽은 결과가 파일로 저장되지 않았습니다.")
            if (!safetyChecked) add("안전 확인 항목을 모두 선택해야 합니다.")
            if (!confirmationTyped) add("확인 문구를 정확히 입력해야 합니다.")
            if (alreadyCleared) add("이번 세션에서 이미 삭제했습니다. 반복 삭제는 허용하지 않습니다.")
        }
}

/** ECU 오류코드 삭제 화면의 확인 절차. */
object UdsClearConfirmation {

    const val PHRASE: String = "오류코드를 저장했습니다"

    fun matches(input: String): Boolean = input.trim() == PHRASE

    /**
     * 최상단 경고.
     *
     * 삭제가 무엇이고 무엇이 아닌지 정확히 알려 준다.
     * 과장하지도, 축소하지도 않는다.
     */
    const val WARNING: String =
        "이 기능은 ECU 에 저장된 고장 기록을 지웁니다.\n\n" +
            "고장 감지 기능을 끄는 것이 아닙니다. 원인이 남아 있으면 ECU 가 다시 검출해 " +
            "같은 코드를 다시 세우고, 차량은 다시 같은 상태가 됩니다.\n\n" +
            "삭제는 수리가 아닙니다."

    /** 고전압 계통 코드가 있을 때 추가로 보여 주는 경고. */
    const val HIGH_VOLTAGE_WARNING: String =
        "읽어 온 코드 중에 고전압 배터리 계통 코드가 있습니다.\n\n" +
            "차량이 충전과 주행을 막고 있다면 그것은 보호 동작입니다. " +
            "기록을 지워 차량이 다시 동작하더라도, 원인이 해결된 것은 아닙니다.\n\n" +
            "타는 냄새, 연기, 비정상적인 열, 절연 경고가 있으면 즉시 중단하고 " +
            "차량에서 떨어지십시오."

    val CHECKLIST: List<String> = listOf(
        "삭제 전 오류코드와 원시 응답을 저장했습니다.",
        "삭제가 고장을 수리하지 않는다는 점을 이해했습니다.",
        "원인이 남아 있으면 코드가 다시 나타난다는 점을 이해했습니다.",
        "고전압 관련 이상 징후가 보이면 즉시 중단하겠습니다."
    )
}
