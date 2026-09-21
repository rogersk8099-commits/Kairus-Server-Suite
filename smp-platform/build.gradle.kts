plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
    id("org.owasp.dependencycheck") version "12.1.0"
}

group = "com.neonnexus.smpplatform"
version = "0.5.0-kairu-foundation"
description = "Kairu SMPPlatform production plugin for Paper/Purpur 26.2"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.skriptlang.org/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.SkriptLang:Skript:2.10.0")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.flywaydb:flyway-core:11.3.1")
    implementation("org.flywaydb:flyway-database-postgresql:11.3.1")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.google.code.gson:gson:2.12.1")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}
tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.test {
    useJUnitPlatform()
    testLogging { events("failed", "skipped") }
}
tasks.shadowJar {
    archiveBaseName.set("SMPPlatform")
    archiveClassifier.set("")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    // Shadow 8.x cannot rewrite Java 25 (class-file 69) bytecode. Keep dependencies bundled
    // without relocation so the plugin remains buildable on the Paper 26.2 / Java 25 toolchain.
    manifest { attributes["paperweight-mappings-namespace"] = "mojang" }
}
tasks.jar { enabled = false }
tasks.build { dependsOn(tasks.shadowJar) }

dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    analyzers.assemblyEnabled = false
}
