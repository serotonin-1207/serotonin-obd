package com.eunho.leafobd

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eunho.leafobd.data.BatteryChannel
import com.eunho.leafobd.data.BatteryCsv
import com.eunho.leafobd.log.BatteryReport
import com.eunho.leafobd.log.BatteryReportPdf
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryPdfDeviceTest {
    @Test fun renderMultiPageMissingChannelReport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val data = BatteryCsv.example().copy(expectedChannels = 96,
            channels = List(96) { BatteryChannel(it + 1, if (it == 9) null else 3.69 + (it % 7) * 0.003, null) },
            temperatureSensors = listOf("private sensor name" to 24.0, "second" to 25.0, "third" to 26.0))
        val report = BatteryReport.from(data)
        val pdf = BatteryReportPdf.create(context, report)
        val output = File(context.getExternalFilesDir(null), "battery-report-qa").apply { mkdirs() }
        pdf.copyTo(File(output, "battery.pdf"), overwrite = true)
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(4, renderer.pageCount)
            for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(output, "page-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
