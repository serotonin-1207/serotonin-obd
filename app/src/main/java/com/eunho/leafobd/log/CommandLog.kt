package com.eunho.leafobd.log

import java.time.Instant

/**
 * 어댑터에 보낸 명령 한 건과 그 원시 응답.
 *
 * 진단의 근거는 "앱이 해석한 결과"가 아니라 "어댑터가 실제로 보낸 바이트"다.
 * 따라서 rawResponse는 어떤 경우에도 가공하지 않고 그대로 보관한다.
 */
data class CommandLog(
    val timestamp: Instant,
    val command: String,
    val rawResponse: String,
    val normalizedResponse: String?,
    val elapsedMs: Long,
    val success: Boolean,
    val errorMessage: String? = null
)
