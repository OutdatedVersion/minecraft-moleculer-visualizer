import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.0"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

group = "com.outdatedversion"
version = "0.1.0"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    maven(url = "https://papermc.io/repo/repository/maven-public/")
    maven(url = "https://repo.aikar.co/content/groups/aikar/")
    maven(url = "https://oss.sonatype.org/content/repositories/releases")
}

dependencies {
    implementation("co.aikar:acf-paper:0.5.0-SNAPSHOT")
    implementation("com.elmakers.mine.bukkit:EffectLib:9.4")
    // Moleculer and dependencies
    implementation("com.github.berkesa:moleculer-java:1.2.22")
    implementation("com.github.berkesa:datatree-promise:1.0.7")
    implementation("com.github.berkesa:datatree-core:1.1.2")
    // Moleculer transport
    implementation("io.nats:jnats:2.13.2")

    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")

    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("com.google.guava:guava:30.1.1-jre")
    api("org.apache.commons:commons-math3:3.6.1")
}


// based on: https://discuss.gradle.org/t/how-to-run-execute-string-as-a-shell-command-in-kotlin-dsl/32235/10
fun runCommand(cmd: String, currentWorkingDir: File = file("./")): String {
    val byteOut = org.apache.commons.io.output.ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = cmd.split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

tasks.withType<ProcessResources> {
    expand(
        "main" to "com.outdatedversion.moleculer.visualizer.Plugin",
        "version" to "${project.version}-${runCommand("git rev-parse --short HEAD")}-${runCommand("git rev-parse --abbrev-ref HEAD")}"
    )
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.javaParameters = true
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveFileName.set("moleculer-visualizer-${project.version}-shadow.jar")
}