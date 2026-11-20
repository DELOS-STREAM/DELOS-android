package com.limelight.shell.screens

import androidx.compose.animation.AnimatedVisibility
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.limelight.R
import com.limelight.shell.ui.textResources
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.limelight.shell.companion.FilterPage
import com.limelight.shell.companion.FilterUnit
import com.limelight.shell.companion.PublishCompanionPage
import com.limelight.shell.companion.filterCount
import com.limelight.shell.data.GameGenre
import com.limelight.shell.ui.genreNameRes
import com.limelight.shell.ui.themeNameRes
import com.limelight.shell.data.GameMetadata
import com.limelight.shell.data.HdrFilter
import com.limelight.shell.data.LengthFilter
import com.limelight.shell.data.Library
import com.limelight.shell.data.LocalGameMetadata
import com.limelight.shell.data.MetadataFilters
import com.limelight.shell.ui.Cue
import com.limelight.shell.ui.EaseOut
import com.limelight.shell.ui.dialogEnter
import com.limelight.shell.ui.dialogExit
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.FocusLook
import com.limelight.shell.ui.FocusRing
import com.limelight.shell.ui.FocusRow
import com.limelight.shell.ui.FocusSpacing
import com.limelight.shell.ui.InitialFocus
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.LocalStage
import com.limelight.shell.ui.rememberMarginBringIntoView
import com.limelight.shell.ui.LocalFeedback
import com.limelight.shell.ui.PillButton
import com.limelight.shell.ui.RowGlideHost
import com.limelight.shell.ui.ScrollRow
import com.limelight.shell.ui.Symbol
import com.limelight.shell.ui.Symbols
import com.limelight.shell.ui.genreIcon
import com.limelight.shell.ui.genreName
import com.limelight.shell.ui.themeName
import com.limelight.shell.ui.themeIcon
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.backdropBlur
import com.limelight.shell.ui.fadeEdges
import com.limelight.shell.ui.focusEffects
import com.limelight.shell.ui.glass
import com.limelight.shell.ui.px
import com.limelight.shell.ui.StageShape
import com.limelight.shell.ui.cpx
import com.limelight.shell.ui.tpx
import com.limelight.shell.ui.rowNavScope
import dev.chrisbanes.haze.HazeState

private fun countGames(games: List<GameMetadata?>, entries: (GameMetadata) -> List<GameGenre>): Map<Int, Int> {
    val counts = HashMap<Int, Int>()
    for (game in games) {
        if (game == null) continue
        for (id in entries(game).map { it.id }.toSet()) counts[id] = (counts[id] ?: 0) + 1
    }
    return counts
}

/** How many games carry each genre (by genre id); a game lists a genre at most once. */
internal fun genreCounts(games: List<GameMetadata?>): Map<Int, Int> =
    countGames(games) { it.candidate?.genres.orEmpty() }

/** How many games carry each theme (by theme id); a game lists a theme at most once. */
internal fun themeCounts(games: List<GameMetadata?>): Map<Int, Int> =
    countGames(games) { it.candidate?.themes.orEmpty() }

/** The live result line of the filter modal: "27 of 55 games", or "All 55 games" without a filter. */
internal fun filterResultLine(res: Resources, shown: Int, total: Int, active: Boolean): String =
    if (active) res.getQuantityString(R.plurals.filters_result_active, total, shown, total)
    else res.getQuantityString(R.plurals.filters_result_all, total, total)

/** The values that narrow the library as the sheet names them, in the order of its rows and chips: genres, themes, HDR support, length. */
internal fun libraryFilterLabels(res: Resources, filters: MetadataFilters, genres: List<GameGenre>, themes: List<GameGenre>): List<String> = buildList {
    // A chosen value the library no longer lists comes after the listed ones
    fun named(chosen: Set<Int>, listed: List<GameGenre>, name: (GameGenre) -> String, fallback: (Int) -> String) {
        listed.filter { it.id in chosen }.forEach { add(name(it)) }
        chosen.filter { id -> listed.none { it.id == id } }.sorted().forEach { add(fallback(it)) }
    }
    named(filters.genres, genres, { genreName(res, it) }) { genreNameRes(it)?.let(res::getString) ?: res.getString(R.string.filters_section_genre) }
    named(filters.themes, themes, { themeName(res, it) }) { themeNameRes(it)?.let(res::getString) ?: res.getString(R.string.filters_section_theme) }
    HdrFilter.entries.filter { it in filters.hdr }.forEach { add(res.getString(hdrFilterLabel(it))) }
    LengthFilter.entries.filter { it in filters.length }.forEach { add(res.getString(lengthFilterLabel(it))) }
}

