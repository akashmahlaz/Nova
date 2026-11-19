package com.nova.rider.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.icons.NovaIcons
import com.nova.rider.data.auth.Country
import com.nova.rider.data.auth.PhoneRegions

/** Priority shortcut countries to feature at the top of the list. */
private val PriorityIsos = listOf("IN", "NZ", "AU", "GB", "US", "CA")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selected: Country?,
    onPick: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val c = MaterialTheme.colorScheme

    val results = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            // Priority countries first, followed by remainder in alphabetical order
            val priority = PriorityIsos.mapNotNull { PhoneRegions.find(it) }
            val remainder = PhoneRegions.all.filter { it.iso !in PriorityIsos }
            priority + remainder
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Quiet, Minimalist Header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Select country",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.onSurface,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(NovaIcons.Close, contentDescription = "Close", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            // Clean, Quiet Search Bar (48dp height, no heavy floating labels)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = c.surfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    Icon(
                        NovaIcons.Search,
                        contentDescription = null,
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = c.onSurface,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    "Search country name or dial code…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                NovaIcons.Close,
                                contentDescription = "Clear",
                                tint = c.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.35f))

            // Country List
            if (results.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
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
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) c.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Natural flag emoji without bulky boxes
        Text(country.flag, fontSize = 22.sp)

        // Clean country name
        Text(
            country.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Monospace-styled dial code
        Text(
            "+${country.dial}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = if (isSelected) c.primary else c.onSurfaceVariant,
        )

        // Subtle checkmark when selected
        if (isSelected) {
            Icon(
                NovaIcons.Check,
                contentDescription = "Selected",
                tint = c.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
