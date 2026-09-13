package com.eunho.leafobd.log

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

object WorkshopPdf {
    /** A4, wrapping Korean text with line-based pagination; input is the reviewed allowlist report. */
    fun create(context: Context, text: String, appendPages: ((PdfDocument, Int) -> Unit)? = null): File {
        require(text.length <= 500_000) { "보고서가 너무 큽니다." }
        val paint = TextPaint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 499)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(4f, 1f).setIncludePad(false).build()
        val file = File(File(context.cacheDir, "workshop-reports").apply { mkdirs() }, "report-${java.util.UUID.randomUUID()}.pdf")
        try {
            val document = PdfDocument()
            try {
                var line = 0; var pageNumber = 1
                while (line < layout.lineCount) {
                    val top = layout.getLineTop(line)
                    var end = line + 1
                    while (end < layout.lineCount && layout.getLineBottom(end) - top <= 724) end++
                    val bottom = layout.getLineBottom(end - 1)
                    val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                    page.canvas.save()
                    page.canvas.clipRect(48f, 48f, 547f, (48 + bottom - top).toFloat())
                    page.canvas.translate(48f, (48 - top).toFloat())
                    layout.draw(page.canvas)
                    page.canvas.restore()
                    page.canvas.drawText("Serotonin OBD · $pageNumber", 48f, 805f, paint)
                    document.finishPage(page)
                    line = end; pageNumber++
                }
                appendPages?.invoke(document, pageNumber)
                file.outputStream().use { document.writeTo(it) }
            } finally { document.close() }
            return file
        } catch (e: Exception) { file.delete(); throw e }
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("정비 보고서", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "개인정보 제외 PDF 공유"))
    }
}