/** The label of the button that applies the chosen filters: "Show 12 apps". */
internal fun filterShowLabel(res: Resources, matching: Int): String = res.getQuantityString(R.plurals.filters_show, matching, matching)

/**
 * The sheet's choices while it is open: the library keeps the applied filter ([applied]) until [confirm] hands the choices to [apply];
 * closing without it drops them, and the next [begin] starts from the applied filter again.
 */
@Stable
class FilterDraft(private val applied: () -> MetadataFilters, private val apply: (MetadataFilters) -> Unit) {
    var value by mutableStateOf(MetadataFilters())
        private set

    fun begin() { value = applied() }
    fun edit(change: (MetadataFilters) -> MetadataFilters) { value = change(value) }
    fun reset() { value = MetadataFilters() }
    fun confirm() { if (value != applied()) apply(value) }
}

/** The home hint bar's label for Y: "Filter", with the number of narrowing criteria while any is set. */
internal fun filterHintLabel(res: Resources, filters: MetadataFilters): String =
    if (filters.active) res.getQuantityString(R.plurals.filters_hint_active, filters.activeCount, filters.activeCount) else res.getString(R.string.filters_hint)

@StringRes
internal fun hdrFilterLabel(filter: HdrFilter): Int = when (filter) {
    HdrFilter.HDR -> R.string.filters_hdr_hdr
    HdrFilter.SDR -> R.string.filters_hdr_sdr
    HdrFilter.UNKNOWN -> R.string.metadata_hdr_unverified
}

@StringRes
internal fun lengthFilterLabel(filter: LengthFilter): Int = when (filter) {
    LengthFilter.UNDER_10 -> R.string.filters_length_under_10
    LengthFilter.FROM_10_TO_30 -> R.string.filters_length_10_30
    LengthFilter.FROM_30_TO_60 -> R.string.filters_length_30_60
    LengthFilter.OVER_60 -> R.string.filters_length_over_60
    LengthFilter.UNKNOWN -> R.string.filters_length_unknown
}

/** The icons of the home badges (sparkle, dim sun and sealed question mark), so a filter and its badge look alike. */
private fun hdrFilterIcon(filter: HdrFilter): String = when (filter) {
    HdrFilter.HDR -> Symbols.HDR_ON
    HdrFilter.SDR -> Symbols.SDR
    HdrFilter.UNKNOWN -> Symbols.HDR_UNKNOWN
}

/**
 * The library filter modal (Y on the home screen): a centered glass panel with genre, theme, HDR support and duration chips. A chip
 * toggles its value in [draft]; the library stays as it is until [onConfirm] (the "Show N apps" pill, Y, Return on a chip) applies
 * the choices, and closing (B) drops them. Values in one row combine with OR, rows with AND. The focus stays inside while it is open
 * and starts on the first selected genre. [ready] is the library to count in (null while it is not available, which also hides the panel).
 */
