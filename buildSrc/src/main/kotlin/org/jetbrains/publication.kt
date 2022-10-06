package org.jetbrains

import com.github.jengelman.gradle.plugins.shadow.ShadowExtension
import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.DokkaPublicationChannel.*
import java.net.URI

class DokkaPublicationBuilder {
    enum class Component {
        Java, Shadow
    }

    var artifactId: String? = null
    var component: Component = Component.Java
}


fun Project.registerDokkaArtifactPublication(publicationName: String, configure: DokkaPublicationBuilder.() -> Unit) {
    configure<PublishingExtension> {
        publications {
            register<MavenPublication>(publicationName) {
                val builder = DokkaPublicationBuilder().apply(configure)
                artifactId = builder.artifactId
                when (builder.component) {
                    DokkaPublicationBuilder.Component.Java -> from(components["java"])
                    DokkaPublicationBuilder.Component.Shadow -> run {
                        extensions.getByType(ShadowExtension::class.java).component(this)
                        artifact(tasks["sourcesJar"])
                    }
                }
                artifact(tasks["javadocJar"])
                configurePom("Dokka ${project.name}")
            }
        }
    }

    configureBintrayPublicationIfNecessary(publicationName)
    configureArtifactoryPublication(publicationName)
    configureSonatypePublicationIfNecessary(publicationName)
    createDokkaPublishTaskIfNecessary()
    registerBinaryCompatibilityCheck(publicationName)
}
fun Project.configureArtifactoryPublication(
    vararg publications: String
) {
    if (Artifactory in this.publicationChannels) {
        configure<PublishingExtension> {
            repositories {
                /* already registered */
                findByName(Artifactory.name)?.let { return@repositories }
                maven {
                    name = Artifactory.name
                    url = URI.create(getArtifactoryUrlForPublication())
                    credentials {
                        username = System.getenv("CONCLAVE_ARTIFACTORY_USERNAME")
                        password = System.getenv("CONCLAVE_ARTIFACTORY_PASSWORD")
                    }
                }
            }
        }
    }

    whenEvaluated {
        tasks.withType<PublishToMavenRepository> {
            if (this.repository.name == Artifactory.name) {
                this.isEnabled = this.isEnabled && publication.name in publications
                if (!this.isEnabled) {
                    this.group = "disabled"
                }
            }
        }
    }
}

fun Project.createDokkaPublishTaskIfNecessary() {
    tasks.maybeCreate("dokkaPublish").run {
        if (publicationChannels.any { it.isArtifactoryRepository }) {
            dependsOn(tasks.named("publish"))
        }

        if (publicationChannels.any { it.isMavenRepository }) {
            dependsOn(tasks.named("publishToSonatype"))
        }

        if (publicationChannels.any { it.isBintrayRepository }) {
            dependsOn(tasks.named("bintrayUpload"))
        }
    }
}

fun Project.configureBintrayPublicationIfNecessary(vararg publications: String) {
    if (publicationChannels.any { it.isBintrayRepository }) {
        configureBintrayPublication(*publications)
    }
}

private fun Project.configureBintrayPublication(vararg publications: String) {
    extensions.configure<BintrayExtension>("bintray") {
        user = System.getenv("BINTRAY_USER")
        key = System.getenv("BINTRAY_KEY")
        dryRun = System.getenv("BINTRAY_DRY_RUN") == "true" ||
                project.properties["bintray_dry_run"] == "true"
        pkg = PackageConfig().apply {
            val bintrayPublicationChannels = publicationChannels.filter { it.isBintrayRepository }
            if (bintrayPublicationChannels.size > 1) {
                throw IllegalArgumentException(
                    "Only a single bintray repository can be used for publishing at once. Found $publicationChannels"
                )
            }

            repo = when (bintrayPublicationChannels.single()) {
                Artifactory, MavenCentral, MavenCentralSnapshot -> throw IllegalStateException(
                    "${bintrayPublicationChannels.single()} is not a bintray repository"
                )
                BintrayKotlinDev -> "kotlin-dev"
                BintrayKotlinEap -> "kotlin-eap"
                BintrayKotlinDokka -> "dokka"
            }

            name = "dokka"
            userOrg = "kotlin"
            desc = "Dokka, the Kotlin documentation tool"
            vcsUrl = "https://github.com/kotlin/dokka.git"
            setLicenses("Apache-2.0")
            version = VersionConfig().apply {
                name = dokkaVersion
            }
        }
        setPublications(*publications)
    }
}

fun Project.configureSonatypePublicationIfNecessary(vararg publications: String) {
    if (publicationChannels.any { it.isMavenRepository }) {
        signPublicationsIfKeyPresent(*publications)
    }
}

fun Project.getArtifactoryUrlForPublication(): String {
    var mavenRepo = ""
    val versionType = getConclaveDokkaVersionType()
    if (versionType == ConclaveDokkaVersionType.SNAPSHOT) {
        mavenRepo = "conclave-maven-dev"
    } else if (versionType == ConclaveDokkaVersionType.RELEASE_CANDIDATE) {
        mavenRepo = "conclave-maven-unstable"
    } else {
        mavenRepo = "conclave-maven-stable"
    }
    return "https://software.r3.com/artifactory/" + mavenRepo
}

fun MavenPublication.configurePom(projectName: String) {
    pom {
        name.set(projectName)
        description.set("Dokka is a documentation engine for Kotlin and Java, performing the same function as Javadoc for Java")
        url.set("https://github.com/Kotlin/dokka")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("JetBrains")
                name.set("JetBrains Team")
                organization.set("JetBrains")
                organizationUrl.set("http://www.jetbrains.com")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/Kotlin/dokka.git")
            url.set("https://github.com/Kotlin/dokka/tree/master")
        }
    }
}

@Suppress("UnstableApiUsage")
private fun Project.signPublicationsIfKeyPresent(vararg publications: String) {
    val signingKeyId: String? = System.getenv("SIGN_KEY_ID")
    val signingKey: String? = System.getenv("SIGN_KEY")
    val signingKeyPassphrase: String? = System.getenv("SIGN_KEY_PASSPHRASE")

    if (!signingKey.isNullOrBlank()) {
        extensions.configure<SigningExtension>("signing") {
            if (signingKeyId?.isNotBlank() == true) {
                useInMemoryPgpKeys(signingKeyId, signingKey, signingKeyPassphrase)
            } else {
                useInMemoryPgpKeys(signingKey, signingKeyPassphrase)
            }
            publications.forEach { publicationName ->
                extensions.findByType(PublishingExtension::class)!!.publications.findByName(publicationName)?.let {
                    sign(it)
                }
            }
        }
    }
}
