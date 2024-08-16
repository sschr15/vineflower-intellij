import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key)?.toString() ?: ""

plugins {
    // Java support
    id("java")
    // Kotlin support
    kotlin("jvm") version "1.9.22"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij.platform") version "2.0.1"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "1.3.0"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
    }

    pluginVerification {
        ides {
            properties("pluginVerifierIdeVersions").split(',').map(String::trim).filter(String::isNotEmpty).forEach {
                ide(IntelliJPlatformType.IntellijIdeaCommunity, it)
            }
            recommended()
        }
    }
}

repositories {
    mavenCentral()
    intellijPlatform.defaultRepositories()
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(properties("platformVersion"))
        plugins(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
        bundledPlugins(properties("platformBundledPlugins").split(',').map(String::trim).filter(String::isNotEmpty))

        pluginVerifier()
        instrumentationTools()
        zipSigner()
    }
    
    testCompileOnly("org.vineflower:vineflower:1.10.1")
}

tasks {
    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
        }
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = it
            kotlinOptions.freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    patchPluginXml {
        pluginVersion = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            projectDir.resolve("README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider {
            changelog.run {
                getOrNull(properties("pluginVersion")) ?: getLatest()
            }.toHTML()
        })
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
        task {
            jvmArgumentProviders += CommandLineArgumentProvider {
                listOf(
                    "robot-server.port=8082",
                    "ide.mac.message.dialogs.as.sheets=false",
                    "jb.privacy.policy.text=<!--999.999-->",
                    "jb.consents.confirmation.enabled=false",
                )
            }
        }

        plugins {
            robotServerPlugin()
        }
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').take(1))
    }

    buildSearchableOptions {
        isEnabled = false
    }

    runIde {
        systemProperty("idea.ProcessCanceledException", "disabled")
        systemProperty("idea.debug.mode", "true")
    }
}
