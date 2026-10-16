package fr.alex.chambre.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.EntityState

private fun sceneIcon(entityId: String): ImageVector = when (entityId) {
    "script.chambre_scene_coucher" -> Icons.Filled.Bedtime
    "script.chambre_scene_lecture" -> Icons.AutoMirrored.Filled.MenuBook
    "script.chambre_scene_reveil" -> Icons.Filled.WbTwilight
    else -> Icons.Filled.NightsStay
}

@Composable
fun ScenesPanel(
    entities: Map<String, EntityState>,
    lastScene: String?,
    enabled: Boolean,
    onRun: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Section("Ambiance", modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Entities.SCENES.forEach { scene ->
                SceneTile(
                    label = scene.label,
                    icon = sceneIcon(scene.entityId),
                    selected = scene.entityId == lastScene,
                    running = entities[scene.entityId]?.state == "on",
                    enabled = enabled,
                    onClick = { onRun(scene.entityId) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SceneTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        when {
            pressed -> 16.dp
            selected -> 44.dp
            else -> 24.dp
        },
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "corner",
    )
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, label = "container")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, label = "content")
    val pulse = if (running) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f, targetValue = 0.96f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse",
        ).value
    } else 1f

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier.scale(pulse),
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = CircleShape, color = if (selected) colors.onPrimary.copy(alpha = 0.12f) else colors.surfaceContainerHighest, modifier = Modifier.fillMaxSize()) {}
                Icon(icon, null, Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}