@Composable
fun FilterModalLayer(visible: Boolean, ready: Library.Ready?, draft: FilterDraft, haze: HazeState, reducedMotion: Boolean, onConfirm: () -> Unit) {
    // Keep the last library while animating out
    var shown by remember { mutableStateOf(ready) }
    if (ready != null) shown = ready
    val open by rememberUpdatedState(visible)
    val enter: EnterTransition = dialogEnter(reducedMotion)
    val exit: ExitTransition = dialogExit(reducedMotion)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = enter, exit = exit) {
            val library = shown ?: return@AnimatedVisibility
            FilterPanel(library, open, draft, haze, onConfirm)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterPanel(ready: Library.Ready, open: Boolean, draft: FilterDraft, haze: HazeState, onConfirm: () -> Unit) {
    val colors = LocalDelosColors.current
    val stage = LocalStage.current
    val metadata = LocalGameMetadata.current
    val k = stage.dpPerPx * LocalDensity.current.density
    val shape = RoundedCornerShape(34.px)
    val chipScroll = rememberScrollState()
    val bringIntoView = rememberMarginBringIntoView(chipScroll, before = 150f * k, after = 56f * k, stagePx = k)
    val filters = draft.value
    val apps = ready.apps

    // Reading the entries here makes the counts follow the metadata as it arrives
    val games = apps.map { metadata.entry(ready.pc, it).data }
    // Names follow the language, so the chips are ordered by the translated name (the store sorts by IGDB's English one)
    val res = textResources()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current
    val genres = remember(games, locale) { val by = java.text.Collator.getInstance(res.configuration.locales[0]); metadata.genres(ready.pc, apps).sortedWith(compareBy(by) { genreName(res, it) }) }
    val themes = remember(games, locale) { val by = java.text.Collator.getInstance(res.configuration.locales[0]); metadata.themes(ready.pc, apps).sortedWith(compareBy(by) { themeName(res, it) }) }
    val counts = remember(games) { genreCounts(games) }
    val themeCount = remember(games) { themeCounts(games) }
    // Cheap enough to follow every toggle: the title and the pill show what the choices would leave
    val matching = games.indices.count { filters.matches(apps[it], games[it]) }

    // The second display summarises the sheet while it is open: the same count as the title's, the chosen values, and the sheet's own reset
    val chips = remember(filters, genres, themes, locale) { libraryFilterLabels(res, filters, genres, themes) }
    val reset = remember(draft) { { draft.reset() } }
    val page = remember(open, chips, matching, apps.size, filters.active, reset) {
        if (open) FilterPage(true, chips, filterCount(matching, apps.size, filters.active), FilterUnit.GAMES, reset) else null
    }
    PublishCompanionPage(page)

    // One requester per genre chip: the first focus goes to the first selected one, else to "All"
    val genreRequesters = remember { HashMap<Int, FocusRequester>() }
    fun genreRequester(id: Int?) = genreRequesters.getOrPut(id ?: 0) { FocusRequester() }
    val confirmRequester = remember { FocusRequester() }
    val startGenre = genres.firstOrNull { it.id in filters.genres }?.id
    // The focus goes in every time the modal opens, also when it is reopened while it still animates out:
    // the panel then stays composed, so a one-time request would leave the focus on the dashboard behind it.
    if (open) InitialFocus(genreRequester(startGenre))

    // Reset disables itself once nothing is chosen; if it had the focus, the focus goes to the pill that applies the choices
    var panelFocused by remember { mutableStateOf(false) }
    var wasActive by remember { mutableStateOf(filters.active) }
    LaunchedEffect(filters.active) {
        if (wasActive && !filters.active) {
            withFrameNanos { }
            if (!panelFocused) runCatching { confirmRequester.requestFocus() }
        }
        wasActive = filters.active
    }

    Column(
        Modifier
            // 1240 on a stage that has it; the compact one (small panels) keeps the 48 px margin on each side
            .width((if (stage.shape == StageShape.COMPACT) stage.stageWidth - 2f * SettingsLayout.margin(stage) else minOf(1240f, stage.stageWidth - 60f)).px)
            // 820 at 1080: a taller stage gives the chips more room, a shorter one less (the compact one keeps clear of the bars)
            .heightIn(max = (stage.stageHeight - (if (stage.shape == StageShape.COMPACT) 220f else 260f)).coerceAtLeast(0f).px)
            // box-shadow: 0 40px 100px -30px rgba(0, 0, 0, .7), like the dialogs
            .dropShadow(shape) {
                radius = 100f * k
                spread = -30f * k
                offset = Offset(0f, 40f * k)
                color = Color.Black
                alpha = .7f
            }
            .clip(shape)
            .backdropBlur(haze, 24f, colors.surface3)
            .border(1.px, colors.line, shape)
            // Under the Reset / Show pills the room is not scaled down on the compact stage: the focused pill's glow (44 px under it) ends inside the panel
            .padding(horizontal = 40.cpx).padding(top = 44.cpx, bottom = 44.px)
            .onFocusChanged { panelFocused = it.hasFocus }
            .focusProperties { onExit = { if (open) cancelFocusChange() } }
            .focusGroup()
            .rowNavScope(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.px, end = 12.px, bottom = 26.cpx), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.filters_title), style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 40.tpx))
            Spacer(Modifier.weight(1f))
            val result = filterResultLine(textResources(), matching, apps.size, filters.active)
            BasicText(
                result,
                Modifier
                    .clip(CircleShape)
                    .background(if (filters.active) colors.accent.copy(alpha = .16f) else colors.surface)
                    .border(1.px, if (filters.active) colors.accent.copy(alpha = .45f) else colors.line, CircleShape)
                    .padding(horizontal = 26.px, vertical = 10.px),
                style = TextStyle(color = if (filters.active) colors.accent3 else colors.text2, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 24.tpx),
            )
        }
        // The chips scroll when the genres need more room than the panel has; the focused one stays in view
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        // Share the cursor across the chip rows without changing spatial D-pad navigation.
        RowGlideHost(
            ring = FocusRing(ChipFocus.LOOK.band, ChipFocus.LOOK.outset), corner = 999f, focusScale = ChipFocus.LOOK.scale,
            fill = null, glow = null,
            // The edges fade only while chips continue behind them, so the ring of a chip in the first or the last row is not dimmed
            modifier = Modifier.weight(1f, fill = false).fadeEdges(stage, chipScroll, 20f, 40f),
        ) {
        Column(
            Modifier
                .verticalScroll(chipScroll)
                .padding(start = 12.px, end = 12.px, top = 24.cpx, bottom = 48.cpx),
            verticalArrangement = Arrangement.spacedBy(34.cpx),
        ) {
            FilterSection(Symbols.GENRE_UNKNOWN, stringResource(R.string.filters_section_genre)) {
                ChipRow {
                    FilterChip(
                        stringResource(R.string.filters_all), filters.genres.isEmpty(), { draft.edit { it.copy(genres = emptySet()) } },
                        Modifier.focusRequester(genreRequester(null)), count = apps.size, onConfirm = onConfirm,
                    )
                    genres.forEach { genre ->
                        key(genre.id) {
                            FilterChip(
                                genreName(res, genre), genre.id in filters.genres, { draft.edit { it.toggleGenre(genre.id) } },
                                Modifier.focusRequester(genreRequester(genre.id)), icon = genreIcon(genre.id), count = counts[genre.id] ?: 0, onConfirm = onConfirm,
                            )
                        }
                    }
                }
            }
            FilterDivider()
            // Without any theme in the library there is nothing to choose (a theme that was chosen stays visible)
            if (themes.isNotEmpty() || filters.themes.isNotEmpty()) {
                FilterSection(Symbols.THEMES, stringResource(R.string.filters_section_theme)) {
                    ChipRow {
                        FilterChip(
                            stringResource(R.string.filters_all), filters.themes.isEmpty(), { draft.edit { it.copy(themes = emptySet()) } },
                            count = apps.size, onConfirm = onConfirm,
                        )
                        themes.forEach { theme ->
                            key(theme.id) {
                                FilterChip(
                                    themeName(res, theme), theme.id in filters.themes, { draft.edit { it.toggleTheme(theme.id) } },
                                    icon = themeIcon(theme.id), count = themeCount[theme.id] ?: 0, onConfirm = onConfirm,
                                )
                            }
                        }
                    }
                }
                FilterDivider()
            }
            FilterSection(Symbols.HDR_ON, stringResource(R.string.filters_section_hdr)) {
                ChipRow {
                    FilterChip(stringResource(R.string.filters_hdr_any), filters.hdr.isEmpty(), { draft.edit { it.copy(hdr = emptySet()) } }, onConfirm = onConfirm)
                    HdrFilter.entries.forEach { option ->
                        FilterChip(
                            stringResource(hdrFilterLabel(option)), option in filters.hdr, { draft.edit { it.toggleHdr(option) } },
                            icon = hdrFilterIcon(option), onConfirm = onConfirm,
                        )
                    }
                }
            }
            FilterDivider()
            FilterSection(Symbols.COMPLETION_TIME, stringResource(R.string.filters_section_duration)) {
                // The last row: one press down reaches the pill that applies the choices, wherever the cursor is in the row
                val down = Modifier.focusProperties { this.down = confirmRequester }
                ChipRow {
                    FilterChip(stringResource(R.string.filters_length_any), filters.length.isEmpty(), { draft.edit { it.copy(length = emptySet()) } }, down, onConfirm = onConfirm)
                    LengthFilter.entries.forEach { option ->
                        FilterChip(
                            stringResource(lengthFilterLabel(option)), option in filters.length, { draft.edit { it.toggleLength(option) } }, down, onConfirm = onConfirm,
                        )
                    }
                }
            }
        }
        }
        }
        // The pills keep their focus spacing apart, more for a long label
        Box(Modifier.fillMaxWidth().padding(top = 30.cpx, start = 12.px, end = 12.px), contentAlignment = Alignment.CenterEnd) { FocusRow(FocusSpacing.PILL, 24f) {
            val active = filters.active
            PillButton(
                stringResource(R.string.filters_reset), Symbols.RESET, { draft.reset() },
                Modifier.alpha(if (active) 1f else .4f).then(if (active) Modifier else Modifier.focusProperties { canFocus = false }),
                compact = stage.shape == StageShape.COMPACT,
            )
            // The confirm cue comes from [onConfirm], which also closes the sheet
            PillButton(
                filterShowLabel(res, matching), Symbols.CHECK, onConfirm, Modifier.focusRequester(confirmRequester),
                primary = true, clickCue = null, compact = stage.shape == StageShape.COMPACT,
            )
        } }
    }
}

