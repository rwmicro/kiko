// Les textes des notifications. Le ton des maquettes : complice, un peu taquin, jamais culpabilisant.
// Une personne d'abord : « Ayu t'a écrit » passe avant « Kiko te rappelle ».
// On ne connaît pas le genre des partenaires : les phrases restent neutres.
// On n'invente jamais ce que l'autre a dit ou fait.

/** Une ligne de reminder_candidates (voir la migration des notifications). */
export interface Candidate {
  user_id: string;
  kind: "streak_risk" | "anniversary" | "reengage";
  dedupe_key: string;
  conversation_id: string | null;
  first_name: string | null;
  partner_name: string | null;
  partner_night: boolean;
  partner_waiting: boolean;
  streak: number | null;
  minutes_left: number | null;
  days_inactive: number | null;
  months: number | null;
  online_partners: number;
}

export interface Template {
  id: string;
  kind: Candidate["kind"];
  /** Jour d'absence visé (relances). */
  day?: number;
  /** La variante pousse à écrire à l'autre : jamais quand il fait nuit chez lui. */
  nudgesPartner?: boolean;
  when?: (c: Candidate) => boolean;
  title: (c: Candidate) => string;
  body: (c: Candidate) => string;
}

const p = (c: Candidate) => c.partner_name ?? "ton partenaire";
const hasPartner = (c: Candidate) => c.partner_name != null;
const plural = (n: number, one: string, many: string) => `${n} ${n > 1 ? many : one}`;

export function duration(months: number): string {
  if (months < 12) return months === 1 ? "1 mois" : `${months} mois`;
  const years = Math.floor(months / 12);
  return years === 1 ? "1 an" : `${years} ans`;
}

export const templates: Template[] = [
  // Série en danger (maquettes 21 et 22) : l'autre a écrit aujourd'hui, il ne manque que toi.
  {
    id: "streak_countdown",
    kind: "streak_risk",
    nudgesPartner: true,
    when: (c) => (c.minutes_left ?? 999) <= 180,
    title: (c) => `${c.minutes_left} minutes pour sauver la série`,
    body: (c) => `${p(c)} a écrit aujourd'hui. Il ne manque que toi.`,
  },
  {
    id: "streak_flame",
    kind: "streak_risk",
    nudgesPartner: true,
    title: (c) => `🔥 ${plural(c.streak ?? 0, "jour", "jours")} avec ${p(c)}`,
    body: (c) => `${p(c)} a fait sa part aujourd'hui. Un petit message et la flamme tient.`,
  },
  {
    id: "streak_watching",
    kind: "streak_risk",
    title: () => "Kiko ne dit rien…",
    body: (c) => `Kiko regarde juste votre série de ${plural(c.streak ?? 0, "jour", "jours")} avec ${p(c)}. 👀`,
  },

  // Anniversaire d'amitié (maquette 21).
  {
    id: "anniversary_cake",
    kind: "anniversary",
    title: (c) => `${p(c)}, ${duration(c.months ?? 1)} d'amitié`,
    body: (c) => `Il y a ${duration(c.months ?? 1)}, vous échangiez votre premier « halo ». 🎂`,
  },
  {
    id: "anniversary_progress",
    kind: "anniversary",
    nudgesPartner: true,
    title: (c) => `Déjà ${duration(c.months ?? 1)} avec ${p(c)} !`,
    body: () => "Et si vous vous racontiez ce que chacun a appris depuis le début ?",
  },

  // Relances sur 14 jours (maquette 24), à 19 h, une par jour au plus.
  {
    id: "d1_warm_seat",
    kind: "reengage",
    day: 1,
    title: () => "Kiko t'a gardé une place au chaud",
    body: (c) =>
      c.online_partners > 0 && hasPartner(c)
        ? `${p(c)} est en ligne. Un petit « halo » ?`
        : hasPartner(c)
        ? `Un petit message à ${p(c)} ?`
        : "Tes futurs partenaires t'attendent.",
  },
  {
    id: "d1_waiting",
    kind: "reengage",
    day: 1,
    nudgesPartner: true,
    when: (c) => c.partner_waiting && hasPartner(c),
    title: (c) => `${p(c)} t'a écrit`,
    body: () => "Son message attend ta réponse. Deux minutes suffisent.",
  },
  {
    id: "d1_question",
    kind: "reengage",
    day: 1,
    nudgesPartner: true,
    when: hasPartner,
    title: () => "Question du jour",
    body: (c) => `Quel plat te rappelle ton enfance ? Pose la question à ${p(c)}, et réponds-y dans sa langue.`,
  },
  {
    id: "d2_waiting",
    kind: "reengage",
    day: 2,
    nudgesPartner: true,
    when: (c) => c.partner_waiting && hasPartner(c),
    title: (c) => `${p(c)} attend ta réponse`,
    body: () => "Son dernier message est toujours là. Une phrase suffit pour reprendre.",
  },
  {
    id: "d2_no_pressure",
    kind: "reengage",
    day: 2,
    title: () => "Ça fait deux jours",
    body: (c) => (hasPartner(c) ? `Pas de pression. ${p(c)} et Kiko sont là quand tu veux.` : "Pas de pression. Kiko est là quand tu veux."),
  },
  {
    id: "d3_melted",
    kind: "reengage",
    day: 3,
    title: () => "Votre série a fondu 🫠",
    body: () => "Pas grave. Une conversation et c'est reparti, Kiko a gardé le compte au chaud.",
  },
  {
    id: "d3_sulking",
    kind: "reengage",
    day: 3,
    title: () => "Kiko boude un peu 💧",
    body: (c) => (hasPartner(c) ? `Trois jours sans nouvelles. Un message à ${p(c)} et tout est oublié.` : "Trois jours sans nouvelles. Ce n'est pas grave, reviens quand tu veux."),
  },
  {
    id: "d7_week",
    kind: "reengage",
    day: 7,
    title: () => "Une semaine déjà",
    body: () => "Tes partenaires parlent toujours. Deux minutes suffisent pour reprendre le fil.",
  },
  {
    id: "d7_voice",
    kind: "reengage",
    day: 7,
    nudgesPartner: true,
    when: hasPartner,
    title: () => "Kiko a une idée",
    body: (c) => `Envoie un vocal de 10 secondes à ${p(c)}. C'est la façon la plus simple de reprendre.`,
  },
  {
    id: "d14_goodbye",
    kind: "reengage",
    day: 14,
    title: () => "On arrête les rappels",
    body: () => "Ils ne semblent pas marcher. Kiko ne t'écrira plus, mais il sera là quand tu reviens. 💛",
  },
];

