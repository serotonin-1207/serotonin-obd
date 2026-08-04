package com.eunho.leafobd.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 새 버전 정보. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val downloadUrl: String
)

/** 업데이트 확인 결과. */
sealed interface UpdateResult {
    /** 최신 버전을 쓰고 있음. */
    data object UpToDate : UpdateResult

    /** 새 버전이 있음. */
    data class Available(val info: UpdateInfo) : UpdateResult

    /** 확인에 실패함(네트워크 없음 등). 실패해도 앱 사용에는 지장이 없다. */
    data class Failed(val message: String) : UpdateResult
}

/**
 * 새 버전이 있는지 확인한다.
 *
 * ## 무엇을 하고, 무엇을 하지 않는가
 *
 * **하는 일**: GitHub 에 올려 둔 `version.json` 을 내려받아 최신 버전 번호를 읽는다.
 * 현재 설치된 버전보다 크면 "새 버전 있음"을 알려 준다.
 *
 * **하지 않는 일**: 진단 데이터(오류코드·원시 응답·로그·차대번호)를 어디로도 보내지 않는다.
 * 이 클래스가 만드는 통신은 **버전 파일을 읽어 오는 GET 요청 하나뿐**이며,
 * 요청 본문에 기기나 차량 정보를 담지 않는다.
 *
 * 업데이트 확인은 설정에서 끌 수 있다. 껐거나 네트워크가 없으면 앱은 그대로 동작한다.
 */
object UpdateChecker {

    private const val TIMEOUT_MS = 8_000

    /**
     * @param currentVersionCode 현재 설치된 [BuildConfig.VERSION_CODE]
     */
    suspend fun check(currentVersionCode: Int): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val text = fetch(AppInfo.VERSION_JSON_URL)
            val info = parse(text)
                ?: return@withContext UpdateResult.Failed("버전 정보를 해석하지 못했습니다.")

            if (info.versionCode > currentVersionCode) UpdateResult.Available(info)
            else UpdateResult.UpToDate
        } catch (e: Exception) {
            // 네트워크가 없거나 저장소가 아직 없을 수 있다. 조용히 실패한다.
            UpdateResult.Failed("업데이트를 확인하지 못했습니다: ${e.message ?: "네트워크 오류"}")
        }
    }

    private fun fetch(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // 어떤 기기·차량 정보도 헤더에 넣지 않는다.
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 아주 작은 JSON 파싱. 외부 라이브러리를 쓰지 않으려고 직접 읽는다.
     * 형식은 [AppInfo.VERSION_JSON_URL] 주석 참조.
     */
    private fun parse(json: String): UpdateInfo? {
        val versionCode = intField(json, "versionCode") ?: return null
        return UpdateInfo(
            versionCode = versionCode,
            versionName = stringField(json, "versionName") ?: versionCode.toString(),
            notes = stringField(json, "notes") ?: "",
            downloadUrl = stringField(json, "downloadUrl") ?: AppInfo.REPO_URL
        )
    }

    private fun intField(json: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

    private fun stringField(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)
            ?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
}
