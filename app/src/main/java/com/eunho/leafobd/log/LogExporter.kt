package com.eunho.leafobd.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 저장된 로그를 사용자가 **직접 눌렀을 때만** 밖으로 내보낸다.
 *
 * 이 내보내기 경로는 사용자 공유 동작만 처리하며 자동 전송하지 않는다.
 * 여기서 하는 일은 Android 공유 창을 여는 것뿐이며, 어디로 보낼지는 사용자가 정한다.
 */
object LogExporter {

    private fun authority(context: Context): String = "${context.packageName}.fileprovider"

    private fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, authority(context), file)

    /** TXT 파일 공유. */
    fun shareText(context: Context, saved: SavedSession): Result<Unit> =
        share(context, listOf(saved.txtFile), "text/plain", saved.baseName)

    /** JSON 파일 공유. */
    fun shareJson(context: Context, saved: SavedSession): Result<Unit> =
        share(context, listOf(saved.jsonFile), "application/json", saved.baseName)

    /** TXT + JSON 함께 공유. */
    fun shareBoth(context: Context, saved: SavedSession): Result<Unit> {
        val files = listOfNotNull(
            saved.txtFile.takeIf { it.exists() },
            saved.jsonFile.takeIf { it.exists() }
        )
        return share(context, files, "*/*", saved.baseName)
    }

    private fun share(
        context: Context,
        files: List<File>,
        mimeType: String,
        title: String
    ): Result<Unit> = runCatching {
        val existing = files.filter { it.exists() }
        require(existing.isNotEmpty()) { "공유할 파일이 없습니다." }

        val uris = ArrayList(existing.map { uriFor(context, it) })

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }.apply {
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "진단 로그 공유")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }.recoverCatching { e ->
        throw IllegalStateException("공유 창을 열지 못했습니다: ${e.message ?: "알 수 없는 오류"}", e)
    }

    /** 결과 요약을 클립보드에 복사한다. */
    fun copyToClipboard(context: Context, label: String, text: String): Result<Unit> = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }.recoverCatching { e ->
        throw IllegalStateException("클립보드에 복사하지 못했습니다: ${e.message ?: "알 수 없는 오류"}", e)
    }
}
