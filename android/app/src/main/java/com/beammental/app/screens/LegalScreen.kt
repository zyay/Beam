package com.beammental.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.ui.effects.MeshBackground
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.theme.BeamColors

/* In-app legal viewer — the same three documents the web serves at
 * /pravne, rendered natively so users never have to leave the app to
 * know what happens with their data. One source of truth on the web
 * keeps them aligned; this port is checked against it on releases. */

private data class LegalDoc(val title: String, val updated: String, val body: List<String>)

private val LEGAL_DOCS = listOf(
    LegalDoc(
        title = "Ochrana súkromia",
        updated = "6. septembra 2026",
        body = listOf(
            "Aplikácia Beam (ďalej len „Beam“) spracúva osobné údaje v minimálnom rozsahu potrebnom na prevádzku služby. Tento dokument vysvetľuje, ktoré údaje, načo a ako dlho.",
            "**Ktoré údaje spracúvame.** Pri registrácii uložíme tvoj e-mail a hašované heslo (bcrypt). Počas onboardingu si môžeš zadať meno a preferencie (nálada, oblasti zaťaženia, ciele, frekvencia check-inu) — tieto sa ukladajú do tvojho profilu. Obsah tvojich správ v chate sa ukladá iba v tvojom zariadení; server si ich dlhodobo neukladá. Záznamy nálady v Prehľade sú uložené na serveri pod tvojím účtom a vidíš ich len ty.",
            "**Načo ich potrebujeme.** Údaje používame výhradne na poskytovanie funkcií Beam: prihlásenie, prispôsobenie rozhovorov a pripomienky. Nepredávame ich ani ich neposkytujeme tretím stranám na marketingové účely.",
            "**Spracovanie AI.** Tvoje správy sa posielajú cez náš server do jazykového modelu (Vercel AI Gateway, prevádzkovateľ Vercel Inc.) výhradne na účel vygenerovania odpovede. Nikdy ich nepoužívame na trénovanie modelov.",
            "**Hlasový režim.** Pri hlasovom rozhovore sa audio posiela na Gemini Live API (Google) v reálnom čase na premenu na text a odpoveď. Obsah hovoru si server dlhodobo neukladá; titulky sa zobrazujú len počas hovoru.",
            "**Skladovanie.** Účet a profil uchovávame, kým ho nevymažeš. Chat históriu drží len tvoje zariadenie — vymažeš ju tlačidlom Nový rozhovor alebo vymazaním úložiska.",
            "**Tvoje práva.** Môžeš požiadať o výpis, opravu alebo vymazanie svojich údajov na kontakt uvedenom nižšie.",
            "**Kontakt.** Otázky k súkromiu: sockagorny@gmail.com.",
        ),
    ),
    LegalDoc(
        title = "Všeobecné podmienky",
        updated = "5. septembra 2026",
        body = listOf(
            "**1. Predmet.** Beam je komunikačná aplikácia na podporu duševnej pohody a sebapoznania. Poskytuje konverzáciu s jazykovým modelom a krízové kontakty. Nie je to zdravotnícka pomôcka, diagnostický nástroj ani náhrada psychologickej, psychiatrickej alebo lekárskiej starostlivosti.",
            "**2. Účet.** Si zodpovedný/á za ochranu svojich prihlasovacích údajov. Musíš mať aspoň 16 rokov. Účet môžeš kedykoľvek zrušiť písomnou žiadosťou na kontakt nižšie.",
            "**3. Prijateľné používanie.** Nepoužívaj Beam na porušovanie práva, obťažovanie ani na rady v oblasti liekov a dávkovania. V prípade akútnej krízy vždy kontaktuj Linku krízy 0800 900 900, IPčko 0800 500 500 alebo 112.",
            "**4. Dostupnosť.** Služba závisí od tretích strán (hosting, AI poskytovateľ) a môže byť dočasne nedostupná. Neručíme za nepretržitú prevádzku.",
            "**5. Obmedzenie zodpovednosti.** Odpovede AI sú generované a nemusia byť správne. Nezodpovedáme za škody vzniknuté spojením na základe obsahu chatu. Ak potrebuješ pomoc, obráť sa na odborníka.",
            "**6. Zmeny podmienok.** Podmienky môžeme aktualizovať; významné zmeny oznámime v aplikácii. Ďalším používaním platí nová verzia.",
            "**Kontakt.** sockagorny@gmail.com",
        ),
    ),
    LegalDoc(
        title = "Zdravotný disclaimer",
        updated = "5. septembra 2026",
        body = listOf(
            "**Beam nie je zdravotnícka pomôcka.** Neposkytuje lekársku, psychologickú ani psychiatrickú starostlivosť, nediagnostikuje a nelieči žiadne ochorenia.",
            "Odpovede vytvára jazykový model a sú len informačné a podporné. Nikdy neodsadzuj ani nemenuj lieky na základe chatu.",
            "**V kríze alebo v akútnom ohrození okamžite kontaktuj:**\n- Linka krízy: **0800 900 900** (nonstop, zdarma)\n- IPčko: **0800 500 500** (chat aj telefonát pre mladých)\n- Tiesňové volanie: **112**",
            "Ak máš dlhodobé ťažkosti (depresia, úzkosti, straty, závislosti), obráť sa na praktického lekára, klinického psychológa alebo psychiatra. Je to znak sily, nie slabosti.",
        ),
    ),
)

@Composable
fun LegalScreen(onBack: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val doc = LEGAL_DOCS[selected]

    Box(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        MeshBackground(Modifier.matchParentSize(), intensity = 0.4f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack, "Späť",
                    tint = BeamColors.Fog,
                    modifier = Modifier.size(20.dp).clickable(onClick = onBack),
                )
                Spacer(Modifier.weight(1f))
                MascotBlob(modifier = Modifier.size(24.dp), blobSize = 24.dp)
            }

            Spacer(Modifier.height(24.dp))
            Text("Právne a súkromie", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist)
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LEGAL_DOCS.forEachIndexed { i, d ->
                    val on = i == selected
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) BeamColors.Sage else BeamColors.Card)
                            .border(
                                1.dp,
                                if (on) BeamColors.Sage else BeamColors.Line,
                                RoundedCornerShape(999.dp),
                            )
                            .clickable { selected = i }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            d.title,
                            fontSize = 12.sp,
                            maxLines = 1,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (on) BeamColors.SageInk else BeamColors.Fog,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(doc.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist)
                Spacer(Modifier.height(4.dp))
                Text("Aktualizované: ${doc.updated}", fontSize = 11.sp, color = BeamColors.Fog)
                Spacer(Modifier.height(16.dp))
                doc.body.forEach { p ->
                    Text(
                        richLegalText(p),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = BeamColors.Fog,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Renders **bold** spans the same way the web legal pages do. */
private fun richLegalText(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, seg ->
        if (seg.isEmpty()) return@forEachIndexed
        if (i % 2 == 1) {
            pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = BeamColors.Mist))
            append(seg)
            pop()
        } else {
            append(seg)
        }
    }
}
