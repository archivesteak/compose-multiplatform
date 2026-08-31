plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("io.github.archivesteak.compose")
}

// Validate project model state while it is legal to access it. Reaching this point also proves
// that Gradle resolved and applied the published marker from the isolated repository.
check(plugins.hasPlugin("io.github.archivesteak.compose"))
check(extensions.findByName("compose") != null)

dependencies {
    if (providers.gradleProperty("useUpstreamCompose").map(String::toBoolean).getOrElse(false)) {
        val scope = providers.gradleProperty("upstreamComposeScope").getOrElse("implementation")
        add(
            scope,
            "org.jetbrains.compose.runtime:runtime:COMPOSE_UPSTREAM_VERSION_PLACEHOLDER",
        )
    } else {
        implementation(
            "io.github.archivesteak.compose.runtime:runtime:COMPOSE_VERSION_PLACEHOLDER"
        )
    }
}

tasks.register("verifyForkPluginResolution") {
    doLast { println("FORK_PLUGIN_RESOLUTION_OK") }
}
