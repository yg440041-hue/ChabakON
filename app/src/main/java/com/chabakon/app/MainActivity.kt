package com.chabakon.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.chabakon.app.ui.C
import com.chabakon.app.ui.ChabakApp
import com.chabakon.app.ui.chabakTypography

class MainActivity : ComponentActivity() {

    private val vm: ChabakViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 음성 정지('멈춰')에 쓸 마이크 권한 (처음 한 번만 물어봄)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = C.amber,
                    onPrimary = C.onAmber,
                    background = C.bg,
                    surface = C.card,
                    onSurface = C.text,
                    onBackground = C.text
                ),
                typography = chabakTypography()
            ) {
                ChabakApp(vm)
            }
        }
    }
}
