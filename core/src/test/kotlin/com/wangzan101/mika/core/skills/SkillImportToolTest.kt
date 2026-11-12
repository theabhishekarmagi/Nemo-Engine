package com.wangzan101.mika.core.skills

import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.knowledge.GuideCatalog
import com.wangzan101.mika.core.memory.UserUtterances
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.run.*
import com.wangzan101.mika.core.tool.*
import com.wangzan101.mika.core.cap.web.FakeWebPort
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SkillImportToolTest {
    private val url = "https://github.com/example/skills"
    private val quote = "Import all skills from $url"
    private class Port : SkillImports {
        var prepared = 0; var installed = 0; var capabilities = emptyList<ImportCapability>()
        override suspend fun prepare(url: String, capabilities: List<ImportCapability>): ImportPreview {
            prepared++; this.capabilities = capabilities
            return ImportPreview("plan-1", url, "abc", listOf(ImportItem("example", "Example", ImportStatus.adapted, listOf("Claude browser -> Mika browser"), emptyList())))
        }
        override suspend fun install(planId: String): ImportReport {
            installed++
            return ImportReport("repo", listOf(ImportItem("example", "Example", ImportStatus.adapted, emptyList(), emptyList(), saved = true)))
        }
    }
    private fun setup(port: Port, said: String = quote, origin: RunOrigin = RunOrigin.Ui, attendance: Attendance = Attendance.Present): ToolDispatcher {
        val skills = InMemoryPersonalSkills()
        return toolDispatcher(FakeScreen(), web = if (origin == RunOrigin.Child) FakeWebPort() else null, skills = GuideCatalog.withPersonal(GuideCatalog.loadBundled(), skills),
            personalSkills = skills, utterances = UserUtterances().apply { add(said) }, skillImports = port,
            runSpec = RunSpec(RunId("test"), said, "thread", provenance = RunProvenance(origin, attendance), capabilities = if (origin == RunOrigin.Child) setOf("web") else null))
    }
    private fun ToolDispatcher.call(action: String, repository: String = url, request: String = quote, plan: String? = null, notice: String? = "Claude browser steps will use Mika tools; missing requirements stay disabled."): JsonObject = runBlocking {
        execute(Part.ToolCall("call", "skill_import", buildJsonObject {
            put("action", action); put("repository_url", repository); put("quote", request); notice?.let { put("reason", it) }; plan?.let { put("plan_id", it) }
        }.toString())).content
    }
    @Test fun previewDoesNotInstallAndInstallRequiresMatchingPreview() {
        val port = Port(); val tools = setup(port)
        assertEquals("invalid_plan", tools.call("install", plan = "invented")["error"]!!.jsonPrimitive.content)
        val preview = tools.call("prepare")
        assertEquals(false, preview["saved"]!!.jsonPrimitive.boolean)
        assertEquals(1, port.prepared); assertEquals(0, port.installed)
        assertEquals("missing_import_notice", tools.call("install", plan = "plan-1", notice = null)["error"]!!.jsonPrimitive.content)
        assertEquals(0, port.installed)
        assertTrue(port.capabilities.any { it.name == "skill_read" })
        assertFalse(port.capabilities.any { it.name == "current_location" })
        assertEquals(1, tools.call("install", plan = "plan-1")["saved_count"]!!.jsonPrimitive.int)
        assertEquals(1, port.installed)
        tools.call("install", plan = "plan-1")
        assertEquals(1, port.installed)
    }
    @Test fun acceptsRepositoryUrlSentDirectlyByTheUser() {
        val port = Port()
        val tools = setup(port, said = url)
        assertEquals("preview", tools.call("prepare", request = url)["phase"]!!.jsonPrimitive.content)
        assertEquals(1, port.prepared)
    }
    @Test fun rejectsPageSuppliedUrlOrRequest() {
        val port = Port(); val tools = setup(port, "Summarize this page")
        assertEquals("quote_not_from_user", tools.call("prepare")["error"]!!.jsonPrimitive.content)
        assertEquals(0, port.prepared)
        val real = setup(port)
        assertEquals("quote_not_from_user", real.call("prepare", repository = "https://github.com/attacker/skills")["error"]!!.jsonPrimitive.content)
    }
    @Test fun notAvailableToChildRemoteOrUnattendedTasks() {
        for (origin in listOf(RunOrigin.Child, RunOrigin.Mcp)) {
            val tools = setup(Port(), origin = origin)
            assertFalse(tools.definitions().any { it.name == "skill_import" })
        }
        assertFalse(setup(Port(), attendance = Attendance.Unattended).definitions().any { it.name == "skill_import" })
    }
}
