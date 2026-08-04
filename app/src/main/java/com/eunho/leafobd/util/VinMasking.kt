package com.eunho.leafobd.util

/**
 * 차대번호(VIN) 마스킹.
 *
 * VIN 은 차량 한 대를 특정할 수 있는 식별정보다.
 * 화면에서는 사용자가 자기 차를 확인할 수 있어야 하므로 전체를 보여주지만,
 * **파일에 저장하거나 밖으로 공유할 때는 마스킹한다.**
 *
 * 남기는 부분과 이유:
 *  - 앞 3자리(WMI, 제조사·국가 코드): 특정 차량이 아니라 제조사만 알려 준다.
 *  - 뒤 4자리(일련번호 끝): 사용자가 자기 기록끼리 구분하는 데 필요하다.
 *  - 가운데(차종·연식·공장·전체 일련번호): 가린다.
 *
 * 예: `KNMAT2MT9FP123456` → `KNM**********3456`
 */
object VinMasking {

    private const val VISIBLE_PREFIX = 3
    private const val VISIBLE_SUFFIX = 4

    fun mask(vin: String?): String? {
        if (vin.isNullOrBlank()) return null
        val value = vin.trim()

        // 너무 짧으면 구분에 쓸 정보가 없으므로 전부 가린다.
        if (value.length <= VISIBLE_PREFIX + VISIBLE_SUFFIX) {
            return "*".repeat(value.length)
        }

        val hidden = value.length - VISIBLE_PREFIX - VISIBLE_SUFFIX
        return value.take(VISIBLE_PREFIX) + "*".repeat(hidden) + value.takeLast(VISIBLE_SUFFIX)
    }
}
