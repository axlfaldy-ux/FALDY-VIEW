package com.faldy.remoteview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.faldy.remoteview.pairing.PairingRole
import com.faldy.remoteview.pairing.PairingStatus
import com.faldy.remoteview.pairing.PairingUiState
import com.faldy.remoteview.pairing.PairingViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Screen { Dashboard, Controller, Receiver }

class MainActivity : ComponentActivity() {
    private lateinit var pairingViewModel: PairingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingViewModel = ViewModelProvider(
            this,
            PairingViewModel.Factory(BuildConfig.API_BASE_URL)
        )[PairingViewModel::class.java]
        setContent { FaldyViewApp(pairingViewModel) }
    }
}

@Composable
private fun FaldyViewApp(pairingViewModel: PairingViewModel) {
    var screen by remember { mutableStateOf(Screen.Dashboard) }
    var showSettings by remember { mutableStateOf(false) }
    var enteredCode by remember { mutableStateOf("") }
    val state by pairingViewModel.state.collectAsState()
    val muted = Color(0xFF657086)

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F7FB)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("FALDY VIEW", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Remote viewing, with consent", color = muted)
                    }
                    if (screen == Screen.Dashboard) {
                        OutlinedButton(onClick = { showSettings = !showSettings }) { Text("Pengaturan") }
                    } else {
                        OutlinedButton(onClick = { screen = Screen.Dashboard }) { Text("Kembali") }
                    }
                }

                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { ErrorNotice(it) }
                if (state.isReconnecting) Text("Mencoba menyambung kembali dengan aman…", color = muted)

                when (screen) {
                    Screen.Dashboard -> {
                        StatusCard(state.status, muted)
                        Text("Pilih mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Button(
                            onClick = {
                                if (state.role != PairingRole.Controller) pairingViewModel.createSession()
                                screen = Screen.Controller
                            },
                            enabled = !state.isLoading && (state.sessionId == null || state.role == PairingRole.Controller),
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (state.sessionId == null || state.role != PairingRole.Controller) "Controller · Buat kode" else "Controller · Buka sesi")
                        }
                        OutlinedButton(
                            onClick = { enteredCode = ""; screen = Screen.Receiver },
                            enabled = !state.isLoading && (state.sessionId == null || state.role == PairingRole.Receiver),
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Receiver · Masukkan kode") }
                        if (showSettings) SettingsNotice(BuildConfig.API_BASE_URL, muted)
                    }
                    Screen.Controller -> ControllerScreen(
                        state = state,
                        onCreate = pairingViewModel::createSession,
                        onApprove = pairingViewModel::approve,
                        onReject = pairingViewModel::reject,
                        onCancel = pairingViewModel::cancel,
                        onDisconnect = pairingViewModel::disconnect,
                        muted = muted
                    )
                    Screen.Receiver -> ReceiverScreen(
                        state = state,
                        code = enteredCode,
                        onCodeChange = { enteredCode = it.filter(Char::isDigit).take(6) },
                        onJoin = { pairingViewModel.joinSession(enteredCode) },
                        onLeave = pairingViewModel::leave,
                        onDisconnect = pairingViewModel::disconnect,
                        muted = muted
                    )
                }

                Spacer(Modifier.weight(1f))
                Text("Pairing memakai API HTTPS. Berbagi layar dan kontrol belum tersedia.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

@Composable
private fun StatusCard(status: PairingStatus, muted: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Status koneksi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(status.label(), color = muted)
        }
    }
}

@Composable
private fun ControllerScreen(
    state: PairingUiState,
    onCreate: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    muted: Color
) {
    Text("Controller", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    StatusCard(state.status, muted)
    if (state.status == PairingStatus.Disconnected) {
        Button(onClick = onCreate, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Buat sesi dan kode") }
    }
    if (state.role == PairingRole.Controller && state.status == PairingStatus.Waiting) {
        Text("Kode pairing", style = MaterialTheme.typography.titleMedium)
        Text(state.pairingCode.orEmpty(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        state.expiresAt?.let { Text("Berlaku sampai ${formatExpiry(it)}", color = muted) }
        Text("Bagikan kode ini kepada Receiver. Kode sekali pakai.", color = muted)
        OutlinedButton(onClick = onCancel, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Batalkan sesi") }
    }
    if (state.role == PairingRole.Controller && state.status == PairingStatus.ApprovalPending) {
        Text("Receiver meminta bergabung. Tinjau permintaan sebelum menerima.", color = muted)
        Button(onClick = onApprove, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Setujui pairing") }
        OutlinedButton(onClick = onReject, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Tolak permintaan") }
    }
    if (state.role == PairingRole.Controller && state.status == PairingStatus.Connected) {
        Text("Pairing disetujui. Belum ada koneksi media atau kontrol layar.", color = muted)
        OutlinedButton(onClick = onCancel, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Akhiri sesi") }
    }
}

@Composable
private fun ReceiverScreen(
    state: PairingUiState,
    code: String,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onDisconnect: () -> Unit,
    muted: Color
) {
    Text("Receiver", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    StatusCard(state.status, muted)
    if (state.sessionId == null) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Kode pairing 6 digit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Button(onClick = onJoin, enabled = code.length == 6 && !state.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Kirim permintaan bergabung")
        }
    }
    if (state.role == PairingRole.Receiver && state.status == PairingStatus.ApprovalPending) {
        Text("Permintaan terkirim. Menunggu persetujuan Controller.", color = muted)
        state.expiresAt?.let { Text("Permintaan kedaluwarsa ${formatExpiry(it)}", color = muted) }
        OutlinedButton(onClick = onLeave, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Batalkan permintaan") }
    }
    if (state.role == PairingRole.Receiver && state.status == PairingStatus.Connected) {
        Text("Pairing disetujui. Berbagi layar belum dimulai.", color = muted)
        OutlinedButton(onClick = onDisconnect, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) { Text("Putuskan sesi") }
    }
}

@Composable
private fun ErrorNotice(message: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFEEEE)) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = Color(0xFF9B1C1C))
    }
}

@Composable
private fun SettingsNotice(baseUrl: String, muted: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("API pairing", fontWeight = FontWeight.SemiBold)
            Text(baseUrl.ifBlank { "Belum dikonfigurasi" }, color = muted)
            Text("Atur Gradle property faldyApiBaseUrl; gunakan HTTPS untuk server publik.", color = muted)
        }
    }
}

private fun PairingStatus.label(): String = when (this) {
    PairingStatus.Disconnected -> "Terputus"
    PairingStatus.Waiting -> "Menunggu Receiver"
    PairingStatus.ApprovalPending -> "Menunggu persetujuan Controller"
    PairingStatus.Connected -> "Pairing disetujui"
}

private fun formatExpiry(instant: java.time.Instant): String =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(instant)