@Composable
private fun FilterSection(icon: String, title: String, content: @Composable () -> Unit) {
    val colors = LocalDelosColors.current
    Column(verticalArrangement = Arrangement.spacedBy(22.cpx)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) {
            Box(Modifier.size(60.cpx).clip(CircleShape).background(colors.accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Symbol(icon, 32.tpx, colors.accent2, filled = true)
            }
            BasicText(title, style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 29.tpx))
        }
        content()
    }
}

/** The thin line between two filter sections. */
@Composable
private fun FilterDivider() {
    Box(Modifier.fillMaxWidth().height(1.px).background(LocalDelosColors.current.line))
}

/** The filter chips' focus ([FilterChip], the Artwork strip's chips): 1.05 and a 3 px band 3 px outside, the gliding plate of their [RowGlideHost] drawn around the scaled chip. */
internal object ChipFocus {
    val LOOK = FocusLook(scale = 1.05f, outset = 3f, band = 3f, ringScales = false)

    /** A [FilterChip]'s least height. */
    const val HEIGHT = 60f
}

/**
 * A row of [FilterChip]s that scrolls when they need more room than it has (keeping their ring's room above and below while it does). The
 * gap between the chips grows with the widest of them to their focus spacing, so a focused chip never reaches its neighbour.
 */
