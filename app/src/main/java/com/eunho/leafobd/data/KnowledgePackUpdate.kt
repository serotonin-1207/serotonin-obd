package com.eunho.leafobd.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class KnowledgePackManifest(
    val revision: Int,
    val packVersion: String,
    val publishedAt: String,
    val minAppVersionCode: Int,
    val packUrl: String,
    val sha256: String,
    val signatureBase64: String
)

data class VerifiedKnowledgePack(
    val manifest: KnowledgePackManifest,
    val pack: PublicKnowledgePack
)

sealed interface KnowledgePackUpdateResult {
    data class Updated(val version: String, val revision: Int, val entryCount: Int) : KnowledgePackUpdateResult
    data object UpToDate : KnowledgePackUpdateResult
    data class AppUpdateRequired(val minVersionCode: Int) : KnowledgePackUpdateResult
    data class Failed(val message: String) : KnowledgePackUpdateResult
}

/** 서명은 리비전·팩 버전·해시와 원본 바이트를 함께 묶어 이전 팩 위장과 변조를 막는다. */
object KnowledgePackSecurity {
    const val MANIFEST_ASSET_NAME = "diagnostic_knowledge_pack_manifest.json"
    const val MAX_MANIFEST_BYTES = 16 * 1024
    private const val ALGORITHM = "SHA256withECDSA"

    fun parseManifest(bytes: ByteArray): KnowledgePackManifest {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MANIFEST_BYTES) { "데이터 팩 정보 파일 크기가 잘못되었습니다." }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("schemaVersion") == 1) { "지원하지 않는 데이터 팩 정보 형식입니다." }
        val value = KnowledgePackManifest(
            revision = root.getInt("revision"),
            packVersion = root.required("packVersion", 40),
            publishedAt = root.required("publishedAt", 10),
            minAppVersionCode = root.getInt("minAppVersionCode"),
            packUrl = root.required("packUrl", 1_000),
            sha256 = root.required("sha256", 64).lowercase(),
            signatureBase64 = root.required("signatureBase64", 256)
        )
        require(root.required("signatureAlgorithm", 40) == ALGORITHM) { "지원하지 않는 서명 방식입니다." }
        require(value.revision > 0 && value.minAppVersionCode > 0) { "데이터 팩 버전 번호가 잘못되었습니다." }
        require(Regex("\\d{4}\\.\\d{2}\\.\\d{2}[-a-z0-9.]*").matches(value.packVersion)) { "데이터 팩 버전 형식이 잘못되었습니다." }
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value.publishedAt)) { "데이터 팩 발행일 형식이 잘못되었습니다." }
        require(Regex("[0-9a-f]{64}").matches(value.sha256)) { "데이터 팩 해시 형식이 잘못되었습니다." }
        require(value.packUrl == AppInfo.KNOWLEDGE_PACK_URL) { "허용되지 않은 데이터 팩 주소입니다." }
        return value
    }

    fun verify(manifestBytes: ByteArray, packBytes: ByteArray): VerifiedKnowledgePack {
        val manifest = parseManifest(manifestBytes)
        require(packBytes.isNotEmpty() && packBytes.size <= PublicKnowledgePackCodec.MAX_BYTES) { "데이터 팩 크기가 허용 범위를 벗어났습니다." }
        val digest = MessageDigest.getInstance("SHA-256").digest(packBytes)
        val expected = manifest.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        require(MessageDigest.isEqual(digest, expected)) { "데이터 팩 해시가 일치하지 않습니다." }

        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(AppInfo.KNOWLEDGE_PACK_PUBLIC_KEY_BASE64))
        )
        val verifier = Signature.getInstance(ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(signaturePayload(manifest, packBytes))
        require(verifier.verify(Base64.getDecoder().decode(manifest.signatureBase64))) { "데이터 팩 서명이 올바르지 않습니다." }

        val pack = PublicKnowledgePackCodec.decode(packBytes)
        require(pack.version == manifest.packVersion) { "서명 정보와 데이터 팩 버전이 다릅니다." }
        require(pack.publishedAt <= manifest.publishedAt) { "데이터 팩 발행일이 서명 정보보다 늦습니다." }
        return VerifiedKnowledgePack(manifest, pack)
    }

    fun signaturePayload(manifest: KnowledgePackManifest, packBytes: ByteArray): ByteArray {
        val header = "leafobd-knowledge-pack-v1\n${manifest.revision}\n${manifest.packVersion}\n${manifest.sha256}\n"
            .toByteArray(Charsets.UTF_8)
        return header + packBytes
    }

    private fun JSONObject.required(name: String, maxLength: Int): String =
        getString(name).trim().also { require(it.isNotEmpty() && it.length <= maxLength) { "$name 값이 없거나 너무 깁니다." } }
}

