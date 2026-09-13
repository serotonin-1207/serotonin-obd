package com.eunho.leafobd.data

import com.eunho.leafobd.ev.EvProfile
import org.junit.Assert.*
import org.junit.Test

class VehicleProfilesTest {
    private fun profile(alias: String, id: String = java.util.UUID.randomUUID().toString()) = VehicleProfile(id, alias, "닛산", "리프", 2019, VehiclePowertrain.ELECTRIC, EvProfile.LEAF_ZE1)
    @Test fun trimsAndSortsProfiles() {
        val result = VehicleProfiles.put(VehicleProfiles.put(emptyList(), profile(" 리프 B ")), profile("리프 A"))
        assertEquals(listOf("리프 A", "리프 B"), result.map { it.alias })
    }
    @Test fun updateKeepsIdentity() {
        val old = profile("리프")
        assertEquals("리프 새 이름", VehicleProfiles.put(listOf(old), old.copy(alias = "리프 새 이름")).single().alias)
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateAliasRejected() { VehicleProfiles.put(listOf(profile("리프")), profile("리프")) }
    @Test(expected = IllegalArgumentException::class) fun iceCannotUseEvProtocol() { VehicleProfiles.validate(profile("차").copy(powertrain = VehiclePowertrain.GASOLINE)) }
}
