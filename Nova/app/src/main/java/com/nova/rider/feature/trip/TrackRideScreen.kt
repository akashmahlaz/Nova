package com.nova.rider.feature.trip

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nova.designsystem.components.NovaTopBar
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.navigation.Home
import com.nova.rider.navigation.Navigator
import kotlinx.coroutines.delay

@Composable
fun TrackRideScreen(nav: Navigator, token: String) {
    var ride by remember(token) { mutableStateOf<NovaApi.TrackedRide?>(null) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(token, reloadKey) {
        while (true) {
            try {
                ride = NovaApi.trackRide(token)
                error = null
                val status = ride?.status
                if (status in setOf("completed", "cancelled", "no_drivers")) break
            } catch (e: Exception) {
                if (ride == null) error = e.message ?: "Could not load ride"
            }
            delay(10_000)
        }
    }

    Scaffold(
        topBar = {
            NovaTopBar(
                title = "Live ride status",
                onBack = { nav.back() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                ride != null -> {
                    val r = ride!!
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${r.bookerName} booked a ${r.productName} ride",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Status: ${r.status.replace('_', ' ')}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            r.scheduledAt?.let {
                                Text(
                                    text = "Scheduled: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Pickup", style = MaterialTheme.typography.labelMedium)
                            Text(r.pickup.name, style = MaterialTheme.typography.bodyLarge)
                            if (r.stops.isNotEmpty()) {
                                HorizontalDivider()
                                r.stops.forEachIndexed { idx, stop ->
                                    Text("Stop ${idx + 1}", style = MaterialTheme.typography.labelMedium)
                                    Text(stop.name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            HorizontalDivider()
                            Text("Drop-off", style = MaterialTheme.typography.labelMedium)
                            Text(r.dropoff.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    OutlinedButton(
                        onClick = { reloadKey++ },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Refresh")
                    }
                }
                error != null -> {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { reloadKey++ }) {
                        Text("Retry")
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
