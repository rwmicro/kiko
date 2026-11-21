// Le choix de la variante d'une relance, à la manière de Duolingo (« A Sleeping, Recovering Bandit
// Algorithm for Optimizing Recurring Notifications », KDD 2020) :
//  - chaque variante a un taux d'ouverture estimé, tiré au hasard dans sa loi bêta (échantillonnage
//    de Thompson) : les bonnes variantes gagnent souvent, les nouvelles ont leur chance ;
//  - une variante déjà reçue récemment est pénalisée, et la pénalité s'efface avec une demi-vie
//    de 15 jours : on ne répète pas le même texte à la même personne.

export interface Stats {
  sent: number;
  opened: number;
}

export const HalfLifeDays = 15;
/** Part du score retirée à une variante reçue à l'instant. */
export const RecencyWeight = 0.6;

/** Tirage dans une loi gamma (Marsaglia et Tsang), pour en déduire une loi bêta. */
function gamma(shape: number, rand: () => number): number {
  if (shape < 1) return gamma(shape + 1, rand) * Math.pow(rand(), 1 / shape);
  const d = shape - 1 / 3;
  const c = 1 / Math.sqrt(9 * d);
  for (;;) {
    let x: number, v: number;
    do {
      // Box-Muller
      const u1 = Math.max(rand(), 1e-12);
      const u2 = rand();
      x = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
      v = 1 + c * x;
    } while (v <= 0);
    v = v * v * v;
    const u = Math.max(rand(), 1e-12);
    if (Math.log(u) < 0.5 * x * x + d - d * v + d * Math.log(v)) return d * v;
  }
}

export function sampleBeta(a: number, b: number, rand: () => number = Math.random): number {
  const x = gamma(a, rand);
  const y = gamma(b, rand);
  return x / (x + y);
}

/** Pénalité de fraîcheur : 1 si reçue à l'instant, 0,5 au bout de 15 jours, 0 si jamais reçue. */
export function recency(lastSent: Date | undefined, now: Date): number {
  if (!lastSent) return 0;
  const days = Math.max(0, (now.getTime() - lastSent.getTime()) / 86_400_000);
  return Math.pow(0.5, days / HalfLifeDays);
}

export function score(stats: Stats | undefined, lastSent: Date | undefined, now: Date, rand: () => number = Math.random): number {
  const sent = stats?.sent ?? 0;
  const opened = Math.min(stats?.opened ?? 0, sent);
  // Loi a priori : une ouverture sur dix, assez faible pour que les données l'emportent vite.
  const sample = sampleBeta(1 + opened, 9 + sent - opened, rand);
  return sample * (1 - RecencyWeight * recency(lastSent, now));
}

/** La variante au meilleur score ; null si la liste est vide. */
export function choose(
  ids: string[],
  stats: Map<string, Stats>,
  history: Map<string, Date>,
  now: Date,
  rand: () => number = Math.random,
): string | null {
  let best: string | null = null;
  let bestScore = -1;
  for (const id of ids) {
    const s = score(stats.get(id), history.get(id), now, rand);
    if (s > bestScore) {
      best = id;
      bestScore = s;
    }
  }
  return best;
}
