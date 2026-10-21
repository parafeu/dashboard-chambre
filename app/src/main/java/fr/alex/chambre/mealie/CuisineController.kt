package fr.alex.chambre.mealie

import fr.alex.chambre.settings.Room
import fr.alex.chambre.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The recipe shown full screen in Cuisine mode. */
sealed interface OpenedRecipe {
    val slug: String
    data class Loading(override val slug: String) : OpenedRecipe
    data class Ready(val recipe: Recipe) : OpenedRecipe {
        override val slug: String get() = recipe.slug
    }
    data class Failed(override val slug: String, val message: String) : OpenedRecipe
}

/**
 * Cuisine mode's Mealie data: the recipe list and today's meal plan, read on start and every [refreshMs] while the
 * tablet is in Cuisine mode, and the recipe opened full screen. Other rooms never call Mealie.
 */
class CuisineController(
    private val scope: CoroutineScope,
    settings: Flow<Settings>,
    private val apiFor: (Settings) -> MealieApi?,
    private val refreshMs: Long = 30 * 60_000L,
    /** Whether this device reads Mealie (see deviceFeatures). */
    private val enabled: (Settings) -> Boolean = { it.room == Room.CUISINE },
    /** false while the phone app is in the background: no polling, the list is kept and read again on return. */
    private val active: Flow<Boolean> = flowOf(true),
) {
    private val _recipes = MutableStateFlow<List<RecipeSummary>>(emptyList())
    val recipes: StateFlow<List<RecipeSummary>> = _recipes.asStateFlow()

    private val _today = MutableStateFlow<List<MealPlanEntry>>(emptyList())
    /** Today's meal plan entries that point at a recipe (« Au menu ce soir »). */
    val today: StateFlow<List<MealPlanEntry>> = _today.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _opened = MutableStateFlow<OpenedRecipe?>(null)
    val opened: StateFlow<OpenedRecipe?> = _opened.asStateFlow()

    private var api: MealieApi? = null
    private val poke = Channel<Unit>(Channel.CONFLATED)
    private var openJob: Job? = null

    init {
        scope.launch {
            settings
                .map { if (enabled(it)) it else null }
                .distinctUntilChanged { a, b -> a?.mealieUrl == b?.mealieUrl && a?.mealieToken == b?.mealieToken && (a == null) == (b == null) }
                .collectLatest { s ->
                    api = null
                    _recipes.value = emptyList()
                    _today.value = emptyList()
                    _error.value = null
                    if (s == null) return@collectLatest
                    val a = apiFor(s)
                    if (a == null) {
                        _error.value = MISSING
                        return@collectLatest
                    }
                    api = a
                    while (true) {
                        active.first { it }
                        load(a)
                        // Until the next refresh, a poke, or the app leaving the screen.
                        val paused = withTimeoutOrNull(refreshMs) {
                            merge(poke.receiveAsFlow().map { false }, active.filter { !it }.map { true }).first()
                        }
                        if (paused == true) active.first { it }
                    }
                }
        }
    }

    private suspend fun load(a: MealieApi) {
        try {
            _recipes.value = a.recipes()
            _today.value = runCatching { a.todayMeals() }.getOrDefault(emptyList()) // no meal plan: no banner
            _error.value = null
        } catch (e: MealieException) {
            _error.value = e.message
        }
    }

    /** Reads the list again now. */
    fun refresh() {
        poke.trySend(Unit)
    }

    fun open(slug: String) {
        val a = api ?: return
        openJob?.cancel()
        _opened.value = OpenedRecipe.Loading(slug)
        openJob = scope.launch {
            _opened.value = try {
                OpenedRecipe.Ready(a.recipe(slug))
            } catch (e: MealieException) {
                OpenedRecipe.Failed(slug, e.message ?: "Erreur Mealie")
            }
        }
    }

    fun close() {
        openJob?.cancel()
        _opened.value = null
    }

    private companion object {
        const val MISSING = "Renseigne l'adresse et la clé API Mealie dans les Réglages"
    }
}
