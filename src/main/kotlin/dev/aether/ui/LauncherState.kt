package dev.aether.ui

import androidx.compose.runtime.*
import dev.aether.core.Platform
import dev.aether.core.auth.*
import dev.aether.core.install.GameInstaller
import dev.aether.core.install.GamePaths
import dev.aether.core.install.JavaProvisioner
import dev.aether.core.launch.GameLauncher
import dev.aether.core.launch.GameSession
import dev.aether.core.loader.Loader
import dev.aether.core.loader.LoaderRegistry
import dev.aether.core.loader.LoaderVersion
import dev.aether.core.meta.MetaClient
import dev.aether.core.meta.VersionEntry
import dev.aether.core.net.Progress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI

enum class Screen { HOME, LIBRARY, NEWS, SETTINGS, PROFILES }

sealed interface LoginState {
    data object Idle : LoginState
    data object Working : LoginState
    data class DeviceCode(val userCode: String, val verificationUri: String) : LoginState
    data class Failed(val message: String, val hint: String?) : LoginState
}

/**
 * Единое состояние приложения. Вся долгая работа уходит в Dispatchers.IO,
 * UI обновляется только через snapshot-состояние Compose.
 */
class LauncherState(
    private val scope: CoroutineScope,
    val clientId: String,
) {
    val paths = GamePaths(File(Platform.dataDir, "game"))
    private val meta = MetaClient()
    private val installer = GameInstaller(meta, paths)
    private val javaProvisioner = JavaProvisioner(meta, paths.runtimes)
    private val microsoft = MicrosoftAuth(clientId)
    private val authenticator = MinecraftAuthenticator(microsoft)
    private val accountStore = AccountStore()
    private val gameLauncher = GameLauncher(clientId)

    // ---- навигация и оформление ----
    var screen by mutableStateOf(Screen.HOME)
    var themeMode by mutableStateOf(dev.aether.ui.theme.ThemeMode.DARK)
    var navExpanded by mutableStateOf(false)

    // ---- аккаунты ----
    var accounts by mutableStateOf<List<Account>>(emptyList())
    var activeUuid by mutableStateOf<String?>(null)
    val activeAccount: Account? get() = accounts.firstOrNull { it.uuid == activeUuid } ?: accounts.firstOrNull()
    var loginState by mutableStateOf<LoginState>(LoginState.Idle)

    // ---- версии ----
    var versions by mutableStateOf<List<VersionEntry>>(emptyList())
    var latestRelease by mutableStateOf<String?>(null)
    var selectedVersionId by mutableStateOf<String?>(null)
    var search by mutableStateOf("")
    var filter by mutableStateOf("all")
    var versionsLoading by mutableStateOf(false)

    // ---- модлоадеры ----
    var loader by mutableStateOf(Loader.VANILLA)
    var loaderVersions by mutableStateOf<List<LoaderVersion>>(emptyList())
    var loaderVersion by mutableStateOf<String?>(null)
    var loaderVersionsLoading by mutableStateOf(false)

    /** Загрузчики, доступные для выбранной версии игры. */
    fun availableLoaders(): List<Loader> = Loader.entries.filter { candidate ->
        LoaderRegistry.supports(candidate, selectedVersionId ?: "")
    }

    /**
     * Список версий загрузчика подтягивается при смене загрузчика или версии
     * игры. Пока он грузится, кнопка запуска остаётся доступной — если
     * пользователь ничего не выбрал, берётся рекомендованная сборка.
     */
    fun selectLoader(next: Loader) {
        loader = next
        loaderVersion = null
        loaderVersions = emptyList()
        if (next == Loader.VANILLA) return
        val gameVersion = selectedVersionId ?: return
        scope.launch {
            loaderVersionsLoading = true
            val provider = LoaderRegistry.provider(next)
            val result = runCatching {
                withContext(Dispatchers.IO) { provider?.availableVersions(gameVersion).orEmpty() }
            }
            result
                .onSuccess { list ->
                    loaderVersions = list
                    loaderVersion = list.firstOrNull { it.recommended }?.version ?: list.firstOrNull()?.version
                    if (list.isEmpty()) error = "${next.displayName} не выпущен для версии $gameVersion"
                }
                .onFailure { error = "Не удалось получить версии ${next.displayName}: ${it.message}" }
            loaderVersionsLoading = false
        }
    }

    // ---- настройки ----
    var ramMb by mutableStateOf(4096)
    val maxRamMb = (Platform.totalRamMb() - 2048).coerceIn(2048, 32768).toInt()
    var javaOverride by mutableStateOf<File?>(null)

    // ---- запуск ----
    var launching by mutableStateOf(false)
    var progress by mutableStateOf(Progress("", 0, 1))
    var logLines by mutableStateOf<List<String>>(emptyList())
    var session by mutableStateOf<GameSession?>(null)
    var error by mutableStateOf<String?>(null)

    /** Выбор версии игры сбрасывает подобранную версию загрузчика. */
    fun selectGameVersion(id: String) {
        selectedVersionId = id
        if (loader != Loader.VANILLA) selectLoader(loader)
    }

    fun installedVersionIds(): Set<String> =
        paths.versions.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()

    fun bootstrap() {
        val (stored, active) = accountStore.load()
        accounts = stored
        activeUuid = active
        scope.launch {
            refreshVersions()
            // Тихий рефреш сессии: пользователь не видит окно входа при каждом старте.
            stored.firstOrNull { it.uuid == active }?.let { account ->
                runCatching { withContext(Dispatchers.IO) { authenticator.refresh(account) } }
                    .onSuccess { updateAccount(it) }
            }
        }
    }

    suspend fun refreshVersions() {
        versionsLoading = true
        runCatching { withContext(Dispatchers.IO) { meta.versionManifest() } }
            .onSuccess { manifest ->
                versions = manifest.versions
                latestRelease = manifest.latest.release
                if (selectedVersionId == null) selectedVersionId = manifest.latest.release
            }
            .onFailure { failure ->
                meta.cachedVersionManifest()?.let { versions = it.versions }
                error = "Не удалось обновить список версий: ${failure.message}"
            }
        versionsLoading = false
    }

    // ---- вход ----

    fun signIn() {
        scope.launch {
            loginState = LoginState.Working
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val ms = try {
                        microsoft.authorizationCodeFlow { url -> openBrowser(url) }
                    } catch (_: Throwable) {
                        // Порт занят или браузер недоступен — переходим на device code.
                        val dc = microsoft.startDeviceCode()
                        loginState = LoginState.DeviceCode(dc.userCode, dc.verificationUri)
                        openBrowser(dc.verificationUri)
                        microsoft.pollDeviceCode(dc)
                    }
                    authenticator.completeLogin(ms)
                }
            }
            result
                .onSuccess { account ->
                    updateAccount(account)
                    activeUuid = account.uuid
                    persist()
                    loginState = LoginState.Idle
                }
                .onFailure { failure ->
                    val auth = failure as? AuthException
                    loginState = LoginState.Failed(failure.message ?: "Неизвестная ошибка", auth?.hint)
                }
        }
    }

    fun signOut(uuid: String) {
        accounts = accounts.filterNot { it.uuid == uuid }
        if (activeUuid == uuid) activeUuid = accounts.firstOrNull()?.uuid
        persist()
    }

    fun selectAccount(uuid: String) {
        activeUuid = uuid
        persist()
    }

    private fun updateAccount(account: Account) {
        accounts = accounts.filterNot { it.uuid == account.uuid } + account
    }

    private fun persist() = accountStore.save(accounts, activeUuid)

    // ---- запуск игры ----

    fun play() {
        val account = activeAccount ?: run { screen = Screen.PROFILES; return }
        val versionId = selectedVersionId ?: return
        if (launching) return

        scope.launch {
            launching = true
            error = null
            logLines = emptyList()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    // Токен Minecraft живёт сутки — перед запуском обновляем при необходимости.
                    val fresh = if (account.isExpired) authenticator.refresh(account).also {
                        withContext(Dispatchers.Main) { updateAccount(it); persist() }
                    } else account

                    // Ванильная часть ставится всегда: загрузчики работают
                    // поверх неё через inheritsFrom и патчат её же клиент.
                    progress = Progress("Проверка файлов", 0, 1)
                    val vanilla = meta.resolveVersion(versionId, paths.versions)
                    installer.install(vanilla) { progress = it }

                    progress = Progress("Подготовка Java", 0, 1)
                    val java = javaProvisioner.provide(
                        component = vanilla.javaVersion?.component ?: "jre-legacy",
                        majorVersion = vanilla.javaVersion?.majorVersion ?: 8,
                        override = javaOverride,
                    ) { progress = it }

                    // Профиль загрузчика: Fabric отдаётся готовым, Forge/NeoForge
                    // собираются локально процессорами их инсталлятора.
                    val launchId = if (loader == Loader.VANILLA) versionId else {
                        val provider = LoaderRegistry.provider(loader)
                            ?: error("Загрузчик ${loader.displayName} не поддерживается")
                        val chosen = loaderVersion
                            ?: provider.availableVersions(versionId).firstOrNull()?.version
                            ?: error("Нет сборок ${loader.displayName} для версии $versionId")
                        provider.install(versionId, chosen, paths, java) { progress = it }
                    }

                    // Для загрузчика повторно раскрываем профиль: resolveVersion
                    // сольёт его с ванильным родителем и добавит библиотеки.
                    val version = if (launchId == versionId) vanilla
                    else meta.resolveVersion(launchId, paths.versions)
                    val installed = installer.install(version) { progress = it }

                    progress = Progress("Запуск JVM", 1, 1)
                    gameLauncher.launch(
                        java = java,
                        game = installed,
                        gameDir = paths.root,
                        account = fresh,
                        ramMb = ramMb,
                        onLine = { line -> logLines = (logLines + line).takeLast(500) },
                        onExit = { code ->
                            session = null
                            if (code != 0) error = "Игра завершилась с кодом $code"
                        },
                    )
                }
            }
            result
                .onSuccess { session = it }
                .onFailure { error = it.message }
            launching = false
        }
    }

    fun cancelLaunch() {
        session?.kill()
        session = null
        launching = false
    }

    private fun openBrowser(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }
}
