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
        create("dokkaGradlePlugin") {
            id = "com.r3.conclave"
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
        groupId = "com.r3.conclave"
        artifactId = "dokka-gradle-plugin"
    }
}

publishing {
    println("XXX1: Publishing task in runners:gradle-plugin")
    publications {
        println("XXX1: Registering 1st task in runners:gradle-plugin")
        register<MavenPublication>("dokkaGradlePluginForIntegrationTests") {
            artifactId = "dokka-gradle-plugin"
            from(components["java"])
            version = "for-integration-tests-SNAPSHOT"
        }

        println("XXX2: Registering 2nd task in runners:gradle-plugin")
        register<MavenPublication>("pluginMaven") {
            configurePom("Dokka ${project.name}")
            artifactId = "dokka-gradle-plugin"
            artifact(tasks["javadocJar"])
        }

        println("XXX3: After evaluate in runners:gradle-plugin")
        afterEvaluate {
            named<MavenPublication>("dokkaGradlePluginPluginMarkerMaven") {
                configurePom("Dokka plugin")
            }
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf { publication != publishing.publications["dokkaGradlePluginForIntegrationTests"] }
}

afterEvaluate { // Workaround for an interesting design choice https://github.com/gradle/gradle/blob/c4f935f77377f1783f70ec05381c8182b3ade3ea/subprojects/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/MavenPluginPublishPlugin.java#L49
    configureBintrayPublicationIfNecessary("pluginMaven", "dokkaGradlePluginPluginMarkerMaven")
    configureSpacePublicationIfNecessary("pluginMaven", "dokkaGradlePluginPluginMarkerMaven")
    configureSonatypePublicationIfNecessary("pluginMaven", "dokkaGradlePluginPluginMarkerMaven")
//    configureArtifactorySnapshotPublication("pluginMaven", "dokkaGradlePluginPluginMarkerMaven")
//    configureArtifactoryReleasePublication("pluginMaven", "dokkaGradlePluginPluginMarkerMaven")
    createDokkaPublishTaskIfNecessary()
}

registerDokkaArtifactPublication("dokkaGradlePlugin") {
    artifactId = "dokka-gradle-plugin"
}
