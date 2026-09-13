package com.eunho.leafobd

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eunho.leafobd.data.DiagnosticKnowledge
import com.eunho.leafobd.data.KnowledgePackSecurity
import com.eunho.leafobd.data.KnowledgePackStore
import com.eunho.leafobd.data.PublicKnowledgePackCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgePackDeviceTest {
    @Test fun bundledJsonLoadsFromApkAssets() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(DiagnosticKnowledge.installBundled(context))
        assertFalse(DiagnosticKnowledge.loadError)
        assertEquals("2026.09.13-public.7", DiagnosticKnowledge.VERSION)
        assertEquals(105, DiagnosticKnowledge.entries.size)
        assertEquals(34, DiagnosticKnowledge.sources.size)
        assertEquals(102, DiagnosticKnowledge.catalogStatus.commonObdEntries)
        assertTrue(DiagnosticKnowledge.catalogStatus.issues.isEmpty())
    }

    @Test fun signedPackPersistsAndLoadsFromPrivateStorage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manifest = context.assets.open(KnowledgePackSecurity.MANIFEST_ASSET_NAME).use { it.readBytes() }
        val pack = context.assets.open(PublicKnowledgePackCodec.ASSET_NAME).use { it.readBytes() }
        val saved = KnowledgePackStore(context).save(manifest, pack)
        val tampered = pack.clone().also { it[it.lastIndex - 2] = (it[it.lastIndex - 2].toInt() xor 1).toByte() }
        assertTrue(runCatching { KnowledgePackStore(context).save(manifest, tampered) }.isFailure)
        val loaded = KnowledgePackStore(context).load()
        assertEquals(saved.manifest.revision, loaded?.manifest?.revision)
        assertEquals(saved.pack.version, loaded?.pack?.version)
    }
}
