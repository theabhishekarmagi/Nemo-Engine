package com.wangzan101.mika.ui

import com.wangzan101.mika.core.skills.PersonalSkill
import com.wangzan101.mika.core.skills.SkillStatus
import com.wangzan101.mika.i18n.ChineseTexts
import com.wangzan101.mika.settings.MemorySettings
import com.wangzan101.mika.task.MapSkills
import com.wangzan101.mika.task.memoryStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SkillTranslationTest {
    @get:Rule val texts = ChineseTexts()
    @get:Rule val tmp = TemporaryFolder()
    private val original = """
        ---
        name: nearby-snacks
        display: 查附近小吃
        description: 查附近的小吃价格
        source: learned
        learned_from: run-1
        ---
        ## 入口
        地图
        ## 步骤
        搜索小吃
        ## 容易出错的地方
        营业时间
        ## 怎样算办成
        三个选择
        ## 什么时候停下交给用户
        付款前
    """.trimIndent()
    private val english = """
        ---
        name: nearby-snacks
        display: Find nearby snacks
        description: Find nearby snacks and compare prices
        source: learned
        learned_from: run-1
        ---
        ## Entry
        Open Maps
        ## Steps
        Search for snacks
        ## Common pitfalls
        Check opening hours
        ## Verification
        Show three choices
        ## When to hand over
        Stop before payment
    """.trimIndent()
    private val draft = PersonalSkill("nearby-snacks", original, SkillStatus.pending, "run-1", null, 7, emptyList())

    @Test fun translatesPendingWithoutApprovingAndRejectsDuplicateRequests() = runTest {
        val skills = MapSkills().apply { putPending("d1", draft) }
        val result = CompletableDeferred<String?>()
        var calls = 0
        val vm = MemoryViewModel(memoryStore(), MemorySettings(File(tmp.root, "s.json")), skills, backgroundScope,
            translateSkill = { calls++; result.await() }, translationDispatcher = StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.translateSkill("d1", true); vm.translateSkill("d1", true); runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.ui.value.translatingSkills.isNotEmpty())
        result.complete(english); runCurrent()
        assertEquals(draft.copy(markdown = english), skills.drafts()["d1"])
        assertTrue(skills.list().isEmpty())
        assertEquals("Find nearby snacks", vm.ui.value.skills.drafts.single().display)
        assertTrue(vm.ui.value.translatingSkills.isEmpty())
    }

    @Test fun failedTranslationPreservesOriginalAndCanRetry() = runTest {
        val skills = MapSkills().apply { putPending("d1", draft) }
        val vm = MemoryViewModel(memoryStore(), MemorySettings(File(tmp.root, "s.json")), skills, backgroundScope,
            translateSkill = { "invalid" }, translationDispatcher = StandardTestDispatcher(testScheduler))
        vm.translateSkill("d1", true); runCurrent()
        assertEquals(draft, skills.drafts()["d1"])
        assertNotNull(vm.ui.value.message)
        assertTrue(vm.ui.value.translatingSkills.isEmpty())
    }

    @Test fun translationCannotRestoreDeletedDraftOrOverwriteEdits() = runTest {
        for (delete in listOf(false, true)) {
            val skills = MapSkills().apply { putPending("d1", draft) }
            val result = CompletableDeferred<String?>()
            val vm = MemoryViewModel(memoryStore(), MemorySettings(File(tmp.root, "s-$delete.json")), skills, backgroundScope,
                translateSkill = { result.await() }, translationDispatcher = StandardTestDispatcher(testScheduler))
            vm.translateSkill("d1", true); runCurrent()
            val edited = draft.copy(markdown = original + "\nUser edit")
            if (delete) skills.reject("d1") else skills.putPending("d1", edited)
            result.complete(english); runCurrent()
            assertEquals(if (delete) null else edited, skills.drafts()["d1"])
        }
    }

    @Test fun installedTranslationKeepsStatusAndOutcomes() = runTest {
        val installed = draft.copy(status = SkillStatus.suspended, outcomes = listOf("verified"))
        val skills = MapSkills(installed)
        val vm = MemoryViewModel(memoryStore(), MemorySettings(File(tmp.root, "s.json")), skills, backgroundScope,
            translateSkill = { english }, translationDispatcher = StandardTestDispatcher(testScheduler))
        vm.translateSkill(installed.name, false); runCurrent()
        assertEquals(installed.copy(markdown = english), skills.get(installed.name))
    }
}
