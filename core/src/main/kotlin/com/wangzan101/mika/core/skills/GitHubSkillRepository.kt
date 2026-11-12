package com.wangzan101.mika.core.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** Public GitHub only. No git commands, redirects to arbitrary hosts, or archive extraction. */
data class SkillRepositoryRef(val owner: String, val repository: String, val ref: String? = null) {
    val url: String get() = "https://github.com/${owner.lowercase()}/${repository.lowercase()}"
    companion object {
        fun parse(url: String): SkillRepositoryRef {
            val u = runCatching { url.toHttpUrl() }.getOrNull()
                ?: throw SkillImportException("invalid_repository", "Use an HTTPS GitHub repository URL.")
            if (u.scheme != "https" || u.host != "github.com" || u.port != 443 || u.username.isNotEmpty() || u.password.isNotEmpty() || u.query != null || u.fragment != null)
                throw SkillImportException("invalid_repository", "Use an HTTPS GitHub repository URL without credentials or query parameters.")
            val segments = u.pathSegments.filter { it.isNotEmpty() }
            if (segments.size < 2 || segments.any { it == "." || it == ".." } ||
                !Regex("[A-Za-z0-9_-]+").matches(segments[0]) || !Regex("[A-Za-z0-9_.-]+").matches(segments[1]))
                throw SkillImportException("invalid_repository", "The repository URL must include its owner and name.")
            val ref = if (segments.size == 2) null else {
                if (segments.size != 4 || segments[2] != "tree") throw SkillImportException("invalid_repository", "Use the repository root URL, or /tree/BRANCH. All skills in that revision will be imported.")
                segments[3]
            }
            return SkillRepositoryRef(segments[0], segments[1].removeSuffix(".git"), ref)
        }
    }
}

data class RepositorySkill(val path: String, val markdown: String, val resources: Map<String, String>, val issue: String? = null)
data class SkillRepositorySnapshot(val repository: String, val revision: String, val skills: List<RepositorySkill>)
fun interface SkillRepositorySource { suspend fun fetch(url: String): SkillRepositorySnapshot }

