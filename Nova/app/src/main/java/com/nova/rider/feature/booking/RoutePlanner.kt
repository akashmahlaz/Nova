package com.nova.rider.feature.booking

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nova.designsystem.components.IconTile
import com.nova.designsystem.components.NovaListItem
import com.nova.designsystem.components.SectionLabel
import com.nova.designsystem.components.SegmentedColumn
import com.nova.designsystem.icons.NovaIcons
import com.nova.rider.data.auth.GeoPlace
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.PlaceSuggestion
import com.nova.rider.data.auth.SavedPlace
import com.nova.rider.data.profile.ProfileStore
import com.nova.rider.data.trip.TripFormat
import com.nova.rider.data.trip.TripStore
import com.nova.rider.data.trip.metersBetween
import com.nova.rider.data.trip.toGeoPlace
import com.nova.rider.data.trip.tripErrorMessage
import com.nova.rider.feature.common.DotKind
import com.nova.rider.feature.common.RouteDot
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which route input is being typed into. */
sealed interface RouteField {
    data object Pickup : RouteField
    data class Stop(val index: Int) : RouteField
    data object Dropoff : RouteField
}

/**
 * State for the inline route planner on Home: which field is being edited, the typed query,
 * Places suggestions and the Google billing session.
 *
 * Search best practices applied:
 *   * Debounce 300 ms and require >= 2 characters; a new keystroke cancels the pending request.
 *   * One session token (UUID v4) per search: all keystrokes + the Place Details of the picked
 *     result are billed as one session; a fresh token after every pick.
 *   * Results biased around the pickup (or the device), so "Countdown" means the nearby one.
 *   * Zero-query state (saved places, "Use current location") costs no API calls.
 */
@Stable
class RoutePlannerState internal constructor(
    private val scope: CoroutineScope,
    private val context: Context,
    private val focusManager: FocusManager,
) {
    var editing by mutableStateOf<RouteField?>(null)
        private set
    var text by mutableStateOf(TextFieldValue(""))
    var results by mutableStateOf<List<PlaceSuggestion>>(emptyList())
        internal set
    var searching by mutableStateOf(false)
        internal set
    var picking by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        internal set
    internal var sessionToken by mutableStateOf(UUID.randomUUID().toString())
        private set

    val isEditing: Boolean get() = editing != null

    /** True when the field holds its current name fully selected (typing replaces it). */
    val showingZeroQuery: Boolean
        get() = text.text.trim().length < 2 || (text.text.isNotEmpty() && text.selection.length == text.text.length)

    fun fields(): List<RouteField> = buildList {
        add(RouteField.Pickup)
        TripStore.stops.indices.forEach { add(RouteField.Stop(it)) }
        add(RouteField.Dropoff)
    }

    fun displayName(field: RouteField): String = when (field) {
        RouteField.Pickup -> {
            val p = TripStore.pickup
            // The exact place under the rider (reverse geocoded), e.g. "71 Victoria Street West".
            p?.name.orEmpty()
        }
        is RouteField.Stop -> TripStore.stops.getOrNull(field.index)?.name.orEmpty()
        RouteField.Dropoff -> TripStore.dropoff?.name.orEmpty()
    }

    fun startEditing(field: RouteField) {
        if (editing == field) return
        editing = field
        val current = displayName(field)
        text = TextFieldValue(current, TextRange(0, current.length))
        error = null
    }

    fun stopEditing() {
        editing = null
        text = TextFieldValue("")
        results = emptyList()
        focusManager.clearFocus()
    }

    fun addStop() {
        TripStore.addStop()?.let { startEditing(RouteField.Stop(it)) }
    }

    fun removeStop(index: Int) {
        TripStore.removeStop(index)
        if (editing is RouteField.Stop) stopEditing()
    }

    private fun nextEmptyField(): RouteField? = when {
        TripStore.pickup == null -> RouteField.Pickup
        TripStore.stops.indexOfFirst { it == null } >= 0 -> RouteField.Stop(TripStore.stops.indexOfFirst { it == null })
        TripStore.dropoff == null -> RouteField.Dropoff
        else -> null
    }

    /** Puts [place] into the edited field (destination when none) and moves to the next empty one. */
    fun assign(place: GeoPlace, field: RouteField = editing ?: RouteField.Dropoff) {
        when (field) {
            RouteField.Pickup -> TripStore.choosePickup(place)
            is RouteField.Stop -> TripStore.chooseStop(field.index, place)
            RouteField.Dropoff -> TripStore.chooseDropoff(place)
        }
        val next = nextEmptyField()
        if (next == null) stopEditing() else { editing = null; startEditing(next) }
    }

    fun useCurrentLocation() {
        scope.launch { TripStore.useCurrentLocationAsPickup(context) }
        val next = nextEmptyField().takeIf { it != RouteField.Pickup }
        if (next == null) stopEditing() else { editing = null; startEditing(next) }
    }

    fun pick(s: PlaceSuggestion) {
        val field = editing ?: return
        if (picking) return
        picking = true
        scope.launch {
            runCatching { NovaApi.placeDetails(s.placeId, sessionToken) }
                .onSuccess { place ->
                    // Keep Google's short main text ("Sylvia Park Shopping Centre") as the name.
                    sessionToken = UUID.randomUUID().toString()
                    assign(place.copy(name = s.primaryText), field)
                }
                .onFailure { error = tripErrorMessage(it) }
            picking = false
        }
    }
}

