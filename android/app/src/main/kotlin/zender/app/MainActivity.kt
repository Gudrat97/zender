package zender.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ZenderScreen(viewModel())
                }
            }
        }
    }
}

@Composable
fun ZenderScreen(vm: AppViewModel) {
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(vm::addVideo)
    }
    var keyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Zender", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = vm.goal,
            onValueChange = { vm.goal = it },
            label = { Text("Задача, например: 30-секундное видео про кофе") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        OutlinedTextField(
            value = vm.agentName,
            onValueChange = { vm.agentName = it },
            label = { Text("Агент (пусто = полный цикл). Например: trends, qa") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedButton(onClick = { pickVideo.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("Добавить видео")
        }
        vm.inputs.forEach { Text("• ${it.substringAfterLast('/')}", style = MaterialTheme.typography.bodySmall) }

        Button(
            onClick = vm::run,
            enabled = !vm.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.running) "Работаю…" else "Запустить")
        }

        HorizontalDivider()

        Text("Журнал", style = MaterialTheme.typography.titleMedium)
        vm.log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }

        HorizontalDivider()

        Text(
            if (vm.hasKey) "Ключ API сохранён" else "Введите ключ API Anthropic",
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("Ключ API") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedButton(
            onClick = { vm.saveKey(keyInput); keyInput = "" },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить ключ")
        }
    }
}
