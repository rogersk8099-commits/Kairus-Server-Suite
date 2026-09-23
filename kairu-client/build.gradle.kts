plugins { id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" }
group = "uk.kairu"
version = "0.5.0"
repositories { mavenCentral(); maven("https://maven.fabricmc.net/") }
dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.159.0+26.2")

    // The OneConfig bootstrap is built from the pinned source in oneconfig-source/ by
    // Build-OneConfig-Client.ps1 and embedded as a Fabric jar-in-jar. Players install
    // only KairuSmpClient; they never need to place a separate OneConfig mod in mods/.
    val oneConfigJar = providers.gradleProperty("kairu.oneconfigJar").orNull
    if (oneConfigJar != null) {
        implementation(files(oneConfigJar))
        include(files(oneConfigJar))
    } else {
        logger.warn("KairuSmpClient is building without embedded OneConfig. Use Build-OneConfig-Client.ps1 for a distributable client.")
    }
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)); withSourcesJar() }
tasks.jar { archiveFileName.set("KairuSmpClient-0.5.0.jar") }
