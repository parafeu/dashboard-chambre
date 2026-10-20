package fr.alex.chambre.ui.cuisine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import fr.alex.chambre.ha.ConnState
import fr.alex.chambre.ha.Entities
import fr.alex.chambre.ha.temperature
import fr.alex.chambre.mealie.RecipeFilter
import fr.alex.chambre.mealie.RecipeSummary
import fr.alex.chambre.mealie.TodayBanner
import fr.alex.chambre.mealie.cardMeta
import fr.alex.chambre.mealie.filterRecipes
import fr.alex.chambre.mealie.filtersFor
import fr.alex.chambre.mealie.todaysBanner
import fr.alex.chambre.ui.CoverPanel
import fr.alex.chambre.ui.DashboardViewModel
import fr.alex.chambre.ui.HeaderBar
import kotlinx.coroutines.launch

/** The kitchen tablet: header, kitchen shutter and the Mealie recipes; a recipe opens full screen. */
@Composable
fun CuisineApp(vm: DashboardViewModel, onOpenSettings: () -> Unit) {
    val entities by vm.ha.entities.collectAsStateWithLifecycle()
    val forecast by vm.ha.forecast.collectAsStateWithLifecycle()
    val forecastDaily by vm.ha.forecastDaily.collectAsStateWithLifecycle()
    val conn by vm.ha.conn.collectAsStateWithLifecycle()
    val battery by vm.battery.state.collectAsStateWithLifecycle()
    val recipes by vm.cuisine.recipes.collectAsStateWithLifecycle()
    val today by vm.cuisine.today.collectAsStateWithLifecycle()
    val error by vm.cuisine.error.collectAsStateWithLifecycle()
    val opened by vm.cuisine.opened.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.ha.errors.collect { snackbar.showSnackbar(it) } }
    // Cooking: the screen never sleeps while a recipe is open.
    LaunchedEffect(opened != null) { vm.presence.hold = opened != null }

    val online = conn == ConnState.CONNECTED
    val weather = entities[Entities.WEATHER]
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            val recipe = opened
            if (recipe != null) {
                BackHandler { vm.cuisine.close() }
                RecipeScreen(
                    opened = recipe,
                    onBack = vm.cuisine::close,
                    onRetry = { vm.cuisine.open(recipe.slug) },
                    onTimer = { seconds, label -> scope.launch { snackbar.showSnackbar(startSystemTimer(context, seconds, label)) } },
                )
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeaderBar(
                        currentCondition = weather?.state?.takeUnless { it == "unavailable" || it == "unknown" },
                        currentTemp = weather?.temperature,
                        hourly = forecast,
                        daily = forecastDaily,
                        online = online,
                        battery = battery,
                        onOpenSettings = onOpenSettings,
                    )
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        CoverPanel(entities[Entities.KITCHEN_COVER], online, vm.kitchenController, Modifier.width(236.dp))
                        RecipesPanel(
                            recipes = recipes,
                            banner = todaysBanner(today, recipes),
                            error = error,
                            onOpen = vm.cuisine::open,
                            onRetry = vm.cuisine::refresh,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp))
        }
    }
}

@Composable
private fun RecipesPanel(
    recipes: List<RecipeSummary>,
    banner: TodayBanner?,
    error: String?,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf<RecipeFilter>(RecipeFilter.All) }
    val filters = filtersFor(recipes)
    if (filter !in filters) filter = RecipeFilter.All
    val shown = filterRecipes(recipes, query, filter)
    val colors = MaterialTheme.colorScheme

    Surface(modifier, shape = RoundedCornerShape(32.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Recettes",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                SearchBox(query, { query = it }, Modifier.width(300.dp))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { f ->
                    FilterChip(
                        selected = f == filter,
                        onClick = { filter = f },
                        label = { Text(f.label, fontSize = 15.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primary,
                            selectedLabelColor = colors.onPrimary,
                        ),
                    )
                }
            }
            if (banner != null && query.isBlank()) TodayCard(banner) { onOpen(banner.recipe.slug) }
            when {
                error != null && recipes.isEmpty() -> Message(error, onRetry)
                recipes.isEmpty() -> Message("Chargement des recettes…", null)
                shown.isEmpty() -> Message("Aucune recette ne correspond", null)
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(shown, key = { it.slug }) { r -> RecipeCard(r) { onOpen(r.slug) } }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHighest).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = colors.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Chercher une recette", color = colors.onSurfaceVariant, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, "Effacer la recherche", tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TodayCard(banner: TodayBanner, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF6A3B00),
        contentColor = Color(0xFFFFDCBE),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RecipeImage(banner.recipe.imageUrl, Modifier.width(72.dp).height(48.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(banner.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text(banner.recipe.name, fontSize = 20.sp, color = Color(0xFFFFF3E6), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Voir la recette", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun RecipeCard(r: RecipeSummary, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            RecipeImage(r.imageUrl, Modifier.fillMaxWidth().height(120.dp))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Text(cardMeta(r), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A Mealie photo (public URL, cached by Coil), or a plain tile when the recipe has none. */
@Composable
fun RecipeImage(url: String?, modifier: Modifier) {
    if (url == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
    }
}

@Composable
private fun Message(text: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Réessayer") }
    }
    Spacer(Modifier.height(1.dp))
}
