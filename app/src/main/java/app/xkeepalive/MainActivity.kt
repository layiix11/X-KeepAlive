package app.xkeepalive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.xkeepalive.ui.XBackgroundRoot
import app.xkeepalive.ui.theme.XBackgroundTheme
import app.xkeepalive.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            XBackgroundTheme {
                XBackgroundRoot(viewModel)
            }
        }
    }
}
