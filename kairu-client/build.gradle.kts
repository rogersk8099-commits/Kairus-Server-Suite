plugins { id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" }
group = "uk.kairu"
version = "0.5.0"
repositories { mavenCentral(); maven("https://maven.fabricmc.net/") }
dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.159.0+26.2")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)); withSourcesJar() }
tasks.jar { archiveFileName.set("KairuSmpClient-0.5.0.jar") }
