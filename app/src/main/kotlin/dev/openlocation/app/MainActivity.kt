package dev.openlocation.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.openlocation.app.ui.*
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        dev.openlocation.app.data.TargetAccess.refreshGrants(this)
        setContent {
            val vm: SceneViewModel = viewModel()
            val state by vm.ui.collectAsStateWithLifecycle()
            OpenLocationTheme(state.dynamicColor) { OpenLocationApp(state, vm) }
        }
    }
}
