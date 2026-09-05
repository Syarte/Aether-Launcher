package dev.aether.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aether.ui.Avatar
import dev.aether.ui.LauncherState
import dev.aether.ui.LoginState

@Composable
fun ProfilesScreen(state: LauncherState) {
    Column(
        Modifier.fillMaxSize().padding(40.dp).widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Профили", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(
                onClick = { state.signIn() },
                enabled = state.loginState !is LoginState.Working,
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Добавить аккаунт")
            }
        }

        when (val login = state.loginState) {
            is LoginState.Working -> LoginCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Ожидание подтверждения в браузере", style = MaterialTheme.typography.bodyMedium)
                }
            }

            is LoginState.DeviceCode -> LoginCard {
                Text("Введите код на странице входа", style = MaterialTheme.typography.titleMedium)
                Text(
                    login.verificationUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text(
                        login.userCode,
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            is LoginState.Failed -> Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(login.message, color = MaterialTheme.colorScheme.onErrorContainer)
                    login.hint?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            LoginState.Idle -> Unit
        }

        if (state.accounts.isEmpty() && state.loginState is LoginState.Idle) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Пока нет ни одного аккаунта", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Вход выполняется через Microsoft. Лаунчер проверит лицензию Minecraft: Java Edition " +
                            "и получит игровой профиль — пароль в приложение не вводится.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.accounts.forEach { account ->
            val active = account.uuid == state.activeUuid
            Surface(
                onClick = { state.selectAccount(account.uuid) },
                color = if (active) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Avatar(account.username, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(account.username, style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.VerifiedUser, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Microsoft",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (active) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                            Text(
                                "Активен",
                                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    IconButton(onClick = { state.signOut(account.uuid) }) {
                        Icon(Icons.Default.Logout, "Выйти из аккаунта")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
