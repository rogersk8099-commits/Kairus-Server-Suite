allprojects {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/releases")
    }
}

val bootstrapNodes = subprojects.filter { it.parent?.path == ":bootstrap" }

tasks.register<Sync>("buildAndCollect") {
    group = "build"
    description = "Builds every OneConfig bootstrap node and collects the production Kairu jars into build/libs."

    dependsOn(":bootstrap:assembleAllNodes")

    from(bootstrapNodes.map { it.layout.buildDirectory.dir("libs") }) {
        include("*-${rootProject.version}.jar")
    }
    into(layout.buildDirectory.dir("libs"))

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    doFirst {
        if (bootstrapNodes.isEmpty()) {
            throw GradleException("No bootstrap nodes were registered — check the stonecutter tree in settings.gradle.kts.")
        }
    }

    doLast {
        val target = destinationDir
        logger.lifecycle("Collected ${target.listFiles { f -> f.extension == "jar" }?.size ?: 0} Kairu jars into $target")
    }
}
