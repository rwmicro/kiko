package app.kiko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.kiko.ui.theme.Kiko

/**
 * Champ de formulaire (`.fld`) : étiquette au-dessus, boîte blanche bordée,
 * aide ou erreur en dessous. La bordure passe à la couleur primaire au focus.
 */
@Composable
fun KikoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    helper: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val c = Kiko.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        error != null -> c.error
        focused -> c.primary
        else -> c.hair
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (label != null) Text(label, style = Kiko.type.captionStrong, color = c.mid)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = Kiko.type.bodyStrong.copy(color = c.ink),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .background(c.surface, RoundedCornerShape(14.dp))
                        .border(if (focused || error != null) 2.5.dp else 2.dp, borderColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = Kiko.type.body, color = c.soft)
                    inner()
                }
            },
        )
        val note = error ?: helper
        if (note != null) Text(note, style = Kiko.type.micro, color = if (error != null) c.error else c.mid)
    }
}

/** Barre de recherche (`.sfield`), avec la loupe à gauche. */
@Composable
fun KikoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Rechercher",
) {
    val c = Kiko.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Kiko.type.bodyStrong.copy(color = c.ink),
        cursorBrush = SolidColor(c.primary),
        interactionSource = interaction,
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .height(48.dp)
                    .background(c.surface, RoundedCornerShape(17.dp))
                    .border(2.75.dp, if (focused) c.primary else c.hair, RoundedCornerShape(17.dp))
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(KikoIcons.Search, contentDescription = null, tint = c.soft, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = Kiko.type.body, color = c.soft)
                    inner()
                }
            }
        },
    )
}

/** Case à cocher avec son texte, par exemple l'acceptation des conditions. */
@Composable
fun KikoCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, text: String, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Row(
        modifier
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(if (checked) c.primary else c.surface, RoundedCornerShape(8.dp))
                .border(2.dp, if (checked) c.primary else c.hair, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(KikoIcons.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
        }
        Text(text, style = Kiko.type.caption, color = c.ink, modifier = Modifier.weight(1f))
    }
}

/** Interrupteur (`.tog2 i`) : vert quand il est actif. */
@Composable
fun KikoSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Kiko.colors
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .background(if (checked) c.ok else c.hair, CircleShape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = if (checked) 18.dp else 0.dp)
                .size(20.dp)
                .background(androidx.compose.ui.graphics.Color.White, CircleShape),
        )
    }
}

/** Ligne de réglage avec interrupteur (`.tog2`) : titre, précision, interrupteur à droite. */
@Composable
fun KikoToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val c = Kiko.colors
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Kiko.type.captionStrong, color = c.ink)
            if (detail != null) Text(detail, style = Kiko.type.micro, color = c.mid)
        }
        KikoSwitch(checked)
    }
}
