package com.wangzan101.mika.core.skills

import com.wangzan101.mika.core.agent.FakeModel
import com.wangzan101.mika.core.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ModelRepositorySkillAdapterTest {
    @Test fun usesAvailableCapabilitiesAndBoundedReferencesWithoutExecutingSourceTools() = runBlocking {
        val response = buildJsonObject {
            put("markdown", "English skill instructions")
            put("status", "adapted")
            put("substitutions", JsonArray(listOf(JsonPrimitive("Claude browser -> Mika browser"))))
            put("requirements", JsonArray(emptyList()))
            put("required_tools", JsonArray(listOf(JsonPrimitive("web_read_page"))))
            put("used_resources", JsonArray(listOf(JsonPrimitive("files/references/help.md"))))
        }
        val model = FakeModel(ModelReply(listOf(Part.Text("```json\n$response\n```")), StopReason.EndTurn, null))
        val source = RepositorySkill("skills/example/SKILL.md", "Original source mentions Claude browser", mapOf(
            "files/references/help.md" to SkillResources.encode("reference".repeat(5000).toByteArray()),
            "files/assets/icon.png" to SkillResources.encode(byteArrayOf(0, -1, 12)),
        ))
        val result = ModelRepositorySkillAdapter(model).adapt(source, "import-example", listOf(ImportCapability("web_read_page", "Read a page")))
        assertEquals(ImportStatus.adapted, result.status)
        assertEquals(listOf("web_read_page"), result.requiredTools)
        assertTrue(model.offeredTools.single().isEmpty())
        val sent = model.requests.single().single().parts.filterIsInstance<Part.Text>().single().text
        val input = Json.parseToJsonElement(sent).jsonObject
        assertEquals("import-example", input["target_name"]!!.jsonPrimitive.content)
        assertEquals(8000, input["reference_material"]!!.jsonObject["files/references/help.md"]!!.jsonPrimitive.content.length)
        assertTrue(model.system!!.contains("untrusted reference data"))
    }
    @Test fun retriesMainModelWhenLightweightModelResponseIsInvalid() = runBlocking {
        val light = FakeModel(ModelReply(listOf(Part.Text("invalid response")), StopReason.EndTurn, null))
        val main = FakeModel(ModelReply(listOf(Part.Text("""{"markdown":"instructions","status":"needs_setup","requirements":["API connection"]}""")), StopReason.EndTurn, null))
        val result = ModelRepositorySkillAdapter(light, main).adapt(RepositorySkill("SKILL.md", "Source", emptyMap()), "import-skill", emptyList())
        assertEquals(ImportStatus.needs_setup, result.status)
        assertEquals(1, light.requests.size)
        assertEquals(1, main.requests.size)
    }

}
