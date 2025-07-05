plugins {
    kotlin("jvm") version "2.1.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.sloimay"
version = "1.0.1"

repositories {
    // debug
    mavenLocal()


    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        name = "spigotmc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven {
        name = "CodeMC"
        url = uri("https://repo.codemc.io/repository/maven-public/")
    }
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.lucko.me")
    maven("https://maven.enginehub.org/repo/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")

}

dependencies {

    /**
     * If someone knows more about how to manage dependencies efficiently, please
     * feel free to send a pull request XD
     */

    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    // CommandsAPI
    compileOnly("dev.jorel:commandapi-bukkit-core:9.7.0")
    implementation("dev.jorel:commandapi-bukkit-kotlin:9.7.0")

    // Nodestone
    implementation("com.github.sloimayyy:nodestone:1.1.0")
    //implementation("com.sloimay:nodestone:1.1.0") // dev dep
    implementation("com.google.code.gson:gson:2.13.0")
    implementation("com.github.sloimayyy:smath:1.1.4")
    implementation("com.github.sloimayyy:mcvolume:1.0.15")

    // Plot squared
    //implementation(platform("com.intellectualsites.bom:bom-newest:1.52"))
    compileOnly("com.intellectualsites.plotsquared:plotsquared-core:7.3.8")
    compileOnly("com.intellectualsites.plotsquared:plotsquared-bukkit:7.3.8") { isTransitive = false }

    val exposedVersion = "1.0.0-beta-3"
    implementation(group = "org.jetbrains.exposed", name = "exposed-core", version = exposedVersion)
    implementation(group = "org.jetbrains.exposed", name = "exposed-jdbc", version = exposedVersion)
    implementation(group = "org.jetbrains.exposed", name = "exposed-java-time", version = exposedVersion)

    // Luck perms
    compileOnly("net.luckperms:api:5.4")

    // WorldEdit
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.20")

    // Kyori adventure
    implementation("net.kyori:adventure-api:4.14.0")
    implementation("net.kyori:adventure-text-minimessage:4.14.0")

    // NBT API
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.14.1")
}


tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.20.4")
    }
}

val targetJavaVersion = 17
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}


tasks.register<Copy>("copyJarToPluginFolder") {
    // Make this task run after the jar task
    dependsOn("shadowJar")

    // Get the jar file from the libs directory
    from("${buildDir}/libs/${project.name}-${project.version}-all.jar")

    // Specify your destination directory
    into("D:\\Minecraft Servers\\Paper 1.20.4 - ORE dev\\plugins")  // Replace with your path

    // Optional: rename the file if desired
    // rename { "your-preferred-name.jar" }
}

// Make the build task depend on our new copy task
tasks.named("build") {
    finalizedBy("copyJarToPluginFolder")
}
