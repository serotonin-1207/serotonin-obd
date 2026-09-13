package com.eunho.leafobd.log

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import java.util.Locale

object BatteryReportPdf {
    fun create(context: Context, report: BatteryReport) = WorkshopPdf.create(context, report.summary) { document, firstPage ->
        report.charts.forEachIndexed { index, chart ->
            val pageNumber = firstPage + index
            val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
            canvas.drawText(chart.title, 48f, 65f, paint)
            canvas.drawText(if (report.demo) "가상 예시 · 실제 차량 측정 아님" else "단일 시점 기록 · 정상/고장 판정 아님", 48f, 88f, paint)
            canvas.drawText("번호 ${chart.points.first().first}~${chart.points.last().first} · 단위 ${chart.unit} · 확대 눈금", 48f, 111f, paint)
            val values = chart.points.mapNotNull { it.second }
            val pad = if (chart.unit == "V") 0.01 else 1.0
            val low = (values.minOrNull() ?: 0.0) - pad
            val high = (values.maxOrNull() ?: 0.0) + pad
            for (i in 0..4) {
                val y = 180f + i * 70f
                paint.color = Color.LTGRAY
                canvas.drawLine(100f, y, 547f, y, paint)
                paint.color = Color.DKGRAY
                canvas.drawText(String.format(Locale.ROOT, "%.3f", high - (high - low) * i / 4), 48f, y + 4, paint)
            }
            val step = 447f / chart.points.size
            chart.points.forEachIndexed { i, (id, value) ->
                val x = 100f + step * (i + 0.5f)
                paint.color = Color.rgb(20, 102, 74)
                if (value != null) canvas.drawCircle(x, (460 - 280 * (value - low) / (high - low)).toFloat(), 2.5f, paint)
                else {
                    paint.color = Color.GRAY
                    canvas.drawLine(x - 2, 481f, x + 2, 485f, paint)
                    canvas.drawLine(x - 2, 485f, x + 2, 481f, paint)
                }
                if (i % 8 == 0 || i == chart.points.lastIndex) {
                    paint.color = Color.DKGRAY
                    canvas.drawText(id.toString(), x - 5, 510f, paint)
                }
            }
            paint.color = Color.BLACK
            canvas.drawText("가로축: 채널/센서 번호 · 점: 수신값 · 아래 ×: 미수신/범위 미확인", 48f, 545f, paint)
            canvas.drawText("번호 순서는 실제 배터리 배치도가 아닙니다. 온도와 전압은 별도 그래프입니다.", 48f, 568f, paint)
            paint.textSize = 9f
            chart.points.chunked(4).forEachIndexed { row, points ->
                canvas.drawText(points.joinToString("    ") { (id, value) -> "$id: ${value?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "미확인"}" }, 48f, 596f + row * 11f, paint)
            }
            paint.textSize = 11f
            canvas.drawText("Serotonin OBD · $pageNumber", 48f, 805f, paint)
            document.finishPage(page)
        }
    }
}
