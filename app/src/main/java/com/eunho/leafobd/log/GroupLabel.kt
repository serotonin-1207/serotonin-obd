package com.eunho.leafobd.log

object GroupLabel {
    fun normalize(label: String): String {
        val value = label.trim()
        require(value.length in 1..40 && value.none { it.isISOControl() }) { "차량 별칭을 1~40자로 입력하세요." }
        return value
    }
}
