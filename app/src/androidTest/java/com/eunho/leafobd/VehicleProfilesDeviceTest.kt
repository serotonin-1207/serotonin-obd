package com.eunho.leafobd

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eunho.leafobd.data.*
import com.eunho.leafobd.ev.EvProfile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleProfilesDeviceTest {
    @Test fun profilesAndSelectionPersist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val repository = VehicleProfileRepository(context)
        val leaf = VehicleProfile(alias = "시험 리프", manufacturer = "닛산", model = "리프 ZE1", modelYear = 2019,
            powertrain = VehiclePowertrain.ELECTRIC, evProfile = EvProfile.LEAF_ZE1)
        val niro = VehicleProfile(alias = "시험 니로", manufacturer = "기아", model = "니로 EV DE", modelYear = 2019,
            powertrain = VehiclePowertrain.ELECTRIC, evProfile = EvProfile.NIRO_DE)
        repository.save(listOf(leaf, niro))
        assertEquals(setOf(leaf.id, niro.id), VehicleProfileRepository(context).load().map { it.id }.toSet())
        val settings = SettingsRepository(context)
        settings.save(settings.load().copy(selectedVehicleProfileId = leaf.id, vehicleName = leaf.alias))
        assertEquals(leaf.id, SettingsRepository(context).load().selectedVehicleProfileId)
        repository.save(emptyList())
        settings.save(settings.load().copy(selectedVehicleProfileId = null, vehicleName = AppSettings.DEFAULT_VEHICLE))
    }

    @Test fun oldProfileWithoutNewSpecificationFieldsStillLoads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa"))
        val id = java.util.UUID.randomUUID().toString()
        val oldJson = """[{"id":"$id","alias":"구형 기록","manufacturer":"현대","model":"아반떼","modelYear":2020,"powertrain":"GASOLINE","evProfile":""}]"""
        context.getSharedPreferences("vehicle_profiles", android.content.Context.MODE_PRIVATE).edit().putString("profiles", oldJson).commit()
        val loaded = VehicleProfileRepository(context).load().single()
        assertEquals(VehicleMarket.UNKNOWN, loaded.market)
        assertEquals("", loaded.engine)
        assertEquals("", loaded.transmission)
        VehicleProfileRepository(context).save(emptyList())
    }
}
