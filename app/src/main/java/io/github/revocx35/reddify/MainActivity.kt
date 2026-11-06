package io.github.revocx35.reddify

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.github.revocx35.reddify.ui.ReddifyRoot

/** AppCompat (not plain ComponentActivity): the Cast device picker is an AppCompat dialog fragment. */
class MainActivity : AppCompatActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        val app = application as ReddifyApp
        setContent { ReddifyRoot(app.graph, app.player) }
    }
}
