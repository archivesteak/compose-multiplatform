import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.gradleKotlinDsl

plugins {
    `java`
    `maven-publish`
    `java-gradle-plugin`
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow") apply false
}

repositories {
    maven("https://packages.jetbrains.team/maven/p/cmp/dev")
}

val embeddedDependencies by configurations.creating { isTransitive = false }
dependencies {
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())

    fun embedded(dep: String) {
        compileOnly(dep)
        embeddedDependencies(dep)
    }

    val jacksonVersion = "2.12.5"
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("io.ktor:ktor-client-okhttp:3.1.3")
    implementation("org.apache.tika:tika-parsers:1.24.1")
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("org.jetbrains:space-sdk-jvm:2024.3-185883")
    embedded("de.undercouch:gradle-download-task:4.1.2")
}

val shadowJar by tasks.registering(ShadowJar::class) {
    val fromPackage = "de.undercouch"
    val toPackage = "org.jetbrains.compose.internal.publishing.$fromPackage"
    relocate(fromPackage, toPackage)
    archiveBaseName.set("shadow")
    archiveClassifier.set("")
    archiveVersion.set("")
    configurations = listOf(embeddedDependencies)
    from(sourceSets["main"]!!.output)
    exclude("META-INF/gradle-plugins/de.undercouch.download.properties")
}

// ShadowJar already contains this project's classes and resources. Re-zipping it through the
// regular JAR duplicates every project entry and can publish both relocated and original bytecode.
tasks.named<Jar>("jar") {
    enabled = false
}
listOf("apiElements", "runtimeElements").forEach { configurationName ->
    configurations.named(configurationName) {
        outgoing.artifacts.clear()
        outgoing.artifact(shadowJar)
    }
}
tasks.named("assemble") {
    dependsOn(shadowJar)
}
