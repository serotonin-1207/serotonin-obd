package com.eunho.leafobd.util

/**
 * Bluetooth MAC 주소 마스킹.
 *
 * MAC 주소는 기기를 특정할 수 있는 식별정보다.
 * 로그 파일과 화면에는 **마지막 2바이트만** 남기고 나머지는 가린다.
 * 전체 주소는 메모리 안에서 연결에만 쓰고 파일에 기록하지 않는다.
 */
object MacMasking {

    private const val HIDDEN = "**"

    /**
     * `00:1D:A5:68:98:8B` → `**:**:**:**:98:8B`
     *
     * 형식이 예상과 다르면 마지막 5글자만 남긴다.
     */
    fun mask(address: String?): String? {
        if (address.isNullOrBlank()) return null

        val parts = address.split(':')
        if (parts.size >= 2) {
            val visible = parts.takeLast(2)
            val hidden = List(parts.size - 2) { HIDDEN }
            return (hidden + visible).joinToString(":")
        }

        return if (address.length > 5) HIDDEN + address.takeLast(5) else address
    }
}
