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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.security.SecureRandom

private enum class Screen { Dashboard, Controller, Receiver }
private enum class PairingStatus { Disconnected, Waiting, ApprovalPending, Connected }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FaldyViewApp() }
    }
}

@Composable
private fun FaldyViewApp() {
    var screen by remember { mutableStateOf(Screen.Dashboard) }
    var showSettings by remember { mutableStateOf(false) }
    var pairingStatus by remember { mutableStateOf(PairingStatus.Disconnected) }
    var pairingCode by remember { mutableStateOf("") }
    var enteredCode by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    val muted = Color(0xFF657086)

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F7FB)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
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

                when (screen) {
                    Screen.Dashboard -> {
                        StatusCard(pairingStatus, muted)
                        Text("Pilih mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Button(
                            onClick = {
                                if (pairingStatus == PairingStatus.Disconnected) {
                                    pairingCode = newPairingCode()
                                    pairingStatus = PairingStatus.Waiting
                                    enteredCode = ""
                                    feedback = null
                                }
                                screen = Screen.Controller
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(if (pairingStatus == PairingStatus.Disconnected) "Controller · Buat kode" else "Controller · Buka sesi") }
                        OutlinedButton(
                            onClick = { enteredCode = ""; feedback = null; screen = Screen.Receiver },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Receiver · Masukkan kode") }
                        DemoNotice(muted)
                        if (showSettings) SettingsNotice(muted)
                    }
                    Screen.Controller -> ControllerScreen(
                        status = pairingStatus,
                        code = pairingCode,
                        feedback = feedback,
                        onApprove = { pairingStatus = PairingStatus.Connected; feedback = "Persetujuan diberikan. Sesi lokal aktif." },
                        onReject = { pairingStatus = PairingStatus.Disconnected; pairingCode = ""; feedback = "Permintaan ditolak." },
                        onCancel = { pairingStatus = PairingStatus.Disconnected; pairingCode = ""; feedback = "Sesi dibatalkan." },
                        onDisconnect = { pairingStatus = PairingStatus.Disconnected; pairingCode = ""; feedback = "Sesi diputus." },
                        muted = muted
                    )
                    Screen.Receiver -> ReceiverScreen(
                        status = pairingStatus,
                        code = enteredCode,
                        feedback = feedback,
                        onCodeChange = { enteredCode = it.filter(Char::isDigit).take(6) },
                        onSubmit = {
                            if (pairingStatus == PairingStatus.Waiting && enteredCode.length == 6 && enteredCode == pairingCode) {
                                pairingStatus = PairingStatus.ApprovalPending
                                feedback = "Kode cocok. Menunggu persetujuan Controller pada prototipe lokal."
                            } else {
                                feedback = "Kode tidak valid atau tidak ada sesi lokal yang menunggu."
                            }
                        },
                        onCancel = { pairingStatus = PairingStatus.Disconnected; pairingCode = ""; feedback = "Permintaan dibatalkan." },
                        onDisconnect = { pairingStatus = PairingStatus.Disconnected; pairingCode = ""; feedback = "Sesi diputus." },
                        onReviewRequest = { screen = Screen.Controller },
                        muted = muted
                    )
                }

                Spacer(Modifier.weight(1f))
                Text("Berbagi layar dan kontrol belum tersedia.", style = MaterialTheme.typography.bodySmall, color = muted)
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
    status: PairingStatus,
    code: String,
    feedback: String?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    muted: Color
) {
    Text("Controller", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    StatusCard(status, muted)
    if (status == PairingStatus.Waiting) {
        Text("Kode pairing", style = MaterialTheme.typography.titleMedium)
        Text(code, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Bagikan kode ini kepada Receiver. Kode hanya berlaku pada prototipe di perangkat ini.", color = muted)
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Batalkan sesi") }
    }
    if (status == PairingStatus.ApprovalPending) {
        Text("Permintaan Receiver diterima. Pastikan Anda menyetujui perangkat yang benar.", color = muted)
        Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) { Text("Terima permintaan pairing") }
        OutlinedButton(onClick = onReject, modifier = Modifier.fillMaxWidth()) { Text("Tolak permintaan") }
    }
    if (status == PairingStatus.Connected) {
        Text("Persetujuan tercatat. Ini hanya status lokal; belum ada koneksi antarperangkat.", color = muted)
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Putuskan koneksi") }
    }
    feedback?.let { Text(it, color = muted) }
}

@Composable
private fun ReceiverScreen(
    status: PairingStatus,
    code: String,
    feedback: String?,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onReviewRequest: () -> Unit,
    muted: Color
) {
    Text("Receiver", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    StatusCard(status, muted)
    if (status == PairingStatus.Disconnected || status == PairingStatus.Waiting) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Kode pairing 6 digit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Button(onClick = onSubmit, enabled = code.length == 6, modifier = Modifier.fillMaxWidth()) { Text("Kirim permintaan") }
    }
    if (status == PairingStatus.ApprovalPending) {
        Text("Permintaan menunggu keputusan Controller. Memasukkan kode adalah tindakan Receiver untuk memulai pairing.", color = muted)
        OutlinedButton(onClick = onReviewRequest, modifier = Modifier.fillMaxWidth()) { Text("Tinjau di Controller") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Batalkan permintaan") }
    }
    if (status == PairingStatus.Connected) {
        Text("Persetujuan tercatat. Ini hanya status lokal; belum ada koneksi antarperangkat.", color = muted)
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Putuskan koneksi") }
    }
    feedback?.let { Text(it, color = muted) }
    DemoNotice(muted)
}

@Composable
private fun DemoNotice(muted: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
        Text(
            "Prototipe lokal: kode dan persetujuan hanya disimpan di memori aplikasi ini. Perangkat lain belum dapat terhubung.",
            Modifier.padding(16.dp), color = muted
        )
    }
}

@Composable
private fun SettingsNotice(muted: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
        Text("Pengaturan akan tersedia pada tahap berikutnya.", Modifier.padding(16.dp), color = muted)
    }
}

private fun PairingStatus.label(): String = when (this) {
    PairingStatus.Disconnected -> "Terputus"
    PairingStatus.Waiting -> "Menunggu Receiver"
    PairingStatus.ApprovalPending -> "Menunggu persetujuan pengguna"
    PairingStatus.Connected -> "Terhubung (status lokal prototipe)"
}

private fun newPairingCode(): String = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')
