plugins {
    id("net.fabricmc.fabric-loom")
    `oneconfig-bridge` // creates the modImplementation and friends configurations
    `oneconfig-fabric`
}

dependencies {
    // Kairu uses the Fabric client message event only to receive the signed
    // SMPPlatform response envelope.  It never performs privileged work.
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.159.0+26.2")
    if (versionedCatalog.has("modmenu"))
    implementation(versionedCatalog["modmenu"])
}
