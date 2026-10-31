package cl.logisticaverde.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import cl.logisticaverde.app.ui.theme.CieloBlanco
import cl.logisticaverde.app.ui.theme.CieloClaro
import cl.logisticaverde.app.ui.navigation.LogisticaVerdeNavHost
import cl.logisticaverde.app.ui.theme.LogisticaVerdeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogisticaVerdeTheme {
                // Fondo de toda la app: degradado celeste → blanco (también detrás de la barra de estado)
                Scaffold(
                    containerColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(CieloClaro, CieloBlanco)))
                ) { innerPadding ->
                    LogisticaVerdeNavHost(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