class GitHubSkillRepository(
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build(),
) : SkillRepositorySource {
    override suspend fun fetch(url: String): SkillRepositorySnapshot = withContext(Dispatchers.IO) {
        val repo = SkillRepositoryRef.parse(url)
        val base = "https://api.github.com/repos/${repo.owner}/${repo.repository}"
        val ref = repo.ref ?: (Json.parseToJsonElement(get(base, 128 * 1024).decodeToString()) as JsonObject)["default_branch"]?.jsonPrimitive?.content
            ?: throw SkillImportException("invalid_repository", "GitHub did not return a default branch.")
        val commitUrl = "$base/commits".toHttpUrl().newBuilder().addPathSegment(ref).build().toString()
        val commit = Json.parseToJsonElement(get(commitUrl, 4 * 1024 * 1024).decodeToString()).jsonObject
        val revision = commit["sha"]?.jsonPrimitive?.content?.takeIf { Regex("[0-9a-f]{40}").matches(it) }
            ?: throw SkillImportException("invalid_revision", "GitHub did not return an immutable commit.")
        val zip = get("https://codeload.github.com/${repo.owner}/${repo.repository}/zip/$revision", MAX_ARCHIVE)
        snapshot(repo.url, revision, zip)
    }

    private suspend fun get(url: String, limit: Int): ByteArray {
        coroutineContext.ensureActive()
        client.newCall(Request.Builder().url(url).header("User-Agent", "Nemo-Engine-skill-import")
            .header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            if (!r.isSuccessful) throw SkillImportException("repository_download_failed", when (r.code) {
                403, 429 -> "GitHub rate limit reached. Try importing again later."
                404 -> "Repository or revision not found. Only public GitHub repositories are supported."
                else -> "GitHub download failed (HTTP ${r.code})."
            })
            val body = r.body ?: throw SkillImportException("repository_download_failed", "GitHub returned an empty response.")
            if (body.contentLength() > limit) tooLarge()
            return body.byteStream().use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > limit) tooLarge()
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        }
    }

    companion object {
        const val MAX_SKILLS = 100
        private const val MAX_ARCHIVE = 32 * 1024 * 1024
        private fun tooLarge(): Nothing = throw SkillImportException("repository_too_large", "Repository exceeds the import size limit; no skills were imported.")

        /** Bounded decompression, all-or-error discovery; never silently imports only a subset. */
        fun snapshot(repository: String, revision: String, zip: ByteArray): SkillRepositorySnapshot {
            val files = linkedMapOf<String, ByteArray>()
            var total = 0
            var entries = 0
            var root: String? = null
            ZipInputStream(ByteArrayInputStream(zip)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (++entries > 8000) tooLarge()
                    val parts = entry.name.split('/', limit = 2)
                    if (parts.size != 2 || !SkillResources.safe(parts[0])) throw SkillImportException("unsafe_archive", "Invalid repository archive paths.")
                    if (root == null) root = parts[0]
                    if (root != parts[0]) throw SkillImportException("unsafe_archive", "Archive contains multiple repository roots.")
                    if (entry.isDirectory) continue
                    val path = parts[1]
                    if (!SkillResources.safe(path) || path in files) throw SkillImportException("unsafe_archive", "Repository contains unsafe or duplicate paths.")
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > 64 * 1024 * 1024 || out.size() + n > SkillResources.MAX_BYTES) tooLarge()
                        out.write(buffer, 0, n)
                    }
                    files[path] = out.toByteArray()
                }
            }
            val mainFiles = files.keys.filter { it.substringAfterLast('/') == "SKILL.md" }.sorted()
            if (mainFiles.isEmpty()) throw SkillImportException("no_skills", "No SKILL.md files were found in this repository.")
            if (mainFiles.size > MAX_SKILLS) throw SkillImportException("too_many_skills", "This repository contains ${mainFiles.size} skills; the current limit is $MAX_SKILLS per import. No skills were imported.")
            val dirs = mainFiles.associateWith { it.substringBeforeLast('/', "") }
            fun under(path: String, dir: String) = dir.isEmpty() || path.startsWith("$dir/")
            val skills = mainFiles.map { main ->
                val dir = dirs.getValue(main)
                val resources = linkedMapOf("original/SKILL.md" to SkillResources.encode(files.getValue(main)))
                for ((path, bytes) in files) {
                    val nearestSkill = dirs.entries.filter { under(path, it.value) }.maxByOrNull { it.value.length }?.key
                    val local = under(path, dir) && nearestSkill == main && path != main
                    val parent = path.substringBeforeLast('/', "")
                    val legal = Regex("(?i)(licen[cs]e|notice|copying|copyright)([.\\-_].*)?").matches(path.substringAfterLast('/')) &&
                        (parent.isEmpty() || parent == dir || dir.startsWith("$parent/"))
                    if (local) resources["files/" + path.removePrefix(if (dir.isEmpty()) "" else "$dir/")] = SkillResources.encode(bytes)
                    if (legal) resources["upstream/$path"] = SkillResources.encode(bytes)
                }
                if (resources.size > SkillResources.MAX_FILES || resources.values.sumOf { SkillResources.decode(it).size.toLong() } > SkillResources.MAX_BYTES) tooLarge()
                val md = files.getValue(main).decodeToString()
                val issue = when {
                    md.length > 120_000 -> "The source instructions exceed the adaptation size limit. The full original is retained for review."
                    '\u0000' in md -> "The source SKILL.md is not a valid text document. The original is retained for review."
                    else -> null
                }
                RepositorySkill(main, md, resources, issue)
            }
            return SkillRepositorySnapshot(repository, revision, skills)
        }
    }
}
