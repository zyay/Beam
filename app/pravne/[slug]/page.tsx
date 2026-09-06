import Link from "next/link";
import { notFound } from "next/navigation";
import BeamMark from "@/components/BeamMark";

const DOCS: Record<string, { title: string; updated: string; body: string[] }> = {
  "ochrana-sukromia": {
    title: "Ochrana súkromia",
    updated: "6. septembra 2026",
    body: [
      `Aplikácia Beam (ďalej len „Beam“) spracúva osobné údaje v minimálnom rozsahu potrebnom na prevádzku služby. Tento dokument vysvetľuje, ktoré údaje, načo a ako dlho.`,
      `**Ktoré údaje spracúvame.** Pri registrácii uložíme tvoj e-mail a hašované heslo (bcrypt). Počas onboardingu si môžeš zadať meno a preferencie (nálada, oblasti zaťaženia, ciele, frekvencia check-inu) — tieto sa ukladajú do tvojho profilu. Obsah tvojich správ v chate sa ukladá iba v tvojom zariadení (lokálne úložisko prehliadača); server si ich dlhodobo neukladá.`,
      `**Načo ich potrebujeme.** Údaje používame výhradne na poskytovanie funkcií Beam: prihlásenie, prispôsobenie rozhovorov a pripomienky. Nepredávame ich ani ich neposkytujeme tretím stranám na marketingové účely.`,
      `**Spracovanie AI.** Tvoje správy sa posiela cez náš server do jazykového modelu (Vercel AI Gateway, prevádzkovateľ Vercel Inc., model minimax/minimax-m3) výhradne na účel vygenerovania odpovede. Nikdy ich nepoužívame na trénovanie modelov.`,
      `**Súbory cookie.** Používame jedinú nevyhnutnú cookie (beam_session) — authentizačný token, ktorý udržiava tvoje prihlásenie. Nepoužívame sledovacie ani marketingové cookies.`,
      `**Skladovanie.** Účet a profil uchovávame, kým ho nevymažeš. Chat históriu drží len tvoje zariadenie — vymažeš ju tlačidlom Nový rozhovor alebo vymazaním úložiska prehliadača.`,
      `**Tvoje práva.** Môžeš požiadať o výpis, opravu alebo vymazanie svojich údajov na kontakt uvedenom nižšie. Nepridlžujeme citlivé špeciálne kategórie údajov povinne — o citlivých veciach sa rozprávaj v chate len v rozsahu, v akom si to praješ.`,
      `**Kontakt.** Otázky k súkromiu: sockagorny@gmail.com.`,
    ],
  },
  "vseobecne-podmienky": {
    title: "Všeobecné podmienky",
    updated: "5. septembra 2026",
    body: [
      `**1. Predmet.** Beam je komunikačná aplikácia na podporu duševnej pohody a sebapoznania. Poskytuje konverzáciu s jazykovým modelom a krízové kontakty. Nie je to zdravotnícka pomôcka, diagnostický nástroj ani náhrada psychologickej, psychiatrickej alebo lekárskiej starostlivosti.`,
      `**2. Účet.** Si zodpovedný/á za ochranu svojich prihlasovacích údajov. Musíš mať aspoň 16 rokov. Účet môžeš kedykoľvek zrušiť písomnou žiadosťou na kontakt nižšie.`,
      `**3. Prijateľné používanie.** Nepoužívaj Beam na porušovanie práva, obťažovanie ani na rady v oblasti liekov a dávkovania. V prípade akútnej krízy vždy kontaktuj Linku krízy 0800 900 900, IPčko 0800 500 500 alebo 112.`,
      `**4. Dostupnosť.** Služba závisí od tretích strán (hosting, AI poskytovateľ) a môže byť dočasne nedostupná. Neručíme za nepretržitú prevádzku.`,
      `**5. Obmedzenie zodpovednosti.** Odpovede AI sú generované a nemusia byť správne. Nezodpovedáme za škody vzniknuté spojením na základe obsahu chatu. Ak potrebuješ pomoc, obráť sa na odborníka.`,
      `**6. Zmeny podmienok.** Podmienky môžeme aktualizovať; významné zmeny oznámime v aplikácii. Ďalším používaním platí nová verzia.`,
      `**Kontakt.** sockagorny@gmail.com`,
    ],
  },
  "zdravotny-disclaimer": {
    title: "Zdravotný disclaimer",
    updated: "5. septembra 2026",
    body: [
      `**Beam nie je zdravotnícka pomôcka.** Neposkytuje lekársku, psychologickú ani psychiatrickú starostlivosť, nediagnostikuje a nelieči žiadne ochorenia.`,
      `Odpovede vytvára jazykový model a sú len informačné a podporné. Nikdy neodsadzuj ani nemenuj lieky na základe chatu.`,
      `**V kríze alebo v akútnom ohrození okamžite kontaktuj:**
- Linka krízy: **0800 900 900** (nonstop, zdarma)
- IPčko: **0800 500 500** (chat aj telefonát pre mladých)
- Tiesňové volanie: **112**`,
      `Ak máš dlhodobé ťažkosti (depresia, úzkosti, straty, závislosti), obráť sa na praktického lekára, klinického psychológa alebo psychiatra. Je to znak sily, nie slabosti.`,
    ],
  },
};

export function generateStaticParams() {
  return Object.keys(DOCS).map((slug) => ({ slug }));
}

export default async function LegalPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const doc = DOCS[slug];
  if (!doc) notFound();

  return (
    <main className="mx-auto min-h-dvh w-full max-w-[680px] px-5 py-10">
      <div className="mb-8 flex items-center justify-between">
        <Link
          href="/"
          className="flex items-center gap-2 text-sm text-fog transition-colors hover:text-mist"
        >
          <svg width={16} height={16} viewBox="0 0 256 256" fill="currentColor" aria-hidden>
            <path d="M224,128a8,8,0,0,1-8,8H59.31l58.35,58.34a8,8,0,0,1-11.32,11.32l-72-72a8,8,0,0,1,0-11.32l72-72a8,8,0,0,1,11.32,11.32L59.31,120H216A8,8,0,0,1,224,128Z" />
          </svg>
          Späť
        </Link>
        <BeamMark size={22} />
      </div>

      <h1 className="text-3xl font-semibold tracking-tight">{doc.title}</h1>
      <p className="mt-2 text-xs text-fog">Aktualizované: {doc.updated}</p>

      <div className="mt-8 flex flex-col gap-5 text-[15px] leading-relaxed text-[#c9c9d0]">
        {doc.body.map((p, i) => (
          <RichParagraph key={i} text={p} />
        ))}
      </div>

      <p className="mt-12 text-center text-xs text-fog">
        Beam · mental health —{" "}
        <Link href="/pravne/ochrana-sukromia" className="underline underline-offset-2 hover:text-mist">
          Súkromie
        </Link>
        {" · "}
        <Link href="/pravne/vseobecne-podmienky" className="underline underline-offset-2 hover:text-mist">
          Podmienky
        </Link>
        {" · "}
        <Link href="/pravne/zdravotny-disclaimer" className="underline underline-offset-2 hover:text-mist">
          Disclaimer
        </Link>
      </p>
    </main>
  );
}

function RichParagraph({ text }: { text: string }) {
  const bold = text.split(/(\*\*[^*]+\*\*)/g);
  return (
    <p>
      {bold.map((seg, i) =>
        seg.startsWith("**") && seg.endsWith("**") ? (
          <strong key={i} className="text-mist">
            {seg.slice(2, -2)}
          </strong>
        ) : (
          <span key={i}>{seg}</span>
        )
      )}
    </p>
  );
}