@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
    ScrollRow(gap = 0f, ringRoom = 12f) { FocusRow(ChipFocus.LOOK, 14f, content = content) }
}

/**
 * A selectable chip: filled with the accent while chosen, glass otherwise; the focus ring marks the cursor. With [onConfirm] (a sheet
 * whose choices apply on confirm) the keyboard's Space toggles it and Return confirms; A and the D-pad center still toggle it.
 */
@Composable
internal fun FilterChip(
    label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, count: Int? = null,
    onConfirm: (() -> Unit)? = null,
) {
    val colors = LocalDelosColors.current
    val feedback = LocalFeedback.current
    var focused by remember { mutableStateOf(false) }
    val content = if (selected) colors.onAccent else colors.text
    Row(
        modifier
            .then(if (onConfirm == null) Modifier else Modifier.onPreviewKeyEvent { event ->
                val action = when (event.key) {
                    Key.Spacebar -> { { feedback.play(Cue.SELECT); onClick() } }
                    Key.Enter, Key.NumPadEnter -> onConfirm
                    else -> return@onPreviewKeyEvent false
                }
                // Both edges are handled, so Android does not synthesize a fallback key
                if (event.type == KeyEventType.KeyUp) action()
                true
            })
            .focusEffects(
                onClick = onClick, corner = 999f, focusScale = ChipFocus.LOOK.scale, ring = FocusRing(ChipFocus.LOOK.band, ChipFocus.LOOK.outset), glow = null,
                onFocusChange = { focused = it }, glide = true,
            )
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.accent) else Modifier.glass(colors.surface).border(1.px, colors.line, CircleShape))
            .heightIn(min = ChipFocus.HEIGHT.px)
            .padding(horizontal = 30.cpx, vertical = 12.cpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.px),
    ) {
        if (icon != null) Symbol(icon, 28.tpx, content, filled = selected || focused)
        BasicText(label, style = TextStyle(color = content, fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 24.tpx))
        if (count != null) BasicText("$count", style = TextStyle(color = content.copy(alpha = .6f), fontFamily = Figtree, fontSize = 22.tpx))
    }
}