/** Les variantes possibles pour une personne, dans l'ordre du tableau. */
export function eligible(c: Candidate): Template[] {
  return templates.filter((t) =>
    t.kind === c.kind &&
    (t.day == null || t.day === c.days_inactive) &&
    (!t.nudgesPartner || !c.partner_night) &&
    (t.when == null || t.when(c))
  );
}

// ---------------------------------------------------------------------------
// Messages, corrections, appels : le texte vient de ce qui s'est passé.
// ---------------------------------------------------------------------------

/** « Asia/Jakarta » → « Jakarta ». */
export function cityOf(tz: string | null | undefined): string {
  if (!tz || tz === "UTC") return "UTC";
  return tz.split("/").pop()!.replaceAll("_", " ");
}

export function formatTime(iso: string, tz: string): string {
  return new Intl.DateTimeFormat("fr-FR", { timeZone: safeTz(tz), hour: "2-digit", minute: "2-digit" }).format(new Date(iso));
}

/** « aujourd'hui », « demain » ou « samedi », à l'heure de la personne. */
export function formatDay(iso: string, tz: string, now: Date): string {
  const day = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: safeTz(tz) }).format(d);
  const target = day(new Date(iso));
  if (target === day(now)) return "aujourd'hui";
  if (target === day(new Date(now.getTime() + 86_400_000))) return "demain";
  return new Intl.DateTimeFormat("fr-FR", { timeZone: safeTz(tz), weekday: "long", day: "numeric", month: "long" }).format(new Date(iso));
}

function safeTz(tz: string): string {
  try {
    new Intl.DateTimeFormat("fr-FR", { timeZone: tz });
    return tz;
  } catch {
    return "UTC";
  }
}

export function milestoneBody(n: number, partner: string): string {
  switch (n) {
    case 7: return `Une semaine à deux, votre meilleure série. Envoie un bravo à ${partner}.`;
    case 30: return `Un mois sans jamais vous lâcher. Envoie un bravo à ${partner}, ce succès est aussi le sien.`;
    case 100: return `Cent jours ! Peu de duos tiennent aussi longtemps. Bravo à vous deux.`;
    default: return `Un an de conversations avec ${partner}. Bravo à vous deux.`;
  }
}

export interface Rendered {
  title: string;
  body: string;
}

// deno-lint-ignore no-explicit-any
type Payload = Record<string, any>;

/** Le titre et le texte d'une notification de la file, à l'heure du destinataire. */
export function render(kind: string, payload: Payload, tz: string, now: Date): Rendered | null {
  if (typeof payload.title === "string" && typeof payload.body === "string") {
    return { title: payload.title, body: payload.body };
  }
  const who = payload.sender_name ?? payload.partner_name ?? "Quelqu'un";
  const when = (iso: string | undefined) =>
    iso ? `${capitalize(formatDay(iso, tz, now))} à ${formatTime(iso, tz)}` : "Ouvre la discussion pour voir l'heure.";
  switch (kind) {
    case "message":
      return { title: who, body: payload.preview ?? "" };
    case "request":
      return { title: `${who} veut discuter avec toi`, body: payload.preview ?? "" };
    case "correction":
      return {
        title: `${who} a corrigé ton message`,
        body: [payload.corrected, payload.note].filter((s) => s).join("\n"),
      };
    case "call":
      return { title: `${who} te propose un appel`, body: when(payload.at) };
    case "call_accepted":
      return { title: `${who} a accepté ton appel`, body: when(payload.at) };
    case "call_soon": {
      const minutes = Math.max(1, Math.round((Date.parse(payload.at) - now.getTime()) / 60_000));
      return {
        title: `Appel avec ${who} dans ${minutes} min`,
        body: `${formatTime(payload.at, tz)} chez toi · ${formatTime(payload.at, payload.partner_time_zone ?? "UTC")} à ${cityOf(payload.partner_time_zone)}`,
      };
    }
    case "milestone":
      return { title: `${payload.streak} jours avec ${who} !`, body: milestoneBody(Number(payload.streak), who) };
    default:
      return null;
  }
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
