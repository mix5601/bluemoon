// Shared engine: Blockbench models, resource pack, skills, model-backed mobs.
dependencies {
    // Bukkit API classes are needed at test runtime to load mechanic classes (no server is started)
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Paper bundles Gson and JOML at runtime; tests only touch the Bukkit-free code.
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.joml:joml:1.10.8")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    test {
        // validate the example models shipped with both plugins
        resources.srcDir(rootProject.file("bluemoon/src/main/resources"))
        resources.srcDir(rootProject.file("skills/src/main/resources"))
        resources.include("models/**", "weapons/**", "skills/**", "mobs/**", "summons/**")
    }
}

tasks.test {
    useJUnitPlatform()
}
