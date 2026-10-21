package fr.alex.chambre.ui.cuisine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.alex.chambre.mealie.OpenedRecipe
import fr.alex.chambre.mealie.Recipe
import fr.alex.chambre.mealie.findTimers
import fr.alex.chambre.mealie.formatQuantity
import fr.alex.chambre.mealie.ingredientLine
import fr.alex.chambre.mealie.minutesLabel
import fr.alex.chambre.mealie.stepHeading

private val CURRENT_STEP_BG = Color(0xFF6A3B00)
private val CURRENT_STEP_TEXT = Color(0xFFFFF3E6)
private val CURRENT_STEP_TITLE = Color(0xFFFFDCBE)

/** Full-screen recipe: servings, ingredients to tick, steps with timer chips inside the sentences. */
@Composable
fun RecipeScreen(
    opened: OpenedRecipe,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTimer: (seconds: Int, label: String) -> Unit,
    /** Portrait phone: ingredients above the steps, the whole screen scrolls. */
    stacked: Boolean = false,
) {
    when (opened) {
        is OpenedRecipe.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is OpenedRecipe.Failed -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(opened.message, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("Retour") }
                Button(onClick = onRetry) { Text("Réessayer") }
            }
        }
        is OpenedRecipe.Ready -> RecipeContent(opened.recipe, onBack, onTimer, stacked)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeContent(recipe: Recipe, onBack: () -> Unit, onTimer: (Int, String) -> Unit, stacked: Boolean) {
    var servings by remember(recipe.slug) { mutableDoubleStateOf(recipe.baseServings) }
    var ticked by remember(recipe.slug) { mutableStateOf(emptySet<Int>()) }
    var current by remember(recipe.slug) { mutableIntStateOf(0) }
    val factor = servings / recipe.baseServings
    val colors = MaterialTheme.colorScheme
    val stepper: @Composable () -> Unit = {
        ServingsStepper(
            servings = servings,
            onLess = { servings = (servings - 1).coerceAtLeast(1.0) },
            onMore = { servings = (servings + 1).coerceAtMost(24.0) },
        )
    }

    val outer = if (stacked) Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp) else Modifier.fillMaxSize().padding(24.dp)
    Column(outer, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.heightIn(min = 88.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(60.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour aux recettes", Modifier.size(28.dp))
            }
            RecipeImage(recipe.imageUrl, Modifier.width(116.dp).height(84.dp).clip(RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(recipe.name, fontSize = (if (stacked) 22 else 28).sp, lineHeight = (if (stacked) 26 else 32).sp, color = colors.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val times = listOfNotNull(
                    recipe.prepMinutes?.let { "Préparation ${minutesLabel(it)}" },
                    recipe.cookMinutes?.let { "Cuisson ${minutesLabel(it)}" },
                    recipe.totalMinutes?.let { "Total ${minutesLabel(it)}" },
                )
                if (times.isNotEmpty()) Text(times.joinToString(if (stacked) "\n" else "   "), fontSize = 15.sp, color = colors.onSurfaceVariant)
            }
            if (!stacked) stepper()
        }
        if (stacked) stepper()

        Split(stacked) { first, second ->
            Panel(first) {
                PanelTitle("Ingrédients", "${ticked.size} / ${recipe.ingredients.size} prêts")
                if (recipe.ingredients.any { !it.scalable && it.food == null }) {
                    Text(
                        "Certains ingrédients ne sont pas analysés dans Mealie : leurs quantités ne suivent pas les portions.",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                ItemList(recipe.ingredients, lazy = !stacked, spacing = 2) { i, ing ->
                    val section = ing.section
                    if (section != null && (i == 0 || recipe.ingredients[i - 1].section != section)) {
                        Text(
                            section,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp),
                        )
                    }
                    IngredientRow(
                        line = ingredientLine(ing, factor),
                        done = i in ticked,
                        onToggle = { ticked = if (i in ticked) ticked - i else ticked + i },
                    )
                }
            }
            Panel(second) {
                PanelTitle("Étapes", if (recipe.steps.isEmpty()) "" else "Étape ${current + 1} sur ${recipe.steps.size}")
                val list = rememberLazyListState()
                // Stacked (phone): the whole screen scrolls, so the current step is brought into view instead.
                val currentStep = remember { BringIntoViewRequester() }
                LaunchedEffect(current) { if (stacked) currentStep.bringIntoView() else list.animateScrollToItem(current) }
                ItemList(recipe.steps, lazy = !stacked, spacing = 6, modifier = if (stacked) Modifier else Modifier.weight(1f), state = list) { i, step ->
                    val on = i == current
                    Column(
                        Modifier.fillMaxWidth()
                            .then(if (on) Modifier.bringIntoViewRequester(currentStep) else Modifier)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) CURRENT_STEP_BG else Color.Transparent)
                            .padding(if (on) 16.dp else 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(onClick = { current = i }, color = Color.Transparent, shape = RoundedCornerShape(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(if (on) colors.primary else colors.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (on) colors.onPrimary else colors.onSurface)
                                }
                                val title = stepHeading(step, current = on)
                                Text(
                                    title,
                                    fontSize = if (on) 15.sp else 17.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        on -> CURRENT_STEP_TITLE
                                        i < current -> colors.onSurfaceVariant.copy(alpha = 0.7f)
                                        else -> colors.onSurface
                                    },
                                    maxLines = if (on) 2 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (on && step.text.isNotBlank()) {
                            StepText(
                                text = step.text,
                                onTimer = { seconds -> onTimer(seconds, step.title.ifBlank { recipe.name }) },
                                modifier = Modifier.padding(start = 50.dp),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { current = (current - 1).coerceAtLeast(0) }, enabled = current > 0, modifier = Modifier.weight(1f).height(60.dp)) {
                        Text("Étape précédente", fontSize = 18.sp)
                    }
                    Button(onClick = { current = (current + 1).coerceAtMost(recipe.steps.lastIndex) }, enabled = current < recipe.steps.lastIndex, modifier = Modifier.weight(1.4f).height(60.dp)) {
                        Text("Étape suivante", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** The step text with every duration replaced, in the sentence, by a tappable timer chip. */
@Composable
private fun StepText(text: String, onTimer: (Int) -> Unit, modifier: Modifier = Modifier) {
    val timers = remember(text) { findTimers(text) }
    val textStyle = TextStyle(fontSize = 21.sp, lineHeight = 34.sp, color = CURRENT_STEP_TEXT)
    val chipStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val annotated = buildAnnotatedString {
        var last = 0
        timers.forEachIndexed { i, t ->
            append(text.substring(last, t.start))
            appendInlineContent("timer$i", text.substring(t.start, t.end))
            last = t.end
        }
        append(text.substring(last))
    }
    val inline = timers.mapIndexed { i, t ->
        val label = text.substring(t.start, t.end)
        // Chip width = its label + icon + paddings, so the sentence flows around it exactly.
        val labelWidth = with(density) { measurer.measure(label, chipStyle).size.width.toDp() }
        val width = labelWidth + 17.dp + 6.dp + 8.dp + 12.dp + 4.dp
        "timer$i" to InlineTextContent(
            Placeholder(with(density) { width.toSp() }, 30.sp, PlaceholderVerticalAlign.TextCenter),
        ) {
            Surface(
                onClick = { onTimer(t.seconds) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            ) {
                Row(Modifier.padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Timer, "Lancer une minuterie de ${t.label}", Modifier.size(17.dp))
                    Text(label, style = chipStyle, maxLines = 1)
                }
            }
        }
    }.toMap()
    Text(annotated, modifier = modifier, style = textStyle, inlineContent = inline)
}

@Composable
private fun IngredientRow(line: fr.alex.chambre.mealie.IngredientLine, done: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dim = colors.onSurfaceVariant.copy(alpha = 0.6f)
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        color = if (done) Color.Transparent else colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (done) colors.primary else Color.Transparent)
                    .border(BorderStroke(2.dp, if (done) colors.primary else dim), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = colors.onPrimary)
            }
            Text(
                buildAnnotatedString {
                    line.quantity?.let {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (done) dim else colors.primary)) { append(it) }
                        append(" ")
                    }
                    append(line.text)
                    line.note?.let { withStyle(SpanStyle(color = if (done) dim else colors.onSurfaceVariant)) { append(" · $it") } }
                },
                fontSize = 16.sp,
                color = if (done) dim else colors.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
        }
    }
}

@Composable
private fun ServingsStepper(servings: Double, onLess: () -> Unit, onMore: () -> Unit) {
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onLess, enabled = servings > 1.0, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Remove, "Une portion de moins")
            }
            Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatQuantity(servings, null), fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Text(if (servings > 1.0) "portions" else "portion", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = onMore, enabled = servings < 24.0, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Add, "Une portion de plus")
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxHeight(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun PanelTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(detail, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Two panels side by side (tablet), or one under the other (phone). */
@Composable
private fun ColumnScope.Split(stacked: Boolean, content: @Composable (first: Modifier, second: Modifier) -> Unit) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content(Modifier.fillMaxWidth(), Modifier.fillMaxWidth()) }
    } else {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) { content(Modifier.width(390.dp), Modifier.weight(1f)) }
    }
}

/** A lazy list on the tablet; a plain column on the phone, where the whole screen scrolls. */
@Composable
private fun <T> ItemList(
    items: List<T>,
    lazy: Boolean,
    spacing: Int,
    modifier: Modifier = Modifier,
    state: LazyListState? = null,
    item: @Composable (Int, T) -> Unit,
) {
    if (lazy) {
        LazyColumn(modifier, state = state ?: rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
            itemsIndexed(items) { i, t -> item(i, t) }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.dp)) { items.forEachIndexed { i, t -> item(i, t) } }
    }
}
