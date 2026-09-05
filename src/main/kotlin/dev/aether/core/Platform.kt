package dev.aether.core

import java.io.File

/**
 * Определение платформы в терминах, которыми оперируют манифесты Mojang.
 * Значения `osName` совпадают с полем `rules[].os.name` в version JSON,
 * `runtimeKey` — с ключами верхнего уровня в java-runtime/all.json.
 */
object Platform {

    private val rawOs = System.getProperty("os.name").lowercase()
    private val rawArch = System.getProperty("os.arch").lowercase()

    val isWindows = rawOs.contains("win")
    val isMac = rawOs.contains("mac") || rawOs.contains("darwin")
    val isLinux = !isWindows && !isMac

    /** windows | osx | linux — как в правилах version JSON. */
    val osName: String = when {
        isWindows -> "windows"
        isMac -> "osx"
        else -> "linux"
    }

    val osVersion: String = System.getProperty("os.version") ?: ""

    /** x86 | x86_64 | arm64 — как в rules[].os.arch. */
    val arch: String = when {
        rawArch == "aarch64" || rawArch == "arm64" -> "arm64"
        rawArch.contains("64") -> "x86_64"
        else -> "x86"
    }

    /** "32" / "64" — подстановка в классификаторы natives вида natives-windows-${arch}. */
    val nativesArchBits: String = if (arch == "x86") "32" else "64"

    /** Ключ платформы в https://piston-meta.mojang.com/v1/products/java-runtime/.../all.json */
    val runtimeKey: String = when {
        isWindows && arch == "arm64" -> "windows-arm64"
        isWindows && arch == "x86" -> "windows-x86"
        isWindows -> "windows-x64"
        isMac && arch == "arm64" -> "mac-os-arm64"
        isMac -> "mac-os"
        isLinux && arch == "x86" -> "linux-i386"
        else -> "linux"
    }

    val classpathSeparator: String = File.pathSeparator

    /** Каталог данных лаунчера по конвенциям ОС (не .minecraft — чтобы не конфликтовать с официальным). */
    val dataDir: File = when {
        isWindows -> File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Aether")
        isMac -> File(System.getProperty("user.home"), "Library/Application Support/Aether")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share", "aether")
    }

    /** Физическая память в мегабайтах — для верхней границы слайдера RAM. */
    fun totalRamMb(): Long = try {
        val bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val m = bean.javaClass.getMethod("getTotalMemorySize").apply { isAccessible = true }
        (m.invoke(bean) as Long) / 1024 / 1024
    } catch (_: Throwable) {
        8192L
    }
}
