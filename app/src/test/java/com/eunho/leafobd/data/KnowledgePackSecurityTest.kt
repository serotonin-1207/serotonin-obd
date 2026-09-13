package com.eunho.leafobd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KnowledgePackSecurityTest {
    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)).use { it.readBytes() }

    @Test fun `배포 데이터 팩의 해시와 서명을 검증한다`() {
        val verified = KnowledgePackSecurity.verify(
            resource(KnowledgePackSecurity.MANIFEST_ASSET_NAME),
            resource(PublicKnowledgePackCodec.ASSET_NAME)
        )
        assertEquals(7, verified.manifest.revision)
        assertEquals("2026.09.13-public.7", verified.pack.version)
        assertEquals(105, verified.pack.entries.size)
    }

    @Test fun `데이터 한 바이트가 바뀌면 거부한다`() {
        val pack = resource(PublicKnowledgePackCodec.ASSET_NAME)
        pack[pack.lastIndex - 2] = (pack[pack.lastIndex - 2].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgePackSecurity.verify(resource(KnowledgePackSecurity.MANIFEST_ASSET_NAME), pack)
        }
    }

    @Test fun `리비전만 높인 위조 매니페스트도 거부한다`() {
        val changed = resource(KnowledgePackSecurity.MANIFEST_ASSET_NAME)
            .toString(Charsets.UTF_8).replace("\"revision\": 7", "\"revision\": 8")
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgePackSecurity.verify(changed.toByteArray(), resource(PublicKnowledgePackCodec.ASSET_NAME))
        }
    }

    @Test fun `고정 GitHub 주소가 아닌 데이터 팩은 거부한다`() {
        val changed = resource(KnowledgePackSecurity.MANIFEST_ASSET_NAME)
            .toString(Charsets.UTF_8).replace(AppInfo.KNOWLEDGE_PACK_URL, "https://example.invalid/pack.json")
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgePackSecurity.parseManifest(changed.toByteArray())
        }
    }
}
