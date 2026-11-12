package com.wangzan101.mika.core.skills

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable
enum class ImportStatus { ready, adapted, needs_setup, unsupported }

@Serializable
data class SkillImportInfo(
    val repository: String,
    val revision: String,
    val path: String,
    val status: ImportStatus,
    val substitutions: List<String> = emptyList(),
    val requirements: List<String> = emptyList(),
    val requiredTools: List<String> = emptyList(),
    val capabilityHash: String = "",
    val adaptedHash: String = "",
) {
    val usable: Boolean get() = status == ImportStatus.ready || status == ImportStatus.adapted
    companion object { val json = Json { ignoreUnknownKeys = true } }
}

/** Resources are base64 encoded so text, templates and binary assets survive storage unchanged. */
object SkillResources {
    const val MAX_FILES = 256
    const val MAX_BYTES = 8 * 1024 * 1024
    fun safe(path: String): Boolean = path.isNotBlank() && path.length <= 240 &&
        !path.startsWith('/') && !path.contains('\\') && !path.contains(':') &&
        path.split('/').none { it.isEmpty() || it == "." || it == ".." } && path.none { it.code < 32 }
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}

data class ImportCapability(val name: String, val description: String)
data class ImportItem(val name: String, val display: String, val status: ImportStatus,
    val substitutions: List<String>, val requirements: List<String>, val saved: Boolean = false,
    val error: String? = null)
data class ImportPreview(val planId: String, val repository: String, val revision: String, val items: List<ImportItem>)
data class ImportReport(val repository: String, val items: List<ImportItem>)

interface SkillImports {
    suspend fun prepare(url: String, capabilities: List<ImportCapability>): ImportPreview
    suspend fun install(planId: String): ImportReport
}

class SkillImportException(val code: String, message: String) : Exception(message)
