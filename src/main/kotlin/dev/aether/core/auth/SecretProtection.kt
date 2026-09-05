package dev.aether.core.auth

import com.sun.jna.platform.win32.Crypt32Util
import dev.aether.core.Platform

/**
 * Привязка ключа шифрования к учётной записи пользователя ОС.
 *
 * Windows (основная платформа): DPAPI, `CryptProtectData` в пользовательской
 * области. Ключ расшифровывается только под тем же аккаунтом Windows на той же
 * машине — копирование файлов на другой компьютер бесполезно.
 *
 * macOS и Linux: пока только права доступа `0600`. Это честно слабее — файл
 * с ключом лежит рядом с зашифрованными данными, и от чтения другим процессом
 * того же пользователя не защищает. Бэкенды Keychain и libsecret запланированы;
 * до тех пор `isHardened` возвращает false, и интерфейс сообщает об этом.
 */
object SecretProtection {

    val isHardened: Boolean = Platform.isWindows

    fun protect(data: ByteArray): ByteArray =
        if (Platform.isWindows) Crypt32Util.cryptProtectData(data) else data

    fun unprotect(data: ByteArray): ByteArray =
        if (Platform.isWindows) Crypt32Util.cryptUnprotectData(data) else data
}
