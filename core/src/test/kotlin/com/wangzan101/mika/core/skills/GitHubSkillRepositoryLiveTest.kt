package com.wangzan101.mika.core.skills

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in network smoke test: NEMO_SKILL_IMPORT_NETWORK_TEST=1 ./gradlew :core:test --tests '*GitHubSkillRepositoryLiveTest'. */
class GitHubSkillRepositoryLiveTest {
    @Test fun downloadsAnImmutablePublicRepositoryAndDiscoversItsSkills() {
        assumeTrue(System.getenv("NEMO_SKILL_IMPORT_NETWORK_TEST") == "1")
        runBlocking {
            val snapshot = GitHubSkillRepository().fetch("https://github.com/anthropics/skills")
            assertTrue(snapshot.skills.size > 1)
            assertTrue(Regex("[0-9a-f]{40}").matches(snapshot.revision))
            assertTrue(snapshot.skills.all { "original/SKILL.md" in it.resources })
            assertTrue(snapshot.skills.any { s -> s.resources.keys.any { it.contains("LICENSE", ignoreCase = true) } })
            println("Public repository smoke test: ${snapshot.skills.size} skills at ${snapshot.revision}")
        }
    }
}
