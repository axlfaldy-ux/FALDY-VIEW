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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Dashboard() }
    }
}

@Composable
private fun Dashboard() {
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
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
                    OutlinedButton(onClick = { showSettings = !showSettings }) { Text("Pengaturan") }
                }
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Status koneksi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Belum terhubung", color = muted)
                    }
                }
                Text("Pilih mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Button(onClick = { selectedMode = "Controller" }, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Controller")
                }
                OutlinedButton(onClick = { selectedMode = "Receiver" }, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Receiver")
                }
                selectedMode?.let { Text("Mode $it dipilih. Fitur koneksi belum tersedia.", color = muted) }
                if (showSettings) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                        Text("Pengaturan akan tersedia pada tahap berikutnya.", Modifier.padding(16.dp), color = muted)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("Berbagi layar hanya akan berjalan dengan persetujuan pengguna.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}
