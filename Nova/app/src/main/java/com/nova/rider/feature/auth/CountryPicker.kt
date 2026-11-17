package com.nova.rider.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.NovaTextField
import com.nova.designsystem.icons.NovaIcons
import com.nova.rider.data.auth.Country
import com.nova.rider.data.auth.PhoneRegions

/** Popular shortcut countries for quick 1-tap switching. */
private val PopularIsos = listOf("IN", "NZ", "AU", "GB", "US", "CA")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selected: Country?,
    onPick: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val c = MaterialTheme.colorScheme

    val popularCountries = remember {
        PopularIsos.mapNotNull { PhoneRegions.find(it) }
    }

    val results = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            PhoneRegions.all
        } else {
            PhoneRegions.all.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.iso.equals(q, ignoreCase = true) ||
                    it.dial == q.removePrefix("+")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Select your country",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = c.onSurface,
                    )
                    Text(
                        "Choose the country code for your mobile number",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(NovaIcons.Close, contentDescription = "Close", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            // Search Bar
            NovaTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search country name or code (+91, NZ…)",
                leading = {
                    Icon(NovaIcons.Search, contentDescription = "Search", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                },
                trailing = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(NovaIcons.Close, contentDescription = "Clear", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )

            // Popular Countries Horizontal Row (shown when not searching)
            if (query.isBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "POPULAR",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = c.primary,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        popularCountries.forEach { country ->
                            val isChosen = country.iso == selected?.iso
                            Surface(
                                onClick = { onPick(country) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isChosen) c.primaryContainer else c.surfaceContainerLow,
                                border = if (isChosen) null else androidx.compose.foundation.BorderStroke(1.dp, c.outlineVariant),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(country.flag, fontSize = 18.sp)
                                    Text(
                                        "+${country.dial}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isChosen) c.onPrimaryContainer else c.onSurface,
                                    )
                                    Text(
                                        country.iso,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isChosen) c.onPrimaryContainer.copy(alpha = 0.8f) else c.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.5f))

            // Country List
            if (results.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No countries match \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.iso }) { country ->
                        val isChosen = country.iso == selected?.iso
                        CountryListItem(
                            country = country,
                            isSelected = isChosen,
                            onClick = { onPick(country) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryListItem(
    country: Country,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) c.secondaryContainer.copy(alpha = 0.45f) else c.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Flag Avatar Container
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(country.flag, fontSize = 22.sp)
            }

            // Country Name & ISO
            Column(Modifier.weight(1f)) {
                Text(
                    country.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    country.iso,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant,
                )
            }

            // Dial Code Badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) c.primary else c.surfaceContainerHighest,
            ) {
                Text(
                    "+${country.dial}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = if (isSelected) c.onPrimary else c.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Checkmark if selected
            if (isSelected) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(c.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        NovaIcons.Check,
                        contentDescription = "Selected",
                        tint = c.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
