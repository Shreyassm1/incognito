plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":core-crypto"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("com.github.sarxos:webcam-capture:0.3.12")
    implementation("org.jcodec:jcodec:0.2.5")
    implementation("org.jcodec:jcodec-javase:0.2.5")
}

compose.desktop {
    application {
        mainClass = "com.incognito.vault.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "IncognitoVault"
            packageVersion = "1.0.0"
            windows {
                menuGroup = "Incognito Vault"
                upgradeUuid = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"
            }
        }
    }
}
