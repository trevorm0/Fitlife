package com.example.fitwell.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fitwell.ui.theme.*

/** Smooth-ish line chart with a soft fill. Drawn with Compose Canvas (no chart library). */
@Composable
fun LineChart(
    values: List<Float>, color: Color, modifier: Modifier = Modifier.fillMaxWidth().height(120.dp),
    xLabels: List<String> = emptyList(), description: String = "Line chart"
) {
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (values.size < 2) return@Canvas
            val min = values.min(); val max = values.max()
            val range = (max - min).takeIf { it > 0f } ?: 1f
            val stepX = size.width / (values.size - 1)
            fun y(v: Float) = size.height - ((v - min) / range) * (size.height * 0.85f) - size.height * 0.07f
            // grid lines
            repeat(3) { i ->
                val gy = size.height * (i + 1) / 4f
                drawLine(DividerBlue.copy(alpha = 0.6f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }
            val line = Path().apply {
                values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * stepX, y(v)) }
            }
            val fill = Path().apply {
                addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        if (xLabels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                xLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
            }
        }
    }
}

@Composable
fun BarChart(
    values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier.fillMaxWidth().height(140.dp),
    highlightLast: Boolean = true, description: String = "Bar chart"
) {
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (values.isEmpty()) return@Canvas
            val max = (values.max()).takeIf { it > 0f } ?: 1f
            val slot = size.width / values.size
            val barW = slot * 0.5f
            values.forEachIndexed { i, v ->
                val h = (v / max) * size.height
                val c = if (highlightLast && i != values.lastIndex) color.copy(alpha = 0.55f) else color
                drawRoundRect(c, Offset(i * slot + (slot - barW) / 2f, size.height - h), Size(barW, h), CornerRadius(6.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            labels.forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
