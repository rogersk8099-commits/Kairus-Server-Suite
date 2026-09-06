plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.kairu"
version = "1.0.0"

description = "Kairu SMP control-plane bridge for Paper and Purpur"

java {
    // Java 21 is required at build and runtime; --release is also enforced below.
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
    }
    withType<ProcessResources>().configureEach {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    test {
        useJUnitPlatform()
    }
    shadowJar {
        archiveClassifier.set("")
        // No third-party runtime libraries are currently necessary; this keeps the deliverable deployable as a single jar.
        minimize()
    }
    build {
        dependsOn(shadowJar)
    }
}
