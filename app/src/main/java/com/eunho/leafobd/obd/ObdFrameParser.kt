package com.eunho.leafobd.obd

import com.eunho.leafobd.util.ResponseText

/**
 * ECU 한 곳이 보낸 응답.
 *
 * @param ecuId CAN 헤더(예: `7E8`). 헤더 표시가 꺼져 있으면 null.
 * @param bytes 재조립된 데이터 바이트
 */
data class EcuResponse(
    val ecuId: String?,
    val bytes: List<Int>,
    /** 다중 프레임이 선언한 전체 길이. 단일 프레임이면 null. */
    val declaredLength: Int? = null,
    /**
     * 연속 프레임이 중간에 빠졌다면 **빠지기 직전까지 확보한 바이트 수**.
     *
     * 이 지점 뒤의 데이터는 정렬이 어긋나 있어 해석하면 안 된다.
     * 없으면 null.
     */
    val gapAfter: Int? = null
) {
    /** 선언한 길이만큼 빠짐없이 받았는지. */
    val complete: Boolean
        get() = gapAfter == null && (declaredLength == null || bytes.size >= declaredLength)

    /** 해석해도 되는 부분만 잘라 낸다. */
    val usableBytes: List<Int>
        get() = gapAfter?.let { bytes.take(it) } ?: bytes

    /** 화면에 표시할 ECU 이름. */
    val label: String
        get() = when (ecuId) {
            null -> "ECU"
            // ISO 15765-4 표준 응답 주소. 7E8 이 보통 주 제어 모듈이다.
            "7E8" -> "7E8 (주 응답 모듈)"
            else -> ecuId
        }
}

/**
 * ELM327 응답을 ECU별 바이트 목록으로 나눈다.
 *
 * 두 가지 표시 형식을 모두 처리한다.
 *
 * **헤더 꺼짐(ATH0)** — ELM327 이 다중 프레임을 알아서 합쳐 준다.
 * ```
 * 00A
 * 0: 43 04 01 33 02 45
 * 1: 03 21 04 56
 * ```
 *
 * **헤더 켜짐(ATH1)** — 원본 CAN 프레임이 그대로 보인다. ISO-TP(ISO 15765-2)
 * 흐름 제어 바이트(PCI)를 직접 해석해 합쳐야 한다.
 * ```
 * 7E81009490201314A...   (0x10 = 최초 프레임, 총 길이 0x009)
 * 7E82150303030...       (0x21 = 연속 프레임 1)
 * ```
 */
object ObdFrameParser {

    /** 11비트 CAN ID는 16진수 3자리(예: `7E8`). */
    private const val HEADER_LEN_11BIT = 3

    /** 29비트 CAN ID는 16진수 8자리(예: `18DAF110`). */
    private const val HEADER_LEN_29BIT = 8

    fun parse(dataLines: List<String>, headersOn: Boolean): List<EcuResponse> {
        if (!headersOn) {
            val bytes = ResponseText.hexBytes(dataLines)
            return if (bytes.isEmpty()) emptyList() else listOf(EcuResponse(null, bytes))
        }
        return parseWithHeaders(dataLines)
    }

    private fun parseWithHeaders(dataLines: List<String>): List<EcuResponse> {
        // 등장 순서를 유지하기 위해 LinkedHashMap 을 쓴다.
        val payloads = LinkedHashMap<String, MutableList<Int>>()
        val expectedLength = HashMap<String, Int>()

        // 연속 프레임 순번 추적. 순번이 건너뛰면 프레임이 유실된 것이다.
        val nextSequence = HashMap<String, Int>()
        val gapAfter = HashMap<String, Int>()

        for (line in dataLines) {
            val compact = line.replace(" ", "").uppercase()
            if (compact.isEmpty()) continue
            if (!compact.all { it in '0'..'9' || it in 'A'..'F' }) continue

            // 데이터 바이트는 항상 짝수 자리이므로, 전체 길이의 홀짝으로 헤더 길이를 판별한다.
            // 11비트 헤더(3자리)면 전체가 홀수, 29비트 헤더(8자리)면 짝수가 된다.
            val headerLen = if (compact.length % 2 == 1) HEADER_LEN_11BIT else HEADER_LEN_29BIT
            if (compact.length <= headerLen) continue

            val id = compact.substring(0, headerLen)
            val bytes = hexToBytes(compact.substring(headerLen))
            if (bytes.isEmpty()) continue

            val target = payloads.getOrPut(id) { mutableListOf() }
            val pci = bytes[0]

            when (pci and 0xF0) {
                // 단일 프레임: 하위 4비트가 데이터 길이
                0x00 -> {
                    val length = pci and 0x0F
                    target.addAll(bytes.drop(1).take(length))
                }
                // 최초 프레임: 하위 4비트 + 다음 바이트가 전체 길이
                0x10 -> {
                    val length = ((pci and 0x0F) shl 8) or bytes.getOrElse(1) { 0 }
                    expectedLength[id] = length
                    nextSequence[id] = 1  // 다음에 와야 할 연속 프레임 순번
                    target.addAll(bytes.drop(2))
                }
                // 연속 프레임: 하위 4비트는 순번(0~15, 15 다음은 0으로 돌아간다)
                0x20 -> {
                    val sequence = pci and 0x0F
                    val expected = nextSequence[id]
                    if (expected != null && sequence != expected && !gapAfter.containsKey(id)) {
                        // 순번이 건너뛰었다 — 프레임이 유실됐다.
                        // 이 지점 뒤로는 바이트 정렬이 어긋나므로 위치를 기록해 둔다.
                        gapAfter[id] = target.size
                    }
                    nextSequence[id] = (sequence + 1) and 0x0F
                    target.addAll(bytes.drop(1))
                }
                // 알 수 없는 형식이면 손대지 않고 그대로 둔다.
                else -> target.addAll(bytes)
            }
        }

        return payloads.map { (id, bytes) ->
            val limit = expectedLength[id]
            EcuResponse(
                ecuId = id,
                bytes = if (limit != null && limit <= bytes.size) bytes.take(limit) else bytes,
                declaredLength = limit,
                gapAfter = gapAfter[id]
            )
        }.filter { it.bytes.isNotEmpty() }
    }

    /** 홀수 길이면 마지막 반쪽 바이트는 잘린 것으로 보고 버린다. */
    private fun hexToBytes(hex: String): List<Int> {
        val result = ArrayList<Int>(hex.length / 2)
        var i = 0
        while (i + 1 < hex.length) {
            result.add(hex.substring(i, i + 2).toInt(16))
            i += 2
        }
        return result
    }
}
