package org.jetbrains.dokka.it.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.runners.Parameterized.Parameters
import java.io.File
import kotlin.test.*

class GradleRelocatedCachingIntegrationTest(override val versions: BuildVersions) : AbstractGradleCachingIntegrationTest(versions) {

    companion object {
        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val versions = BuildVersions.permutations(
            gradleVersions = listOf("7.0", *ifExhaustive("6.6", "6.1.1")),
            kotlinVersions = listOf("1.3.30", *ifExhaustive("1.3.72", "1.4.32"), "1.5.0")
        ) + BuildVersions.permutations(
            gradleVersions = listOf("5.6.4", "6.0"),
            kotlinVersions = listOf("1.3.30", *ifExhaustive("1.4.32"))
        )
    }

    @BeforeTest
    fun prepareProjectFiles() {
        setupProject(projectFolder(1))
        setupProject(projectFolder(2))
    }

    @Test
    fun execute() {
        runAndAssertOutcome(projectFolder(1), TaskOutcome.SUCCESS)
        runAndAssertOutcome(projectFolder(2), TaskOutcome.FROM_CACHE)
    }

    private fun runAndAssertOutcome(project: File, expectedOutcome: TaskOutcome) {
        val result = createGradleRunner("clean", "dokkaHtml", "-i", "-s", "-Dorg.gradle.caching.debug=true", "--build-cache")
            .withProjectDir(project)
            .buildRelaxed()

        assertEquals(expectedOutcome, assertNotNull(result.task(":dokkaHtml")).outcome)

        File(project, "build/dokka/html").assertHtmlOutputDir()
    }

    private fun projectFolder(index: Int) = File(projectDir.absolutePath + index)
}
