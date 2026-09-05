package dev.aether.core.loader

/** Единая точка доступа к провайдерам модлоадеров. */
object LoaderRegistry {

    private val providers = mapOf(
        Loader.FABRIC to FabricProvider(),
        Loader.FORGE to ForgeProvider(Loader.FORGE),
        Loader.NEOFORGE to ForgeProvider(Loader.NEOFORGE),
    )

    fun provider(loader: Loader): LoaderProvider? = providers[loader]

    /**
     * NeoForge существует только для 1.20.2 и новее — для более старых
     * версий пункт в интерфейсе не показывается.
     */
    fun supports(loader: Loader, gameVersion: String): Boolean = when (loader) {
        Loader.VANILLA -> true
        Loader.NEOFORGE -> compareVersions(gameVersion, "1.20.2") >= 0
        else -> true
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split(".").mapNotNull { it.toIntOrNull() }
        val right = b.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrElse(i) { 0 }) - (right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}