@Composable
fun rememberRoutePlannerState(): RoutePlannerState {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state = remember { RoutePlannerState(scope, context, focusManager) }

    // Debounced autocomplete: restarting on each keystroke cancels the pending delay and drops
    // the older result (debounce + collectLatest semantics).
    LaunchedEffect(state.text.text, state.editing) {
        state.error = null
        if (state.editing == null || state.showingZeroQuery) {
            state.results = emptyList(); state.searching = false
            return@LaunchedEffect
        }
        delay(300)
        state.searching = true
        val near = TripStore.pickup?.point ?: TripStore.currentLocation
        runCatching { NovaApi.autocomplete(state.text.text.trim(), state.sessionToken, near) }
            .onSuccess { state.results = it }
            .onFailure { state.error = tripErrorMessage(it) }
        state.searching = false
    }
    return state
}

/**
 * Stacked pickup / stops / destination inputs: the Expressive "connected" group pattern
 * (outer corners 20 dp, inner 4 dp, 2 dp gaps). Following Material 3 text fields, each row has a
 * leading icon that says what it is (route dot) and at most one trailing action: clear while
 * typing, remove on a stop, and "add stop" on the destination.
 */
@Composable
fun RouteInputs(state: RoutePlannerState, modifier: Modifier = Modifier) {
    val fields = state.fields()
    val canAddStop = TripStore.stops.size < TripStore.MAX_STOPS
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        fields.forEachIndexed { i, field ->
            val shape = when {
                i == 0 -> RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)
                i == fields.lastIndex -> RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)
                else -> RoundedCornerShape(4.dp)
            }
            RouteFieldRow(
                field = field,
                shape = shape,
                active = state.editing == field,
                value = if (state.editing == field) state.text else TextFieldValue(state.displayName(field)),
                placeholder = when (field) {
                    RouteField.Pickup -> when {
                        TripStore.passenger != null && !TripStore.pickupIsCurrentLocation ->
                            "Where should we pick up ${TripStore.passenger!!.name.substringBefore(' ')}?"
                        TripStore.pickupIsCurrentLocation && TripStore.currentLocation != null -> "Finding your location…"
                        else -> "Pickup location"
                    }
                    is RouteField.Stop -> "Add stop ${field.index + 1}"
                    RouteField.Dropoff -> "Where to?"
                },
                onFocus = { state.startEditing(field) },
                onValueChange = { if (state.editing == field) state.text = it },
                onRemove = (field as? RouteField.Stop)?.let { stop -> { state.removeStop(stop.index) } },
                onAddStop = if (field == RouteField.Dropoff && canAddStop) state::addStop else null,
                imeAction = if (i == fields.lastIndex) ImeAction.Search else ImeAction.Next,
            )
        }
    }
}

