#!/usr/bin/env bash
# À lancer UNE SEULE FOIS par environnement, depuis « Déploiement de la base » (lancement manuel, champ repair_until).
#
# Les premières migrations ont été collées à la main dans le SQL Editor : Supabase ne sait donc pas
# qu'elles sont appliquées, et `supabase db push` voudrait les rejouer. Ce script les inscrit comme
# « appliquées » dans l'historique, sans exécuter leur SQL, jusqu'à la version donnée incluse.
set -euo pipefail
cd "$(dirname "$0")/../.."

until_version="${1:?Version attendue, par exemple 20261118030000}"
[[ "$until_version" =~ ^[0-9]{14}$ ]] || { echo "::error::Version invalide : $until_version (14 chiffres attendus)"; exit 1; }

versions=()
for f in supabase/migrations/*.sql; do
  v=$(basename "$f" | cut -d_ -f1)
  if [[ "$v" < "$until_version" || "$v" == "$until_version" ]]; then versions+=("$v"); fi
done
[ ${#versions[@]} -gt 0 ] || { echo "::error::Aucune migration jusqu'à $until_version"; exit 1; }

echo "Marquées comme déjà appliquées : ${versions[*]}"
supabase migration repair --status applied "${versions[@]}"
supabase migration list
