import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

group = "dev.aether"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val ktorVersion = "3.1.3"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    // Локальный loopback-сервер только для перехвата OAuth redirect_uri.
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")

    // Распаковка Java-рантайма Mojang (файлы отдаются в LZMA).
    implementation("org.tukaani:xz:1.10")

    // DPAPI для привязки ключа шифрования к учётной записи Windows.
    implementation("net.java.dev.jna:jna-platform:5.17.0")

    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }

compose.desktop {
    application {
        mainClass = "dev.aether.MainKt"

        nativeDistributions {
            // Приоритет — Windows; Dmg и Deb собираются, но не тестировались.
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Aether"
            packageVersion = project.version.toString()
            description = "Открытый лаунчер Minecraft: Java Edition"
            vendor = "Aether"
            // jlink: в дистрибутив попадают только реально нужные модули JDK.
            modules("java.instrument", "java.management", "java.naming", "jdk.unsupported", "jdk.crypto.ec")

            windows {
                menu = true
                menuGroup = "Aether"
                perUserInstall = true          // установка без прав администратора
                dirChooser = true
                upgradeUuid = "5f0b8d2e-3a11-4c8c-9c2d-7a1f0e9b4d31"
                // iconFile.set(project.file("packaging/aether.ico"))
            }
            macOS { bundleID = "dev.aether.launcher" }
            linux { debMaintainer = "dev@aether.invalid" }
        }
    }
}
