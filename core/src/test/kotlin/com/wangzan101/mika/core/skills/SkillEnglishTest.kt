package com.wangzan101.mika.core.skills

import com.wangzan101.mika.core.agent.FakeModel
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.StopReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SkillEnglishTest {
    private val original = """
        ---
        name: nearby-food
        display: 查附近小吃与价格
        description: 用户想找附近的小吃时使用
        source: learned
        learned_from: run-123
        apps: [com.google.android.apps.maps]
        ---
        ## 入口
        打开地图
        ## 步骤
        用 `current_location` 查位置
        ## 容易出错的地方
        核对营业状态
        ## 怎样算办成
        显示附近小吃
        ## 什么时候停下交给用户
        付款前停下
    """.trimIndent()
    private val english = original.replace("查附近小吃与价格", "Find nearby snacks and prices")
        .replace("用户想找附近的小吃时使用", "Use when looking for nearby snacks")
        .replace("## 入口", "## Entry").replace("## 步骤", "## Steps")
        .replace("## 容易出错的地方", "## Common pitfalls").replace("## 怎样算办成", "## Verification")
        .replace("## 什么时候停下交给用户", "## When to hand over")
        .replace("打开地图", "Open Maps").replace("用 `current_location` 查位置", "Use `current_location` for the location")
        .replace("核对营业状态", "Check opening hours").replace("显示附近小吃", "Show nearby snacks")
        .replace("付款前停下", "Stop before payment")

    private fun translate(reply: String) = runBlocking {
        SkillEnglish.translate(FakeModel(ModelReply(listOf(Part.Text(reply)), StopReason.EndTurn, null)), original)
    }

    @Test fun translatesScreenshotDraftWithoutChangingIdentity() {
        assertTrue(SkillEnglish.needsTranslation(original))
        assertEquals(english, translate(english))
        assertFalse(SkillEnglish.needsTranslation(english))
        assertNull(SkillDrafter.validate(english, setOf("current_location"), null))
        assertNull(SkillDrafter.validate(original, setOf("current_location"), null))
    }

    @Test fun rejectsChangedIdentityProvenanceOrTools() {
        for ((from, to) in listOf("nearby-food" to "another-skill", "run-123" to "run-456", "source: learned" to "source: user", "current_location" to "run_command")) {
            assertNull("Changed $from", translate(english.replace(from, to)))
        }
        assertNull(translate(original))
    }

    @Test fun rejectsIncompleteEnglishSkill() {
        assertEquals("missing_section", SkillDrafter.validate(english.replace("## Verification", "## Notes"), setOf("current_location"), null))
    }
}
