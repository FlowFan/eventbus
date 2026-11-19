import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        // The oldest supported IDE (2024.1) runs on Java 17
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Build against the locally installed Android Studio (no IDE download); fall back to IntelliJ IDEA, e.g. on CI
        val localIde = "/Applications/Android Studio.app"
        if (file(localIde).exists()) {
            local(localIde)
        } else {
            intellijIdea("2026.3")
        }

        bundledPlugins("com.intellij.java", "org.jetbrains.kotlin")

        testFramework(TestFrameworkType.Platform)
    }
}

// IntelliJ Platform Gradle Plugin Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
}
