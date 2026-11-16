package com.example.fitwell.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.fitwell.data.local.FitnessGoalEntity
import com.example.fitwell.data.local.WorkoutEntity
import com.example.fitwell.ui.theme.*

// ---------------- base pieces ----------------

/** Rounded navy card; clickable when [onClick] is given. */
@Composable
fun FitLifeCard(
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    containerColor: Color = CardNavy, borderColor: Color = DividerBlue.copy(alpha = 0.6f),
    shape: androidx.compose.ui.graphics.Shape = FitLifeDimens.cardShape,
    content: @Composable ColumnScope.() -> Unit
) {
    val m = if (onClick != null) modifier.clip(shape).clickable(role = Role.Button, onClick = onClick) else modifier
    Card(
        modifier = m, shape = shape, border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = TextPrimary),
        content = content
    )
}

/** Drawable lookup by name with a gradient placeholder, so the UI works before photos are added. */
@Composable
fun FitLifeImage(
    reference: String?, contentDescription: String?, modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Default.FitnessCenter
) {
    val ctx = LocalContext.current
    val resId = remember(reference) {
        if (reference.isNullOrBlank()) 0 else ctx.resources.getIdentifier(reference, "drawable", ctx.packageName)
    }
    if (resId != 0) {
        AsyncImage(model = resId, contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier.background(Brush.linearGradient(listOf(CardElevated, NavyDark, Color(0xFF0A3B3B)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(fallbackIcon, contentDescription = contentDescription, tint = FitGreen.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun RingProgress(
    progress: Float, color: Color, modifier: Modifier = Modifier.size(64.dp),
    strokeWidth: Dp = 6.dp, content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = strokeWidth.toPx()
            val arcSize = Size(size.width - s, size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(DividerBlue, 0f, 360f, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Label + colour, so status is never communicated by colour alone. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionText, color = FitGreen, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
fun FitLifeTopBar(
    title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
        } else Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

// ---------------- metric / health cards ----------------

/** Ring + icon + label + value, as on the Home dashboard (Steps / Calories / Heart rate). */
@Composable
fun FitnessMetricCard(
    label: String, value: String, caption: String, progress: Float, color: Color, icon: ImageVector, modifier: Modifier = Modifier
) {
    FitLifeCard(modifier, shape = FitLifeDimens.metricShape) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RingProgress(progress, color, Modifier.size(60.dp)) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp)) }
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
fun CircularProgressIndicatorCard(
    progress: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 112.dp, content: @Composable BoxScope.() -> Unit
) = RingProgress(progress, color, modifier.size(size), 10.dp, content)

@Composable
fun HealthMetricCard(
    title: String, value: String, subtitle: String, icon: ImageVector, color: Color,
    modifier: Modifier = Modifier, progress: Float? = null
) {
    FitLifeCard(modifier, shape = FitLifeDimens.metricShape) {
        Column(Modifier.padding(FitLifeDimens.cardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(36.dp).background(color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = color, trackColor = DividerBlue
                )
            }
        }
    }
}

@Composable
fun NutritionProgressCard(label: String, value: Int, target: Int, color: Color, unit: String = "g", modifier: Modifier = Modifier) {
    val progress = if (target > 0) value / target.toFloat() else 0f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("$value / $target $unit", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).semantics { contentDescription = "$label $value of $target $unit" },
            color = color, trackColor = DividerBlue
        )
    }
}

// ---------------- workout cards ----------------

@Composable
fun WorkoutCard(workout: WorkoutEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FitLifeCard(modifier, onClick = onClick) {
        Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.CenterVertically) {
            FitLifeImage(workout.imageReference, "${workout.name} workout", Modifier.width(96.dp).fillMaxHeight())
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(workout.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${workout.durationMinutes} min • ${workout.difficulty.label}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

@Composable
fun FeaturedWorkoutCard(workout: WorkoutEntity, onStart: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    FitLifeCard(modifier, onClick = onOpen, shape = FitLifeDimens.cardShape) {
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
            FitLifeImage(workout.imageReference, null, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(listOf(Midnight.copy(alpha = 0.92f), Midnight.copy(alpha = 0.55f), Color.Transparent))
                )
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today's Workout", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(workout.name, style = MaterialTheme.typography.titleMedium)
                Text("${workout.durationMinutes} min • ${workout.difficulty.label}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                FitLifePrimaryButton("Start Workout", onStart, icon = Icons.Default.PlayArrow, modifier = Modifier.heightIn(min = 44.dp))
            }
        }
    }
}

// ---------------- goals / profile ----------------

@Composable
fun GoalProgressCard(
    title: String, description: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier,
    progress: Float? = null, selected: Boolean = false, onClick: (() -> Unit)? = null
) {
    FitLifeCard(
        modifier, onClick = onClick,
        containerColor = if (selected) CardElevated else CardNavy,
        borderColor = if (selected) FitGreen else DividerBlue.copy(alpha = 0.6f)
    ) {
        Row(Modifier.padding(FitLifeDimens.cardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).background(color.copy(alpha = 0.22f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = FitGreen, trackColor = DividerBlue)
                }
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
fun ProfileHeader(name: String, tagline: String, modifier: Modifier = Modifier, imageReference: String? = null) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Avatar(name, imageReference, Modifier.size(96.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(tagline, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

/** Circular avatar: a drawable by name, otherwise the user's initials. */
@Composable
fun Avatar(name: String, imageReference: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val resId = remember(imageReference) {
        if (imageReference.isNullOrBlank()) 0 else ctx.resources.getIdentifier(imageReference, "drawable", ctx.packageName)
    }
    Box(modifier.clip(CircleShape).background(CardElevated).border(2.dp, FitGreen, CircleShape), contentAlignment = Alignment.Center) {
        if (resId != 0) AsyncImage(resId, "Profile photo of $name", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
            if (initials.isBlank()) Icon(Icons.Default.Person, contentDescription = "Profile photo placeholder", tint = FitGreen)
            else Text(initials, style = MaterialTheme.typography.titleMedium, color = FitGreen)
        }
    }
}

