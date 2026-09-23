plugins { id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" }
group = "uk.kairu"
version = "0.5.0"
repositories { mavenCentral(); maven("https://maven.fabricmc.net/") }
val oneConfigJarPath = providers.gradleProperty("kairu.oneconfigJar").orNull
val oneConfigJar = oneConfigJarPath?.let(::file)
dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.159.0+26.2")

    // The OneConfig bootstrap is built from the pinned source in oneconfig-source/ by
    // Build-OneConfig-Client.ps1 and embedded as a Fabric jar-in-jar. Players install
    // only KairuSmpClient; they never need to place a separate OneConfig mod in mods/.
    if (oneConfigJar == null) {
        logger.warn("KairuSmpClient is building without embedded OneConfig. Use Build-OneConfig-Client.ps1 for a distributable client.")
    } else {
        // The bootstrap supplies the public OneConfig API used by KairuOneConfig.
        // It is packaged below as a Fabric nested JAR, not required separately.
        compileOnly(files(oneConfigJar))
    }
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)); withSourcesJar() }
tasks.processResources {
    val nestedJarBlock = oneConfigJar?.let {
        ",\n  \"jars\": [{\"file\": \"META-INF/jars/${it.name}\"}]"
    } ?: ""
    inputs.property("oneConfigJarBlock", nestedJarBlock)
    filesMatching("fabric.mod.json") { expand("oneConfigJarBlock" to nestedJarBlock) }
}
tasks.jar {
    archiveFileName.set("KairuSmpClient-0.5.0.jar")
    if (oneConfigJar != null) from(oneConfigJar) { into("META-INF/jars") }
}
