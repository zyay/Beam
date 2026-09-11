package com.beammental.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.ui.theme.BeamColors

/**
 * Crisis help — the one thing in Beam that must never drift.
 *
 * The lines were duplicated three times (chat card, settings list, the fixed
 * crisis reply), each with its own hardcoded `Color.White` and its own phone
 * numbers. They live here once. [CRISIS_TEXT] is derived from [CrisisLines]
 * rather than written out beside it, so the model's canned answer and the UI
 * cannot disagree about a number.
 */

data class CrisisLine(
    val number: String,
    val name: String,
    val note: String = "",
) {
    /** "Linka krízy · nonstop" — what a row shows next to the number. */
    val label: String get() = if (note.isBlank()) name else "$name · $note"
}

val CrisisLines: List<CrisisLine> = listOf(
    CrisisLine("0800 900 900", "Linka krízy", "nonstop"),
    CrisisLine("0800 500 500", "IPčko", "nonstop"),
    CrisisLine("112", "Tiesňové volanie"),
)

/** The fixed protocol Beam replies with when the crisis guard trips. No
 *  round-trip, no model output — this text is the safety net, so it is a
 *  constant, not a prompt. */
val CRISIS_TEXT: String = buildString {
    append("To, čo cítiš, je veľmi vážne a nie si v tom sám/sama. Zavolaj teraz:\n\n")
    for (line in CrisisLines) {
        append(line.name).append(" · ").append(line.number)
        if (line.note.isNotBlank()) append(" (").append(line.note).append(")")
        append('\n')
    }
    append("\nSom len chatbot — v takejto chvíli ti musí pomôcť človek. ")
    append("Zavolaj, nie je to zlé rozhodnutie.")
}

fun dialNumber(context: Context, number: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
}

/**
 * The in-conversation crisis card: shown in place of a model reply when the
 * guard trips, and in the voice screen's crisis overlay.
 */
@Composable
fun CrisisCard(modifier: Modifier = Modifier) {
    BeamSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        fill = BeamColors.Card,
        hairline = BeamColors.Danger.copy(alpha = 0.30f),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = BeamColors.Danger,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "Krízová pomoc",
                color = BeamColors.Mist,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = (-0.2).sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Nie si v tom sám/sama. Zavolaj — na druhej strane je človek, nonstop.",
            color = BeamColors.Text4,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(12.dp))
        CrisisLines.forEach { line ->
            CrisisRow(
                line = line,
                fill = BeamColors.Danger.copy(alpha = 0.10f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Som len chatbot — v kríze ti musí pomôcť človek.",
            color = BeamColors.Fog,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

/**
 * The same lines as a plain settings list — no urgency styling, because here
 * they are a reference, not an interruption.
 */
@Composable
fun CrisisList(modifier: Modifier = Modifier) {
    BeamSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        CrisisLines.forEachIndexed { index, line ->
            CrisisRow(line = line, fill = Color.Transparent)
            if (index != CrisisLines.lastIndex) {
                BeamDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = BeamColors.Line.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun CrisisRow(
    line: CrisisLine,
    fill: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .background(fill, shape)
            .clickable(
                onClickLabel = "Zavolať ${line.name}",
                role = Role.Button,
            ) { dialNumber(context, line.number) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Phone,
            contentDescription = null,
            tint = BeamColors.Text3,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            line.label,
            color = BeamColors.Text3,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            line.number,
            color = BeamColors.Mist,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}