@Composable
private fun RouteFieldRow(
    field: RouteField,
    shape: RoundedCornerShape,
    active: Boolean,
    value: TextFieldValue,
    placeholder: String,
    onFocus: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onRemove: (() -> Unit)?,
    onAddStop: (() -> Unit)?,
    imeAction: ImeAction,
) {
    val c = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val (container, content) = when {
        active -> c.primaryContainer to c.onPrimaryContainer
        field is RouteField.Stop -> c.tertiaryContainer to c.onTertiaryContainer
        else -> c.surfaceContainerHigh to c.onSurface
    }
    // Focus follows the planner (e.g. after a pick it jumps to the next empty field).
    LaunchedEffect(active) { if (active) focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(shape).background(container)
            .then(if (active) Modifier.border(2.dp, c.primary, shape) else Modifier)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RouteDot(
            when (field) {
                RouteField.Pickup -> DotKind.Pickup
                is RouteField.Stop -> DotKind.Stop
                RouteField.Dropoff -> DotKind.Destination
            },
        )
        Box(Modifier.weight(1f)) {
            if (value.text.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = content.copy(alpha = 0.7f), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = content),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (it.isFocused) onFocus() },
            )
        }
        // One trailing action per field (Material 3 text fields).
        when {
            active && value.text.isNotEmpty() -> IconButton(onClick = { onValueChange(TextFieldValue("")) }) {
                Icon(NovaIcons.Close, "Clear", tint = content, modifier = Modifier.size(18.dp))
            }
            onRemove != null -> IconButton(onClick = onRemove) { Icon(NovaIcons.Close, "Remove stop", tint = content, modifier = Modifier.size(18.dp)) }
            onAddStop != null -> FilledTonalIconButton(
                onClick = onAddStop,
                modifier = Modifier.padding(end = 4.dp).size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.secondaryContainer, contentColor = c.onSecondaryContainer),
            ) { Icon(NovaIcons.Plus, "Add a stop", modifier = Modifier.size(20.dp)) }
            else -> Spacer(Modifier.width(12.dp))
        }
    }
}

/** Suggestions for the field being edited: zero-query shortcuts, or Places results. */
@Composable
fun RouteSuggestions(state: RoutePlannerState, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }
        if (state.showingZeroQuery) {
            // Before typing: no Autocomplete calls. Current location (pickup), the closest
            // landmarks from Nearby Search (nearest first), then saved places.
            val saved: List<Pair<SavedPlace, GeoPlace>> = ProfileStore.places.mapNotNull { sp -> sp.toGeoPlace()?.let { sp to it } }
            val here = TripStore.currentLocation
            val offerCurrent = state.editing == RouteField.Pickup && TripStore.hasLocationPermission(context)
            if (offerCurrent) {
                SegmentedColumn {
                    NovaListItem(
                        0, 1, "Use current location",
                        supporting = TripStore.currentAccuracyMeters?.let { "Accurate to about ${it.roundToInt()} m" } ?: "Your device's location",
                        leading = { IconTile(NovaIcons.Pin, c.primaryContainer, c.onPrimaryContainer) },
                        onClick = state::useCurrentLocation,
                    )
                }
            }
            val nearby = TripStore.nearby
            if (nearby.isNotEmpty()) {
                SectionLabel("Nearby")
                SegmentedColumn {
                    nearby.forEachIndexed { i, place ->
                        NovaListItem(
                            i, nearby.size, place.name, supporting = place.address,
                            leading = { IconTile(NovaIcons.Pin, c.surfaceContainerHigh, c.onSurface) },
                            trailing = {
                                here?.let { Text(TripFormat.distance(metersBetween(it, place.point).toLong()), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant) }
                            },
                            onClick = { state.assign(place) },
                        )
                    }
                }
            }
            if (saved.isNotEmpty()) {
                SectionLabel("Saved places")
                SegmentedColumn {
                    saved.forEachIndexed { i, (sp, place) ->
                        NovaListItem(
                            i, saved.size, sp.label, supporting = sp.address,
                            leading = { IconTile(if (sp.kind == "work") NovaIcons.Work else if (sp.kind == "home") NovaIcons.Home else NovaIcons.Pin, c.surfaceContainerHigh, c.onSurface) },
                            onClick = { state.assign(place) },
                        )
                    }
                }
            }
            if (!offerCurrent && nearby.isEmpty() && saved.isEmpty()) {
                Text("Start typing an address, place or suburb.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Results")
                if (state.searching || state.picking) LoadingIndicator(Modifier.size(24.dp))
            }
            SegmentedColumn {
                state.results.forEachIndexed { i, p ->
                    val first = i == 0
                    NovaListItem(
                        i, state.results.size, p.primaryText, supporting = p.secondaryText,
                        leading = {
                            IconTile(
                                NovaIcons.Pin,
                                if (first) c.primaryContainer else c.surfaceContainerHigh,
                                if (first) c.onPrimaryContainer else c.onSurface,
                            )
                        },
                        trailing = {
                            p.distanceMeters?.let { Text(TripFormat.distance(it), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant) }
                        },
                        onClick = { state.pick(p) },
                    )
                }
            }
            if (!state.searching && state.results.isEmpty() && state.error == null) {
                Text("No matches yet. Try a street, suburb or place name.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
    }
}
