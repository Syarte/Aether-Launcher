package dev.aether.core.auth

import dev.aether.core.Platform
import dev.aether.core.net.Http
import kotlinx.serialization.Serializable
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

@Serializable
private data class AccountsFile(val accounts: List<Account> = emptyList(), val activeUuid: String? = null)

/**
 * Хранилище аккаунтов.
 *
 * На диск попадает только refresh-токен и профиль, зашифрованные AES-256-GCM.
 * Сам ключ защищён средствами ОС (см. [SecretProtection]): на Windows — DPAPI,
 * то есть перенос файлов на другую машину или другого пользователя ничего
 * не даёт. Токен Minecraft (24 ч) не сохраняется вообще — он перевыпускается
 * при каждом старте, поэтому украденный файл не даёт войти в игру.
 */
class AccountStore(private val dir: File = File(Platform.dataDir, "accounts")) {

    private val dataFile = File(dir, "accounts.enc")
    private val keyFile = File(dir, "key.bin")

    init {
        dir.mkdirs()
        runCatching { restrictPermissions(dir) }
    }

    fun load(): Pair<List<Account>, String?> {
        if (!dataFile.isFile) return emptyList<Account>() to null
        return runCatching {
            val plain = decrypt(dataFile.readBytes())
            val parsed = Http.json.decodeFromString(AccountsFile.serializer(), plain)
            parsed.accounts to parsed.activeUuid
        }.getOrElse { emptyList<Account>() to null }
    }

    fun save(accounts: List<Account>, activeUuid: String?) {
        val payload = Http.json.encodeToString(AccountsFile.serializer(), AccountsFile(accounts, activeUuid))
        dataFile.writeBytes(encrypt(payload))
        runCatching { restrictPermissions(dataFile) }
    }

    fun clear() {
        dataFile.delete()
    }

    // ---- шифрование ----

    private fun key(): SecretKey {
        if (keyFile.isFile) {
            runCatching { return SecretKeySpec(SecretProtection.unprotect(keyFile.readBytes()), "AES") }
        }
        val generated = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        keyFile.writeBytes(SecretProtection.protect(generated.encoded))
        runCatching { restrictPermissions(keyFile) }
        return generated
    }

    private fun encrypt(plain: String): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain.toByteArray())
    }

    private fun decrypt(blob: ByteArray): String {
        val iv = blob.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(blob.copyOfRange(12, blob.size)))
    }

    private fun restrictPermissions(f: File) {
        if (Platform.isWindows) {
            f.setReadable(false, false); f.setReadable(true, true)
            f.setWritable(false, false); f.setWritable(true, true)
        } else {
            val perms = if (f.isDirectory) "rwx------" else "rw-------"
            java.nio.file.Files.setPosixFilePermissions(
                f.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString(perms)
            )
        }
    }
}
