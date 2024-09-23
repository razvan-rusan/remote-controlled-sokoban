import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

group = "sokoban"

repositories {
    mavenCentral()
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
}

application {
    mainClass.set("sokoban.AppKt")
}

// Using 'tasks' block to configure all compile tasks
tasks {
    withType<JavaCompile> {
        // Use options.release for setting the Java version compatibility
        options.release.set(22)
        // Set encoding to UTF-8
        options.encoding = "UTF-8"
    }

    withType<KotlinCompile> {
        // Set JVM target for Kotlin to 17 to maintain compatibility with Java 22
        compilerOptions.jvmTarget.set(JvmTarget.JVM_22)
    }
}
