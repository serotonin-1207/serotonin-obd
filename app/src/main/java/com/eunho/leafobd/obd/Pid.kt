package com.eunho.leafobd.obd

/**
 * 해석해서 보여줄 표준 PID 목록. (SAE J1979)
 *
 * 공개 표준에 정의된 계산식만 사용한다. 제조사 전용 PID는 넣지 않는다.
 *
 * 주의: 전기차인 Leaf 에는 엔진 관련 PID(RPM, 냉각수 온도 등)가
 * 아예 없을 수 있다. 지원하지 않는 PID는 조회하지 않으며,
 * 응답이 없어도 오류가 아니다.
 *
 * @param bytes 이 PID가 돌려주는 데이터 바이트 수
 */
enum class Pid(
    val id: Int,
    val label: String,
    val bytes: Int,
    val unit: String = ""
) {
    ENGINE_LOAD(0x04, "계산 엔진 부하", 1, "%"),
    COOLANT_TEMP(0x05, "냉각수 온도", 1, "°C"),
    ENGINE_RPM(0x0C, "엔진 회전수", 2, "rpm"),
    VEHICLE_SPEED(0x0D, "차량 속도", 1, "km/h"),
    INTAKE_TEMP(0x0F, "흡기 온도", 1, "°C"),
    THROTTLE(0x11, "스로틀 위치", 1, "%"),
    RUN_TIME(0x1F, "시동 후 경과 시간", 2, "초"),
    DISTANCE_MIL(0x21, "경고등 점등 후 주행거리", 2, "km"),
    FUEL_LEVEL(0x2F, "연료 잔량", 1, "%"),
    WARMUPS_SINCE_CLEAR(0x30, "코드 삭제 후 예열 횟수", 1, "회"),
    DISTANCE_SINCE_CLEAR(0x31, "코드 삭제 후 주행거리", 2, "km"),
    CONTROL_MODULE_VOLTAGE(0x42, "제어 모듈 전압", 2, "V"),
    AMBIENT_TEMP(0x46, "외기 온도", 1, "°C"),
    OIL_TEMP(0x5C, "엔진 오일 온도", 1, "°C"),
    HYBRID_BATTERY_LIFE(0x5B, "하이브리드/EV 배터리 잔량", 1, "%");

    /** `01`/`02` 뒤에 붙는 2자리 16진 PID 문자열. */
    val hex: String get() = "%02X".format(id)

    companion object {
        fun of(id: Int): Pid? = entries.firstOrNull { it.id == id }
    }
}

/** 해석된 PID 값 하나. */
data class PidValue(
    val pid: Pid?,
    val id: Int,
    val rawBytes: List<Int>,
    val value: Double?
) {
    val label: String get() = pid?.label ?: "PID 0x%02X".format(id)

    val rawHex: String get() = rawBytes.joinToString(" ") { "%02X".format(it) }

    /** 화면에 표시할 문자열. 해석하지 못하면 원시 16진값을 그대로 보여준다. */
    val display: String
        get() {
            val v = value ?: return rawHex
            val unit = pid?.unit.orEmpty()
            val text = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
            return if (unit.isEmpty()) text else "$text $unit"
        }
}

/**
 * 표준 PID 계산식.
 *
 * 각 식은 SAE J1979 공개 정의를 따른다. 확인되지 않은 식은 넣지 않고
 * 원시 바이트만 보존한다(그래야 나중에 검증된 자료로 다시 해석할 수 있다).
 */
object PidDecoder {

    fun decode(id: Int, data: List<Int>): PidValue {
        val pid = Pid.of(id)
        val a = data.getOrNull(0)
        val b = data.getOrNull(1)

        val value: Double? = when (id) {
            0x04 -> a?.let { it * 100.0 / 255.0 }
            0x05 -> a?.let { (it - 40).toDouble() }
            0x0C -> if (a != null && b != null) ((a * 256 + b) / 4.0) else null
            0x0D -> a?.toDouble()
            0x0F -> a?.let { (it - 40).toDouble() }
            0x11 -> a?.let { it * 100.0 / 255.0 }
            0x1F -> if (a != null && b != null) (a * 256 + b).toDouble() else null
            0x21 -> if (a != null && b != null) (a * 256 + b).toDouble() else null
            0x2F -> a?.let { it * 100.0 / 255.0 }
            0x30 -> a?.toDouble()
            0x31 -> if (a != null && b != null) (a * 256 + b).toDouble() else null
            0x42 -> if (a != null && b != null) ((a * 256 + b) / 1000.0) else null
            0x46 -> a?.let { (it - 40).toDouble() }
            0x5B -> a?.let { it * 100.0 / 255.0 }
            0x5C -> a?.let { (it - 40).toDouble() }
            else -> null
        }

        return PidValue(pid = pid, id = id, rawBytes = data, value = value)
    }
}
