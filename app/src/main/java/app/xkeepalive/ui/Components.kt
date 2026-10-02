package app.xkeepalive.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.xkeepalive.R
import app.xkeepalive.ui.theme.Line
import app.xkeepalive.ui.theme.Panel

@Composable
fun LogoMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_keepalive_logo),
        contentDescription = "X-KeepAlive",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(64.dp),
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
fun StatusDot(color: Color, pulsing: Boolean) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "alpha",
        )
        animated
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun StatusChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun KeyValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
    }
}
