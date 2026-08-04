package com.eunho.leafobd.log

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 저장된 진단 기록 한 건(파일 쌍).
 *
 * @param txtFile 사람이 읽는 텍스트 파일
 * @param jsonFile 구조화된 JSON 파일
 */
data class SavedSession(
    val baseName: String,
    val txtFile: File,
    val jsonFile: File,
    val savedAtMillis: Long
) {
    val displayName: String get() = baseName
}

/**
 * 진단 로그를 앱 내부 저장소에 저장하고 다시 읽는다.
 *
 * 저장 위치: `context.filesDir/logs/`
 * - 앱 전용 영역이므로 다른 앱이 읽을 수 없다.
 * - 외부로 나가는 유일한 경로는 사용자가 직접 누르는 공유 버튼뿐이다.
 * - 인터넷 권한이 없으므로 자동 전송은 구조적으로 불가능하다.
 */
class DiagnosticLogRepository(private val context: Context) {

    private val logDir: File
        get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 세션을 TXT와 JSON 두 형식으로 저장한다.
     *
     * @return 저장 결과. 실패하면 [Result.failure] 에 한국어 메시지가 담긴 예외가 들어간다.
     */
    suspend fun save(session: DiagnosticSession): Result<SavedSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = SessionFormatter.fileBaseName(session)
                val txt = File(logDir, "$base.txt")
                val json = File(logDir, "$base.json")

                txt.writeText(SessionFormatter.toText(session), Charsets.UTF_8)
                json.writeText(SessionFormatter.toJson(session), Charsets.UTF_8)

                SavedSession(
                    baseName = base,
                    txtFile = txt,
                    jsonFile = json,
                    savedAtMillis = txt.lastModified()
                )
            }.recoverCatching { e ->
                throw IllegalStateException("로그 파일을 저장하지 못했습니다: ${e.message ?: "알 수 없는 오류"}", e)
            }
        }

    /** 저장된 기록 목록. 최신순. */
    suspend fun list(): List<SavedSession> = withContext(Dispatchers.IO) {
        runCatching {
            logDir.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
                .orEmpty()
                .mapNotNull { txt ->
                    val base = txt.nameWithoutExtension
                    val json = File(logDir, "$base.json")
                    SavedSession(
                        baseName = base,
                        txtFile = txt,
                        jsonFile = json,
                        savedAtMillis = txt.lastModified()
                    )
                }
                .sortedByDescending { it.savedAtMillis }
        }.getOrDefault(emptyList())
    }

    /** 텍스트 파일 내용을 읽는다. 실패하면 안내 문구를 돌려준다. */
    suspend fun readText(saved: SavedSession): String = withContext(Dispatchers.IO) {
        runCatching { saved.txtFile.readText(Charsets.UTF_8) }
            .getOrElse { "로그 파일을 읽지 못했습니다: ${it.message ?: "알 수 없는 오류"}" }
    }

    /** 기록 한 건(TXT+JSON)을 삭제한다. */
    suspend fun delete(saved: SavedSession): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            saved.txtFile.delete()
            if (saved.jsonFile.exists()) saved.jsonFile.delete()
            true
        }.getOrDefault(false)
    }

    private companion object {
        /** AndroidManifest 의 FileProvider `file_paths.xml` 과 반드시 같아야 한다. */
        const val DIR_NAME = "logs"
    }
}
