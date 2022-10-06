package org.jetbrains

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate

fun Project.configureDokkaVersion(): String {
    var dokka_version: String? by this.extra
    if (dokka_version == null) {
        val dokka_version_base: String by this
        dokka_version = dokkaVersionFromBase(dokka_version_base)
    }
    return checkNotNull(dokka_version)
}

private fun dokkaVersionFromBase(baseVersion: String): String {
    val buildNumber = System.getenv("BUILD_NUMBER")
    val forceSnapshot = System.getenv("FORCE_SNAPSHOT") != null
    if (forceSnapshot || buildNumber == null) {
        return "$baseVersion-SNAPSHOT"
    }
    return "$baseVersion-$buildNumber"
}

val Project.dokkaVersion: String
    get() = configureDokkaVersion()

// R3: We use version number in the form 1.5.31-1.4-SNAPSHOT
val Project.dokkaVersionType: DokkaVersionType?
    get() = DokkaVersionType.values().find { it.suffix.matches(dokkaVersion.substringAfterLast("-", "")) }

enum class ConclaveDokkaVersionType {
    GA_RELEASE,
    RELEASE_CANDIDATE,
    SNAPSHOT
}

fun Project.getConclaveDokkaVersionType() : ConclaveDokkaVersionType {
    if (dokkaVersion.endsWith("-SNAPSHOT")) {
        return ConclaveDokkaVersionType.SNAPSHOT
    } else if (dokkaVersion.matches(".+-RC[0-9]+$".toRegex())) {
        return ConclaveDokkaVersionType.RELEASE_CANDIDATE
    } else {
        return ConclaveDokkaVersionType.GA_RELEASE
    }
}
