package com.eunho.leafobd.elm327

import com.eunho.leafobd.log.CommandLog
import com.eunho.leafobd.util.ResponseText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

/** ELM327 통신 중 발생한, 사용자에게 한국어로 보여줄 수 있는 오류. */
class Elm327Exception(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * ELM327 명령을 **한 번에 하나씩** 직렬로 보내고 응답을 읽는 클라이언트.
 *
 * 핵심 규칙
 *  - [mutex] 로 명령을 직렬화한다. 두 명령이 동시에 나가면 응답이 섞여 해석할 수 없다.
 *  - 응답은 ELM327 프롬프트 문자 `>` 가 나올 때까지 읽는다.
 *  - 모든 입출력은 [dispatcher](기본 `Dispatchers.IO`)에서 실행한다. 메인 스레드를 쓰지 않는다.
 *  - 어떤 예외도 밖으로 던지지 않고 실패한 [CommandLog] 로 바꿔 돌려준다.
 *    (앱이 종료되지 않고 사용자에게 한국어 메시지를 보여주기 위한 규칙)
 */
class Elm327Client(
    private val transport: Elm327Transport,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Instant = Instant::now
) {

    private val mutex = Mutex()
    private val buffer = ByteArray(READ_BUFFER_SIZE)

    val transportDescription: String get() = transport.description
    val simulated: Boolean get() = transport.simulated
    val isOpen: Boolean get() = transport.isOpen

    /** 통로를 연다. 실패하면 [Elm327Exception]. */
    suspend fun open() = withContext(dispatcher) {
        try {
            transport.open()
        } catch (e: Exception) {
            throw Elm327Exception(describe(e), e)
        }
    }

    fun close() = transport.close()

    suspend fun send(command: Elm327Command): CommandLog =
        send(command.command, command.timeoutMs)

    /**
     * 명령 하나를 보내고 프롬프트가 올 때까지 기다린다.
     *
     * @return 성공/실패와 원시 응답을 모두 담은 로그. 예외를 던지지 않는다.
     */
    suspend fun send(
        command: String,
        timeoutMs: Long = Elm327Command.DEFAULT_TIMEOUT_MS
    ): CommandLog = mutex.withLock {
        withContext(dispatcher) {
            val startedAt = clock()
            val startNs = System.nanoTime()

            fun elapsed(): Long = (System.nanoTime() - startNs) / 1_000_000

            try {
                if (!transport.isOpen) transport.open()

                // 이전 명령의 잔여 바이트가 남아 있으면 응답이 섞이므로 먼저 비운다.
                drain()

                transport.write((command + "\r").toByteArray(Charsets.US_ASCII))
                val raw = readUntilPrompt(timeoutMs)

                val errorKeyword = ResponseText.errorKeyword(raw, command)
                CommandLog(
                    timestamp = startedAt,
                    command = command,
                    rawResponse = raw,
                    normalizedResponse = ResponseText.dataLines(raw, command).joinToString(" | "),
                    elapsedMs = elapsed(),
                    success = errorKeyword == null,
                    errorMessage = errorKeyword?.let { ResponseText.errorMessageKorean(it) }
                )
            } catch (e: Exception) {
                CommandLog(
                    timestamp = startedAt,
                    command = command,
                    rawResponse = "",
                    normalizedResponse = null,
                    elapsedMs = elapsed(),
                    success = false,
                    errorMessage = describe(e)
                )
            }
        }
    }

    /**
     * `ATMA`(Monitor All)처럼 **끝나지 않는 수신 모드**를 일정 시간 동안 실행한다.
     *
     * ATMA 는 프롬프트를 돌려주지 않고 버스에 흐르는 프레임을 계속 뿌린다.
     * 멈추려면 아무 문자나 보내야 하므로 일반 [send] 로는 다룰 수 없다.
     *
     * **이 함수는 차량에 어떤 요청도 보내지 않는다.** 듣기만 한다.
     * 종료할 때 보내는 캐리지리턴 한 글자는 어댑터에게 "그만"이라고 알리는 것이며
     * 차량 버스로 나가지 않는다.
     *
     * @param durationMs 수신할 시간
     * @return 수신한 원시 문자열 (명령 로그 포함)
     */
    suspend fun monitor(
        command: String = "ATMA",
        durationMs: Long = 10_000L
    ): CommandLog = mutex.withLock {
        withContext(dispatcher) {
            val startedAt = clock()
            val startNs = System.nanoTime()
            val sb = StringBuilder()

            try {
                if (!transport.isOpen) transport.open()
                drain()
                transport.write((command + "\r").toByteArray(Charsets.US_ASCII))

                val deadline = System.currentTimeMillis() + durationMs
                while (System.currentTimeMillis() < deadline) {
                    val read = transport.read(buffer)
                    when {
                        read < 0 -> throw Elm327Exception("어댑터와의 연결이 끊어졌습니다.")
                        read == 0 -> delay(POLL_INTERVAL_MS)
                        else -> {
                            sb.append(String(buffer, 0, read, Charsets.US_ASCII))
                            // 어댑터가 스스로 멈춘 경우(BUFFER FULL 등)
                            if (sb.contains("BUFFER FULL") || sb.contains("STOPPED")) break
                        }
                    }
                }

                // 수신 모드 종료 — 아무 문자나 보내면 멈춘다.
                runCatching { transport.write("\r".toByteArray(Charsets.US_ASCII)) }
                // 남은 출력과 프롬프트를 비운다.
                val stopDeadline = System.currentTimeMillis() + STOP_DRAIN_MS
                while (System.currentTimeMillis() < stopDeadline) {
                    val read = transport.read(buffer)
                    if (read > 0) sb.append(String(buffer, 0, read, Charsets.US_ASCII))
                    else delay(POLL_INTERVAL_MS)
                }

                CommandLog(
                    timestamp = startedAt,
                    command = command,
                    rawResponse = sb.toString(),
                    normalizedResponse = "수신 ${sb.count { it == '\r' || it == '\n' }} 줄",
                    elapsedMs = (System.nanoTime() - startNs) / 1_000_000,
                    success = true
                )
            } catch (e: Exception) {
                CommandLog(
                    timestamp = startedAt,
                    command = command,
                    rawResponse = sb.toString(),
                    normalizedResponse = null,
                    elapsedMs = (System.nanoTime() - startNs) / 1_000_000,
                    success = false,
                    errorMessage = describe(e)
                )
            }
        }
    }

    /**
     * 프롬프트 `>` 가 나올 때까지 읽는다.
     *
     * 블로킹 읽기 대신 폴링 + `delay` 를 쓰기 때문에 코루틴 취소와 타임아웃이 정확히 동작한다.
     */
    private suspend fun readUntilPrompt(timeoutMs: Long): String {
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val read = transport.read(buffer)
            when {
                read < 0 -> throw Elm327Exception("어댑터와의 연결이 끊어졌습니다. 다시 연결해 주십시오.")
                read == 0 -> delay(POLL_INTERVAL_MS)
                else -> {
                    sb.append(String(buffer, 0, read, Charsets.US_ASCII))
                    if (sb.indexOf(ResponseText.PROMPT.toString()) >= 0) {
                        return sb.toString()
                    }
                }
            }
        }

        // 타임아웃이어도 지금까지 받은 내용은 로그에 남길 가치가 있다.
        val partial = sb.toString().trim()
        val detail = if (partial.isEmpty()) "응답이 전혀 없었습니다." else "부분 응답: $partial"
        throw Elm327Exception("어댑터가 ${timeoutMs}ms 안에 응답하지 않았습니다. $detail")
    }

    /** 버퍼에 남아 있는 이전 응답 찌꺼기를 비운다. */
    private suspend fun drain() {
        val deadline = System.currentTimeMillis() + DRAIN_MS
        while (System.currentTimeMillis() < deadline) {
            val read = transport.read(buffer)
            if (read < 0) return
            if (read == 0) delay(POLL_INTERVAL_MS)
        }
    }

    /** 예외를 사용자에게 보여줄 한국어 문장으로 바꾼다. */
    private fun describe(e: Exception): String = when (e) {
        is Elm327Exception -> e.message ?: "어댑터 통신 오류가 발생했습니다."
        is SecurityException -> "Bluetooth 권한이 없습니다. 설정에서 권한을 허용해 주십시오."
        is IOException -> "어댑터 입출력 오류: ${e.message ?: "연결이 끊어졌을 수 있습니다."}"
        else -> "알 수 없는 오류가 발생했습니다: ${e::class.java.simpleName} ${e.message ?: ""}".trim()
    }

    private companion object {
        const val READ_BUFFER_SIZE = 1024

        /** 폴링 간격. 너무 짧으면 CPU를 낭비하고 너무 길면 응답이 느려 보인다. */
        const val POLL_INTERVAL_MS = 15L

        /** 명령 전송 전 잔여 데이터를 비우는 시간. */
        const val DRAIN_MS = 30L

        /** 수신 모드를 멈춘 뒤 남은 출력을 비우는 시간. */
        const val STOP_DRAIN_MS = 400L
    }
}
