package app.xkeepalive.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.xkeepalive.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun HomeAdBanner(enabled: Boolean) {
    if (!enabled || BuildConfig.ADMOB_BANNER_ID.isBlank()) return
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (enabled) Ads.start(context)
    }
    if (failed) return
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        factory = { viewContext ->
            AdView(viewContext).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        failed = true
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = AdView::destroy,
    )
}

private object Ads {
    private val started = AtomicBoolean(false)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(context.applicationContext)
    }
}