/** 앱 전용 no-backup 저장소에 버전별 파일을 쓴 뒤 포인터를 마지막에 교체한다. */
class KnowledgePackStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "knowledge-pack")

    fun load(): VerifiedKnowledgePack? = runCatching {
        val revisionText = readLimited(File(directory, ACTIVE_FILE), 20).toString(Charsets.UTF_8).trim()
        val revision = revisionText.toInt().also { require(it > 0) }
        val manifest = readLimited(manifestFile(revision), KnowledgePackSecurity.MAX_MANIFEST_BYTES)
        val pack = readLimited(packFile(revision), PublicKnowledgePackCodec.MAX_BYTES)
        KnowledgePackSecurity.verify(manifest, pack).also { require(it.manifest.revision == revision) }
    }.getOrNull()

    fun save(manifestBytes: ByteArray, packBytes: ByteArray): VerifiedKnowledgePack {
        val verified = KnowledgePackSecurity.verify(manifestBytes, packBytes)
        directory.mkdirs()
        require(directory.isDirectory) { "데이터 팩 저장 폴더를 만들지 못했습니다." }
        val revision = verified.manifest.revision
        writeAtomic(manifestFile(revision), manifestBytes)
        writeAtomic(packFile(revision), packBytes)
        // 두 본문 파일이 완전히 기록된 뒤 활성 포인터를 바꾼다. 실패하면 이전 포인터가 유지된다.
        writeAtomic(File(directory, ACTIVE_FILE), "$revision\n".toByteArray())
        return verified
    }

    private fun manifestFile(revision: Int) = File(directory, "manifest-$revision.json")
    private fun packFile(revision: Int) = File(directory, "pack-$revision.json")

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val temp = File(directory, ".${target.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readLimited(file: File, maxBytes: Int): ByteArray {
        require(file.isFile && file.length() in 1..maxBytes.toLong()) { "저장된 데이터 팩 파일 크기가 잘못되었습니다." }
        return file.inputStream().use { it.readBytes() }
    }

    private companion object { const val ACTIVE_FILE = "active-revision" }
}

/** 수동 요청 때만 GitHub의 고정 주소 두 곳을 읽는다. 요청 본문이나 차량 데이터는 보내지 않는다. */
class KnowledgePackUpdater(context: Context) {
    private val store = KnowledgePackStore(context)

    suspend fun update(currentRevision: Int, currentAppVersionCode: Int): KnowledgePackUpdateResult =
        withContext(Dispatchers.IO) {
            try {
                val manifestBytes = fetch(AppInfo.KNOWLEDGE_PACK_MANIFEST_URL, KnowledgePackSecurity.MAX_MANIFEST_BYTES)
                val manifest = KnowledgePackSecurity.parseManifest(manifestBytes)
                if (manifest.minAppVersionCode > currentAppVersionCode) {
                    return@withContext KnowledgePackUpdateResult.AppUpdateRequired(manifest.minAppVersionCode)
                }
                if (manifest.revision <= currentRevision) return@withContext KnowledgePackUpdateResult.UpToDate
                val packBytes = fetch(manifest.packUrl, PublicKnowledgePackCodec.MAX_BYTES)
                val installed = store.save(manifestBytes, packBytes)
                DiagnosticKnowledge.activateDownloaded(installed)
                KnowledgePackUpdateResult.Updated(
                    installed.pack.version,
                    installed.manifest.revision,
                    installed.pack.entries.size
                )
            } catch (e: Exception) {
                KnowledgePackUpdateResult.Failed("오류코드 데이터를 업데이트하지 못했습니다: ${e.message ?: "네트워크 오류"}")
            }
        }

    private fun fetch(urlString: String, maxBytes: Int): ByteArray {
        require(urlString == AppInfo.KNOWLEDGE_PACK_MANIFEST_URL || urlString == AppInfo.KNOWLEDGE_PACK_URL) {
            "허용되지 않은 업데이트 주소입니다."
        }
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            val declared = connection.contentLengthLong
            require(declared == -1L || declared in 1..maxBytes.toLong()) { "다운로드 파일 크기가 허용 범위를 벗어났습니다." }
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "다운로드 파일이 너무 큽니다." }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
