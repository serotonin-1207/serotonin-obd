package com.eunho.leafobd.nissan

/**
 * Nissan Leaf 전용 진단 모듈의 **자리표시자**.
 *
 * 이 패키지에는 실제 CAN 명령이 하나도 없다. 자세한 배경은 같은 폴더의 `README.md` 를 보라.
 *
 * 요약:
 *  - 검증되지 않은 ECU 주소 · 진단 세션 명령 · 보안 접근 키를 추측해서 차량에 보내면
 *    고전압 계통이나 에어백/ABS 모듈이 비정상 상태가 될 수 있다.
 *  - 따라서 1차 버전에서는 인터페이스만 두고 [ENABLED] 로 잠가 둔다.
 *  - 표준 OBD-II 기능은 `obd/ObdService` 가 담당하며 이 모듈과 완전히 분리되어 있다.
 */
interface NissanLeafService {

    /** 읽기 전용 진단이 가능한 ECU 목록. 지금은 항상 비어 있다. */
    fun availableEcus(): List<NissanEcu>

    /**
     * 특정 ECU의 DTC를 읽는다.
     *
     * @throws UnsupportedOperationException 항상. 검증된 자료가 없으므로 구현하지 않는다.
     */
    suspend fun readEcuDtcs(ecu: NissanEcu): Nothing

    companion object {
        /**
         * 기능 플래그.
         *
         * `true` 로 바꾸기 전에 `README.md` 의 조건을 반드시 충족해야 한다.
         * 삭제 기능은 이 플래그와 별개로 추가 승인 절차가 필요하다.
         */
        const val ENABLED: Boolean = false

        const val DISABLED_REASON: String =
            "Nissan Leaf 전용 진단은 검증된 공식 자료와 실제 통신 로그가 확보되기 전까지 제공하지 않습니다. " +
                "확인되지 않은 전용 명령을 추측해서 차량에 보내는 것은 위험합니다."
    }
}

/**
 * Leaf의 주요 제어 모듈.
 *
 * 이름과 역할 설명만 담고 있으며, **주소나 명령은 포함하지 않는다.**
 * 사용자에게 "표준 OBD로는 이 모듈들을 읽지 못할 수 있다"는 점을 설명하는 데만 쓴다.
 */
enum class NissanEcu(val displayName: String, val description: String) {
    VCM("VCM", "차량 제어 모듈. 주행·충전 전반을 관리한다."),
    LBC("LBC", "리튬 배터리 컨트롤러. 고전압 배터리 상태와 접촉기를 관리한다."),
    OBC_PDM("OBC/PDM", "차량 탑재 충전기 및 전력 분배 모듈. 완속 충전을 담당한다."),
    EVSE("충전 인터페이스", "충전 포트와 충전기 통신 관련 부분."),
    ABS("ABS", "제동 제어. 안전 계통이므로 이 앱에서 절대 다루지 않는다."),
    SRS("에어백(SRS)", "탑승자 보호 장치. 안전 계통이므로 이 앱에서 절대 다루지 않는다.")
}
