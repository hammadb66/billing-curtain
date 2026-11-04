import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.example.billing.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Billing System"
            packageVersion = "1.0.0"
            vendor = "Zavion Technologies"
            description = "Manage your Curtains Bill"

            includeAllModules = true

            windows{
                iconFile.set(project.file("icon.ico"))
                menu = true
                shortcut = true
                menuGroup = "Billing System"
                upgradeUuid = "75371b93-0175-4aec-9fa4-abc3738337fb"
            }
        }

        buildTypes.release.proguard {
            configurationFiles.from("proguard-rules.pro")
            isEnabled.set(true)
        }

    }
}