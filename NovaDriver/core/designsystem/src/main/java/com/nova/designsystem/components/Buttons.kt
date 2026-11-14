package com.nova.designsystem.components

import com.nova.designsystem.theme.NovaTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Primary Nova action. Uses the expressive [Button] overload with [ButtonDefaults.shapesFor]
 * so the corners morph on press (M3 Expressive shape morphing).
 */
@Composable
fun NovaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(containerColor = NovaTheme.colors.cta, contentColor = NovaTheme.colors.onCta),
    textStyle: TextStyle = MaterialTheme.typography.labelLarge.copy(fontSize = if (height >= 64.dp) MaterialTheme.typography.titleMedium.fontSize.times(1.06f) else MaterialTheme.typography.labelLarge.fontSize),
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(height),
        modifier = modifier.fillMaxWidth().height(height),
        enabled = enabled,
        colors = colors,
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        ButtonContent(text, leadingIcon, trailingIcon, textStyle)
    }
}

@Composable
fun NovaTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    leadingIcon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(height),
        modifier = modifier.height(height),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        ButtonContent(text, leadingIcon, null, MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NovaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    leadingIcon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(height),
        modifier = modifier.height(height),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        ButtonContent(text, leadingIcon, null, MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
    }
}

@Composable
private fun ButtonContent(text: String, leading: ImageVector?, trailing: ImageVector?, style: TextStyle) {
    if (leading != null) {
        Icon(leading, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = style, maxLines = 1)
    if (trailing != null) {
        Spacer(Modifier.width(8.dp))
        Icon(trailing, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

/**
 * Connected button group (M3 Expressive): adjacent [ToggleButton]s with connected leading /
 * middle / trailing shapes. The selected item morphs to a full pill.
 */
@Composable
fun NovaConnectedGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    checkedContainer: Color = NovaTheme.colors.cta,
    checkedContent: Color = NovaTheme.colors.onCta,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface,
    leadingIconForChecked: ImageVector? = null,
    weights: List<Float>? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val checked = index == selectedIndex
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(index) },
                modifier = Modifier
                    .weight(weights?.getOrNull(index) ?: 1f)
                    .height(height)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = container,
                    contentColor = content,
                    checkedContainerColor = checkedContainer,
                    checkedContentColor = checkedContent,
                ),
            ) {
                if (checked && leadingIconForChecked != null) {
                    Icon(leadingIconForChecked, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (checked) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
            }
        }
    }
}
