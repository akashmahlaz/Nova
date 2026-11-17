package com.nova.rider.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nova.designsystem.components.NovaTextField
import com.nova.rider.data.auth.Country
import com.nova.rider.data.auth.PhoneRegions

/** Searchable list of every supported country with its dial code. Pick one to change the number prefix. */
@Composable
fun CountryPickerSheet(selected: Country?, onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val results = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            PhoneRegions.all
        } else {
            PhoneRegions.all.filter {
                it.name.contains(q, ignoreCase = true) || it.iso.equals(q, ignoreCase = true) || it.dial == q.removePrefix("+")
            }
        }
    }
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose your country", style = MaterialTheme.typography.titleLarge)
            NovaTextField(value = query, onValueChange = { query = it }, label = "Search country")
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(results, key = { it.iso }) { country ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(country) }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            country.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (country.iso == selected?.iso) FontWeight.Bold else FontWeight.Normal,
                            color = c.onSurface,
                        )
                        Text("+${country.dial}", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
