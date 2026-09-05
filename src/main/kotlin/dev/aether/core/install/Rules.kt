package dev.aether.core.install

import dev.aether.core.Platform
import dev.aether.core.meta.Library
import dev.aether.core.meta.Rule
import java.io.File

/**
 * Вычисление правил `rules[]` ровно так, как это делает официальный лаунчер:
 * правила применяются по порядку, побеждает последнее совпавшее,
 * при наличии блока rules действие по умолчанию — запрет.
 */
object Rules {

    fun allows(rules: List<Rule>?, features: Map<String, Boolean> = emptyMap()): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (!applies(rule, features)) continue
            allowed = rule.action == "allow"
        }
        return allowed
    }

    private fun applies(rule: Rule, features: Map<String, Boolean>): Boolean {
        rule.os?.let { os ->
            os.name?.let { if (it != Platform.osName) return false }
            os.arch?.let { if (it != Platform.arch) return false }
            os.version?.let { if (!Regex(it).containsMatchIn(Platform.osVersion)) return false }
        }
        rule.features?.forEach { (key, expected) ->
            if ((features[key] ?: false) != expected) return false
        }
        return true
    }

    /** Классификатор natives для текущей ОС в формате до 1.19 (natives-windows-${arch}). */
    fun nativeClassifier(library: Library): String? =
        library.natives?.get(Platform.osName)?.replace("\${arch}", Platform.nativesArchBits)

    /**
     * Путь артефакта в локальном Maven-репозитории по координате
     * `group:artifact:version[:classifier]`.
     */
    fun mavenPath(name: String): String {
        val parts = name.split(":")
        require(parts.size >= 3) { "Некорректная Maven-координата: $name" }
        val (group, artifact, version) = parts
        val classifier = parts.getOrNull(3)
        val extension = if (version.contains("@")) version.substringAfter("@") else "jar"
        val cleanVersion = version.substringBefore("@")
        val fileName = buildString {
            append(artifact).append('-').append(cleanVersion)
            if (classifier != null) append('-').append(classifier)
            append('.').append(extension)
        }
        return group.replace('.', '/') + "/" + artifact + "/" + cleanVersion + "/" + fileName
    }

    fun libraryFile(librariesDir: File, library: Library): File {
        val path = library.downloads?.artifact?.path ?: mavenPath(library.name)
        return File(librariesDir, path)
    }
}
