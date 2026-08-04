package com.eunho.leafobd.util

/**
 * 아주 작은 JSON 작성기.
 *
 * 외부 직렬화 라이브러리를 넣지 않기 위해 직접 만들었다.
 * (지시서: 불필요한 외부 라이브러리를 최소화한다)
 * Android API에 의존하지 않으므로 JVM 단위 테스트로 검증할 수 있다.
 */
object Json {

    /** JSON 문자열 값으로 안전하게 이스케이프한다. null 이면 `null` 리터럴. */
    fun str(value: String?): String {
        if (value == null) return "null"
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                // 그 밖의 제어문자(어댑터 응답에 섞여 들어올 수 있다)는 \uXXXX 로 남긴다.
                ch < ' ' -> sb.append("\\u%04x".format(ch.code))
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    fun bool(value: Boolean): String = if (value) "true" else "false"

    fun num(value: Number?): String = value?.toString() ?: "null"

    /** `"key": value` 형태의 목록을 객체로 묶는다. */
    fun obj(vararg fields: Pair<String, String>, indent: String = ""): String {
        if (fields.isEmpty()) return "{}"
        val inner = fields.joinToString(",\n") { (key, raw) -> "$indent  ${str(key)}: $raw" }
        return "{\n$inner\n$indent}"
    }

    fun array(items: List<String>, indent: String = ""): String {
        if (items.isEmpty()) return "[]"
        return "[\n" + items.joinToString(",\n") { "$indent  $it" } + "\n$indent]"
    }
}
