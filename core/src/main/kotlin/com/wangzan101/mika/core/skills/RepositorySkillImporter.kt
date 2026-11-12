package com.wangzan101.mika.core.skills

import com.wangzan101.mika.core.knowledge.FrontMatter
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

/** Preparation is read-only. Installation consumes a preview only after the agent explains it to the user. */
class RepositorySkillImporter(
    private val source: SkillRepositorySource,
    private val adapter: RepositorySkillAdapter,
    private val store: PersonalSkills,
    private val clock: () -> Long = System::currentTimeMillis,
    private val reservedNames: Set<String> = emptySet(),
) : SkillImports {
    private data class Entry(val skill: PersonalSkill, val baseline: PersonalSkill?)
    private data class Plan(val repository: String, val entries: List<Entry>)
    private val plans = linkedMapOf<String, Plan>()
    private val lock = Mutex()

    override suspend fun prepare(url: String, capabilities: List<ImportCapability>): ImportPreview {
        val snapshot = source.fetch(url)
        val capabilityHash = PersonalSkills.fingerprint(capabilities.sortedBy { it.name }.joinToString("\n") { "${it.name}:${it.description}" })
        val tools = capabilities.map { it.name }.toSet()
        val gate = Semaphore(3)
        val entries = coroutineScope {
            snapshot.skills.map { original -> async { gate.withPermit {
                val name = stableName(snapshot.repository, original.path)
                val baseline = store.get(name)
                val result = try {
                    original.issue?.let { unavailable(name, original.path, it) }
                        ?: checked(adapter.adapt(original, name, capabilities), name, tools, original.resources.keys)
                } catch (_: TimeoutCancellationException) { unavailable(name, original.path, "Adaptation timed out. Re-import to retry.") }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { unavailable(name, original.path, "Could not adapt this skill. Re-import the repository to retry.") }
                val md = PersonalSkills.withField(result.markdown, "source", "imported")
                val info = SkillImportInfo(snapshot.repository, snapshot.revision, original.path, result.status,
                    result.substitutions, result.requirements, result.requiredTools, capabilityHash, PersonalSkills.fingerprint(md))
                Entry(PersonalSkill(name, md, if (info.usable) SkillStatus.trial else SkillStatus.suspended,
                    null, null, baseline?.createdAt ?: clock(), emptyList(), source = "imported", importInfo = info, resources = original.resources), baseline)
            } } }.awaitAll()
        }
        val id = UUID.randomUUID().toString()
        lock.withLock {
            // Bound preparation memory; an old preview must be regenerated rather than silently reused.
            plans.clear()
            plans[id] = Plan(snapshot.repository, entries)
        }
        return ImportPreview(id, snapshot.repository, snapshot.revision, entries.map { item(it.skill) })
    }

    override suspend fun install(planId: String): ImportReport = lock.withLock {
        val plan = plans.remove(planId) ?: throw SkillImportException("import_plan_expired", "The import preview expired. Prepare the repository again.")
        val items = plan.entries.map { entry ->
            currentCoroutineContext().ensureActive()
            val skill = entry.skill
            val current = store.get(skill.name)
            val info = skill.importInfo!!
            val unchangedImport = current?.importInfo?.let { it.repository == info.repository && it.path == info.path &&
                it.revision == info.revision && it.capabilityHash == info.capabilityHash &&
                it.status == info.status && it.substitutions == info.substitutions && it.requirements == info.requirements &&
                current.markdown == skill.markdown && current.resources == skill.resources } == true
            val error = when {
                skill.name in reservedNames || store.pending().any { it.name == skill.name } -> "A built-in skill or pending draft already uses this name."
                current != entry.baseline -> "This skill changed after the preview. Re-import to review the current version."
                current != null && current.importInfo == null -> "An existing personal skill uses this name; it was not overwritten."
                current != null && PersonalSkills.fingerprint(current.markdown) != current.importInfo?.adaptedHash -> "This imported skill has local edits; they were not overwritten."
                else -> null
            }
            if (error != null) item(skill).copy(error = error)
            else if (unchangedImport) item(current!!).copy(saved = true)
            else try {
                store.put(skill)
                val saved = store.get(skill.name)
                if (saved != skill) item(skill).copy(error = "Could not verify the saved skill.")
                else item(skill).copy(saved = true)
            } catch (_: Exception) { item(skill).copy(error = "Could not save this skill. Other import results are listed separately.") }
        }
        ImportReport(plan.repository, items)
    }

    private fun checked(a: SkillAdaptation, name: String, tools: Set<String>, resources: Set<String>): SkillAdaptation {
        val md = PersonalSkills.withName(SkillDrafter.stripFence(a.markdown), name)
        val meta = FrontMatter.parse(md).first
        val display = meta["display"] as? String
        val description = meta["description"] as? String
        if (display.isNullOrBlank() || description.isNullOrBlank() || SkillEnglish.needsTranslation(display) || SkillEnglish.needsTranslation(description) ||
            SkillDrafter.validate(md, tools, "run_bundled_script") != null)
            return unavailable(name, name, "The adapted skill failed format or capability checks. Re-import to retry.")
        val mentionedTools = Regex("`([a-z][a-z0-9_]*)`").findAll(md).map { it.groupValues[1] }.toList()
        val requiredTools = (a.requiredTools + mentionedTools).distinct()
        val missingTools = requiredTools.filter { it !in tools }
        val missingResources = a.usedResources.filter { it !in resources }
        val scripts = a.usedResources.filter { it.substringAfterLast('.').lowercase() in setOf("py", "js", "mjs", "sh", "exe", "bin") }
        val binaryAssets = a.usedResources.filter { it.substringAfterLast('.').lowercase() in setOf("pdf", "docx", "xlsx", "pptx", "png", "jpg", "jpeg", "webp", "mp4", "mp3", "zip") }
        val requirements = (a.requirements + binaryAssets.map { "Binary asset processing needs an integration: $it" } + missingTools.map { "Unavailable tool: $it" } + missingResources.map { "Missing resource: $it" } +
            scripts.map { "Script execution needs setup: $it" }).distinct()
        val status = when {
            a.status == ImportStatus.unsupported -> ImportStatus.unsupported
            requirements.isNotEmpty() || a.status == ImportStatus.needs_setup -> ImportStatus.needs_setup
            a.substitutions.isNotEmpty() -> ImportStatus.adapted
            else -> a.status
        }
        return a.copy(markdown = md, status = status,
            requirements = if (status == ImportStatus.needs_setup && requirements.isEmpty()) listOf("Compatibility setup needs review. Re-import after connecting the required service.") else requirements,
            requiredTools = requiredTools)
    }

    private fun unavailable(name: String, path: String, reason: String): SkillAdaptation {
        val title = path.substringBeforeLast('/').substringAfterLast('/').ifBlank { "Imported skill" }.replace(Regex("[^A-Za-z0-9 -]"), " ").take(80).ifBlank { "Imported skill" }
        return SkillAdaptation("""
            ---
            name: $name
            display: $title
            description: Imported workflow awaiting compatibility setup.
            aliases: []
            ---
            ## Entry
            Review the original skill in original/SKILL.md.
            ## Steps
            This workflow is disabled until it can be adapted.
            ## Common pitfalls
            $reason
            ## Verification
            No successful test has been performed.
            ## When to hand over
            Resolve the listed requirements before using this workflow.
        """.trimIndent(), ImportStatus.needs_setup, emptyList(), listOf(reason), emptyList(), emptyList())
    }

    companion object {
        fun stableName(repository: String, path: String): String {
            val slug = path.substringBeforeLast('/', "skill").substringAfterLast('/').lowercase()
                .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).trimEnd('-').ifBlank { "skill" }
            return "import-$slug-${PersonalSkills.fingerprint("$repository/$path").take(12)}"
        }
        fun item(s: PersonalSkill): ImportItem {
            val info = s.importInfo!!
            val display = FrontMatter.parse(s.markdown).first["display"] as? String ?: s.name
            return ImportItem(s.name, display, info.status, info.substitutions, info.requirements)
        }
    }
}
