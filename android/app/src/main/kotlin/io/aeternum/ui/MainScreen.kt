package io.aeternum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 主屏幕 - Compose UI 入口
 */
@Composable
fun MainScreen(
    vaultState: io.aeternum.data.VaultState,
    onUnlock: (String) -> Unit,
    onInitialize: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (vaultState) {
        is io.aeternum.data.VaultState.Locked -> LockedScreen(onUnlock = onUnlock)
        is io.aeternum.data.VaultState.NotInitialized -> SetupScreen(onInitialize = onInitialize)
        is io.aeternum.data.VaultState.Unlocked -> VaultScreen()
    }
}

/**
 * 锁定屏幕 - 密码输入
 */
@Composable
fun LockedScreen(onUnlock: (String) -> Unit) {
    var password by androidx.compose.runtime.mutableStateOf("")

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Aeternum",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onUnlock(password) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("解锁")
            }
        }
    }
}

/**
 * 设置屏幕 - 初始化新 Vault
 */
@Composable
fun SetupScreen(onInitialize: (String) -> Unit) {
    var password by androidx.compose.runtime.mutableStateOf("")
    var confirmPassword by androidx.compose.runtime.mutableStateOf("")

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "创建 Vault",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("主密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("确认密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onInitialize(password) },
                enabled = password.isNotEmpty() && password == confirmPassword,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("创建")
            }
        }
    }
}

/**
 * Vault 屏幕 - 解锁后的主界面
 */
@Composable
fun VaultScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "我的 Vault",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "这里将显示加密的条目列表",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
