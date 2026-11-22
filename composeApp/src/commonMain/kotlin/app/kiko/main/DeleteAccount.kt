package app.kiko.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kiko.ui.components.KikoButton
import app.kiko.ui.components.KikoButtonStyle
import app.kiko.ui.components.KikoCheckRow
import app.kiko.ui.components.KikoSheet
import app.kiko.ui.theme.Kiko

/** Supprimer son compte : tout part, tout de suite, sans retour possible. */
@Composable
internal fun DeleteAccountSheet(busy: Boolean, error: String?, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var understood by remember { mutableStateOf(false) }
    KikoSheet("Supprimer mon compte") {
        Text(
            "Ton profil, tes discussions, tes photos et tes vocaux sont effacés tout de suite. " +
                "Tes partenaires ne verront plus vos échanges. On ne pourra rien récupérer.",
            style = Kiko.type.body,
            color = Kiko.colors.ink,
        )
        KikoCheckRow(understood, { understood = it }, "J'ai compris, c'est définitif.")
        if (error != null) Text(error, style = Kiko.type.caption, color = Kiko.colors.mid)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KikoButton("Annuler", onCancel, Modifier.weight(1f), style = KikoButtonStyle.White, enabled = !busy)
            KikoButton(
                if (busy) "Suppression…" else "Supprimer",
                onConfirm,
                Modifier.weight(1f),
                style = KikoButtonStyle.Ink,
                enabled = understood && !busy,
            )
        }
    }
}
