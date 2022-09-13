import org.jetbrains.*

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.15.0"
}

repositories {
    google()
}

dependencies {
    api(project(":core"))

    val jackson_version: String by project
    compileOnly("com.fasterxml.jackson.core:jackson-annotations:$jackson_version")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin")
    compileOnly("com.android.tools.build:gradle:4.0.1")
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    testImplementation(project(":test-utils"))
    testImplementation(gradleApi())
    testImplementation(gradleKotlinDsl())
    testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
    testImplementation("com.android.tools.build:gradle:4.0.1")


    constraints {
        val kotlin_version: String by project
        compileOnly("org.jetbrains.kotlin:kotlin-reflect:${kotlin_version}") {
            because("kotlin-gradle-plugin and :core both depend on this")
        }
    }
}

val sourceJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

gradlePlugin {
    plugins {
        println("XXX Creating dokka gradle plugin")
        create("dokkaGradlePlugin") {
            id = "com.r3.conclave.dokka"
            displayName = "Dokka plugin"
            description = "Dokka, the Kotlin documentation tool"
            implementationClass = "org.jetbrains.dokka.gradle.DokkaPlugin"
            version = dokkaVersion
            isAutomatedPublishing = true
        }
    }
}

pluginBundle {
    website = "https://www.kotlinlang.org/"
    vcsUrl = "https://github.com/kotlin/dokka.git"
    tags = listOf("dokka", "kotlin", "kdoc", "android", "documentation")

    mavenCoordinates {
        groupId = "com.r3.conclave.dokka"
        artifactId = "dokka-gradle-plugin"
    }
}

publishing {
    println("XXX1: Publishing task in runners:gradle-plugin")
    publications {
        println("XXX2: Registering 1st task in runners:gradle-plugin")
        register<MavenPublication>("dokkaGradlePluginForIntegrationTests") {
            artifactId = "dokka-gradle-plugin"
            from(components["java"])
            version = "for-integration-tests-SNAPSHOT"
        }

        println("XXX3: Registering 2nd task in runners:gradle-plugin")
        register<MavenPublication>("pluginMaven") {
            configurePom("Dokka ${project.name}")
            artifactId = "dokka-gradle-plugin"
            artifact(tasks["javadocJar"])
        }

        println("XXX4: After evaluate in runners:gradle-plugin")
        afterEvaluate {
            named<MavenPublication>("dokkaGradlePluginPluginMarkerMaven") {
                artifactId = "dokka-gradle-plugin"
                configurePom("com.r3.conclave.dokka.gradle.plugin")
            }
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf {
        println("XXX: onlyIf, publishing.publications: ${publication.name}, ${publication.artifactId}")
        publication != publishing.publications["dokkaGradlePluginForIntegrationTests"]
    }
}

afterEvaluate { // Workaround for an interesting design choice https://github.com/gradle/gradle/blob/c4f935f77377f1783f70ec05381c8182b3ade3ea/subprojects/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/MavenPluginPublishPlugin.java#L49
    println("XXX: afterEvaluate in runners:gradle-plugin")
    configureBintrayPublicationIfNecessary("pluginMaven", "dokkaGradlePluginPluginMarkerMaven")
    configureSpacePublicationIfNecessary("pluginMaven", "dokkaGradlePluginPluginMarkerMaven")
    configureSonatypePublicationIfNecessary("pluginMaven", "dokkaGradlePluginPluginMarkerMaven")
    createDokkaPublishTaskIfNecessary()
}

registerDokkaArtifactPublication("dokkaGradlePlugin") {
    artifactId = "dokka-gradle-plugin"
}
