package com.eunho.leafobd.log

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Local labels only; record content fingerprints prevent overwritten files inheriting a label. */
class RecordVehicleGroups(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "record-vehicle-groups.json"))

    private fun read(): MutableMap<String, String> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return mutableMapOf()
        val bytes = file.openRead().use {
            require(it.channel.size() <= 2_000_000) { "차량 분류 파일이 처리 한도를 초과했습니다." }
            it.readBytes()
        }
        require(bytes.size <= 2_000_000)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("version") == 1)
        val labels = root.getJSONObject("labels")
        return labels.keys().asSequence().associateWith { key ->
            require(Regex("[0-9a-f]{64}").matches(key))
            GroupLabel.normalize(labels.getString(key))
        }.toMutableMap()
    }

    private fun fingerprint(file: File): String {
        require(file.length() in 1..SavedWorkshopReport.MAX_BYTES.toLong())
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= SavedWorkshopReport.MAX_BYTES)
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun labels(sessions: List<SavedSession>): Map<String, String> = synchronized(lock) {
        val labels = read()
        sessions.mapNotNull { session ->
            val key = runCatching { fingerprint(session.jsonFile) }.getOrNull()
            labels[key]?.let { session.baseName to it }
        }.toMap()
    }

    fun assign(sessions: List<SavedSession>, label: String?) = synchronized(lock) {
        require(sessions.isNotEmpty())
        val normalized = label?.let(GroupLabel::normalize)
        val keys = sessions.map { fingerprint(it.jsonFile) }
        val labels = read()
        keys.forEach { if (normalized == null) labels.remove(it) else labels[it] = normalized }
        val root = JSONObject().put("version", 1).put("labels", JSONObject(labels as Map<*, *>))
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 2_000_000)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }

    companion object { private val lock = Any() }
}
