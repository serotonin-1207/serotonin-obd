package com.eunho.leafobd

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eunho.leafobd.log.RecordVehicleGroups
import com.eunho.leafobd.log.SavedSession
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run seed and verifyRestart in separate instrumentation invocations (new app process). */
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class VehicleGroupsDeviceTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = File(target.noBackupFilesDir, "vehicle-groups-device-test")
    private val context = object : ContextWrapper(target) {
        override fun getNoBackupFilesDir(): File = folder.apply { mkdirs() }
    }
    private fun session(name: String) = SavedSession(name, File(folder, "$name.txt"), File(folder, "$name.json"), 0L)
    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: Exception) { }
    }

    @Test fun seed() {
        check(target.packageName.endsWith(".qa"))
        folder.mkdirs()
        val first = session("synthetic-leaf")
        val second = session("synthetic-niro")
        first.jsonFile.writeText("{\"synthetic\":\"leaf\"}")
        second.jsonFile.writeText("{\"synthetic\":\"niro\"}")
        val store = RecordVehicleGroups(context)
        store.assign(listOf(first), "시험 리프")
        store.assign(listOf(second), "시험 니로")
        assertEquals(mapOf(first.baseName to "시험 리프", second.baseName to "시험 니로"), store.labels(listOf(first, second)))
    }

    @Test fun verifyRestartAndMutations() {
        check(target.packageName.endsWith(".qa"))
        val first = session("synthetic-leaf")
        val second = session("synthetic-niro")
        val store = RecordVehicleGroups(context)
        assertEquals("시험 리프", store.labels(listOf(first))[first.baseName])
        assertEquals("시험 니로", store.labels(listOf(second))[second.baseName])
        store.assign(listOf(first), "시험 리프 2")
        assertEquals("시험 리프 2", RecordVehicleGroups(context).labels(listOf(first))[first.baseName])
        store.assign(listOf(first), null)
        assertFalse(store.labels(listOf(first)).containsKey(first.baseName))
        assertEquals("시험 니로", store.labels(listOf(second))[second.baseName])
        second.jsonFile.writeText("{\"synthetic\":\"replaced-content\"}")
        assertTrue(store.labels(listOf(second)).isEmpty())
        val groups = File(folder, "record-vehicle-groups.json")
        val original = groups.readBytes()
        expectFailure { store.assign(listOf(first, session("missing")), "시험") }
        assertArrayEquals(original, groups.readBytes())
        groups.writeText("broken-json")
        expectFailure { store.assign(listOf(first), "시험") }
        assertEquals("broken-json", groups.readText())
        groups.writeBytes(ByteArray(2_000_001))
        expectFailure { store.labels(listOf(first)) }
        // Only this test's private folder inside the isolated QA package is removed.
        assertTrue(folder.deleteRecursively())
    }
}
