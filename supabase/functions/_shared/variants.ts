// Le choix du texte d'une relance. Comme chez Duolingo, plusieurs variantes par situation, pour ne
// pas répéter la même phrase. Mais sans rien mesurer de ce que fait la personne (pas de suivi des
// ouvertures) : on prend une variante jamais reçue, sinon celle reçue il y a le plus longtemps.

/** La variante à envoyer ; null si la liste est vide. */
export function choose(ids: string[], history: Map<string, Date>): string | null {
  let best: string | null = null;
  let oldest = Infinity;
  for (const id of ids) {
    const last = history.get(id)?.getTime() ?? -Infinity;
    if (last < oldest) {
      best = id;
      oldest = last;
    }
  }
  return best;
}
