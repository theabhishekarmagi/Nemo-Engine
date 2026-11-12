package com.wangzan101.mika.core.skills

import com.wangzan101.mika.core.knowledge.GuideCatalog
import com.wangzan101.mika.core.knowledge.GuideView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RepositorySkillImporterTest {
    private val repo = "https://github.com/example/workflows"
    private val caps = listOf(ImportCapability("web_read", "Read a web page"))
    private val resources = mapOf("original/SKILL.md" to SkillResources.encode("upstream source".toByteArray()),
        "files/references/checklist.md" to SkillResources.encode("Check source evidence".toByteArray()),
        "upstream/LICENSE" to SkillResources.encode("Upstream copyright notice".toByteArray()))
    private fun source(vararg names: String) = SkillRepositorySource { SkillRepositorySnapshot(repo, "a".repeat(40),
        names.map { RepositorySkill("skills/$it/SKILL.md", "---\nname: $it\n---\nOriginal workflow", resources) }) }
    private fun doc(name: String) = """
        ---
        name: $name
        display: Research checklist
        description: Use for source-based research
        aliases: [research]
        ---
        ## Entry
        Open the user-specified source.
        ## Steps
        Use `web_read` to read source evidence.
        ## Common pitfalls
        Check dates.
        ## Verification
        Provide source links.
        ## When to hand over
        Stop when login or payment is required.
    """.trimIndent()
    private val adapter = RepositorySkillAdapter { _, name, _ -> SkillAdaptation(doc(name), ImportStatus.adapted,
        listOf("Claude browser -> Mika web reader"), emptyList(), listOf("web_read"), listOf("files/references/checklist.md")) }

    @Test fun previewsThenImportsEverySkillAndRetainsResources() = runBlocking {
        val store = InMemoryPersonalSkills()
        val importer = RepositorySkillImporter(source("research", "food", "writing"), adapter, store)
        val preview = importer.prepare(repo, caps)
        assertEquals(3, preview.items.size)
        assertTrue(store.list().isEmpty())
        val report = importer.install(preview.planId)
        assertTrue(report.items.all { it.saved && it.status == ImportStatus.adapted })
        assertEquals(3, store.list().size)
        assertTrue(store.list().all { it.status == SkillStatus.trial && it.resources == resources && it.importInfo!!.revision == "a".repeat(40) })
        val catalog = GuideCatalog.withPersonal(GuideCatalog.loadBundled(), store)
        val name = store.list().first().name
        assertTrue((catalog.view("personal:$name") as GuideView.Found).content.contains("Before using this workflow"))
        assertEquals(true, (catalog.view("personal:$name", "upstream/LICENSE") as GuideView.Found).content.contains("Upstream copyright"))
    }

    @Test fun missingToolsScriptsAndFailedAdaptersAreSavedDisabled() = runBlocking {
        val store = InMemoryPersonalSkills()
        val mixed = RepositorySkillAdapter { original, name, _ -> when {
            "/missing/" in original.path -> SkillAdaptation(doc(name), ImportStatus.ready, emptyList(), emptyList(), listOf("unavailable_api"), emptyList())
            "/scripts/" in original.path -> SkillAdaptation(doc(name), ImportStatus.ready, emptyList(), emptyList(), emptyList(), listOf("files/scripts/create.py"))
            "/bad/" in original.path -> throw IllegalStateException("provider error")
            "/unsupported/" in original.path -> SkillAdaptation(doc(name), ImportStatus.unsupported, emptyList(), listOf("Desktop application unavailable"), emptyList(), emptyList())
            else -> adapter.adapt(original, name, caps)
        } }
        val importer = RepositorySkillImporter(source("good", "missing", "scripts", "bad", "unsupported"), mixed, store)
        val report = importer.install(importer.prepare(repo, caps).planId)
        assertEquals(5, report.items.count { it.saved })
        assertEquals(4, store.list().count { it.status == SkillStatus.suspended })
        val blocked = store.list().first { it.importInfo!!.path.contains("/missing/") }
        store.put(blocked.copy(status = SkillStatus.active))
        val catalog = GuideCatalog.withPersonal(GuideCatalog.loadBundled(), store)
        assertTrue(catalog.items.none { it.name == blocked.name })
    }

    @Test fun reimportDoesNotOverwriteLocalEditsOrChangeAfterPreview() = runBlocking {
        val store = InMemoryPersonalSkills()
        val importer = RepositorySkillImporter(source("one", "two"), adapter, store)
        importer.install(importer.prepare(repo, caps).planId)
        val one = store.list().first()
        store.put(one.copy(markdown = one.markdown + "\nLocal edit"))
        val preview = importer.prepare(repo, caps)
        val two = store.list().first { it.name != one.name }
        store.put(two.copy(markdown = two.markdown + "\nChanged after preview"))
        val report = importer.install(preview.planId)
        assertTrue(report.items.all { !it.saved && it.error != null })
        assertTrue(store.get(one.name)!!.markdown.endsWith("Local edit"))
        assertTrue(store.get(two.name)!!.markdown.endsWith("Changed after preview"))
    }

    @Test fun reimportIsIdempotentAndNamesDoNotCollideAcrossRepositories() = runBlocking {
        val store = InMemoryPersonalSkills()
        val importer = RepositorySkillImporter(source("one"), adapter, store)
        importer.install(importer.prepare(repo, caps).planId)
        val first = store.list().single()
        store.put(first.copy(status = SkillStatus.active, outcomes = listOf("verified")))
        importer.install(importer.prepare(repo, caps).planId)
        assertEquals(SkillStatus.active, store.list().single().status)
        assertEquals(listOf("verified"), store.list().single().outcomes)
        assertNotEquals(first.name, RepositorySkillImporter.stableName("https://github.com/other/workflows", "skills/one/SKILL.md"))
    }

    private fun zip(files: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out -> files.forEach { (path, data) -> out.putNextEntry(ZipEntry(path)); out.write(data.toByteArray()); out.closeEntry() } }
        return bytes.toByteArray()
    }

    @Test fun archiveDiscoveryIncludesAllSkillsAndAncestorNoticesWithoutCrossContamination() {
        val snapshot = GitHubSkillRepository.snapshot(repo, "abc", zip(mapOf(
            "root/LICENSE" to "root license", "root/NOTICE" to "root notice",
            "root/skills/a/SKILL.md" to "skill a", "root/skills/a/LICENSE.txt" to "local license",
            "root/skills/a/references/check.md" to "reference a", "root/skills/a/assets/icon.png" to "binary asset",
            "root/skills/b/SKILL.md" to "skill b", "root/skills/b/scripts/run.py" to "print('b')",
        )))
        assertEquals(2, snapshot.skills.size)
        val a = snapshot.skills.first()
        assertTrue("upstream/LICENSE" in a.resources)
        assertTrue("upstream/NOTICE" in a.resources)
        assertTrue("upstream/skills/a/LICENSE.txt" in a.resources)
        assertTrue("files/references/check.md" in a.resources)
        assertTrue("files/assets/icon.png" in a.resources)
        assertFalse(a.resources.keys.any { it.contains("run.py") })
        assertEquals("skill a", SkillResources.decode(a.resources.getValue("original/SKILL.md")).decodeToString())
    }

    @Test fun rejectsUnsafeArchivePathsAndTooManySkillsWithoutPartialImport() {
        for (path in listOf("root/../escape", "root/a/../../escape", "root/a\\escape")) {
            try { GitHubSkillRepository.snapshot(repo, "abc", zip(mapOf(path to "bad"))); fail(path) }
            catch (e: SkillImportException) { assertEquals("unsafe_archive", e.code) }
        }
        val many = (0..GitHubSkillRepository.MAX_SKILLS).associate { "root/skills/s$it/SKILL.md" to "skill" }
        try { GitHubSkillRepository.snapshot(repo, "abc", zip(many)); fail("must not truncate") }
        catch (e: SkillImportException) { assertEquals("too_many_skills", e.code) }
    }

    @Test fun oversizedInstructionsAreRetainedDisabledWithoutSkippingOtherSkills() = runBlocking {
        val snapshot = GitHubSkillRepository.snapshot(repo, "abc", zip(mapOf(
            "root/skills/large/SKILL.md" to "x".repeat(120_001),
            "root/skills/normal/SKILL.md" to "Normal instructions",
            "root/skills/normal/references/checklist.md" to "Check sources",
        )))
        val store = InMemoryPersonalSkills()
        val importer = RepositorySkillImporter(SkillRepositorySource { snapshot }, adapter, store)
        val report = importer.install(importer.prepare(repo, caps).planId)
        assertEquals(2, report.items.count { it.saved })
        assertEquals(1, store.list().count { it.status == SkillStatus.suspended })
        val large = store.list().single { it.importInfo!!.path.contains("/large/") }
        assertEquals(120_001, SkillResources.decode(large.resources.getValue("original/SKILL.md")).size)
    }

    @Test fun validatesPublicRepositoryUrls() {
        assertEquals(repo, SkillRepositoryRef.parse("$repo.git").url)
        assertEquals("main", SkillRepositoryRef.parse("$repo/tree/main").ref)
        for (url in listOf("http://github.com/a/b", "https://github.com.evil.test/a/b", "https://user:secret@github.com/a/b", "$repo?token=secret", "https://127.0.0.1/a/b", "$repo/tree/main/skills/a")) {
            try { SkillRepositoryRef.parse(url); fail(url) } catch (_: SkillImportException) { }
        }
    }
}
