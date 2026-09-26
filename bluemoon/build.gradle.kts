evaluationDependsOn(":core")

val core = project(":core")

dependencies {
    implementation(core)
}

base {
    archivesName.set("BlueMoon")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// the plugin jar carries the shared core classes
tasks.jar {
    from(core.extensions.getByType<SourceSetContainer>()["main"].output)
}
