#!/usr/bin/env bash
# Rejoue les migrations sur un Postgres jetable, puis chaque test de supabase/tests.
#
# Deux sortes de tests :
# - un fichier qui contient « doit échouer » : chaque ligne marquée ainsi DOIT finir en erreur,
#   et toutes les autres DOIVENT passer (comparaison des numéros de ligne rapportés par psql) ;
# - les autres fichiers (scénarios) : tout doit passer, sans aucune erreur.
#
# Variables : PGHOST, PGPORT, PGUSER, PGPASSWORD (Postgres de test, jamais Supabase).
set -euo pipefail
export LC_ALL=C # comm exige un tri lexical identique des deux listes
cd "$(dirname "$0")/../.."

stub=supabase/tests/00_local_stub.sql
migrations=(supabase/migrations/*.sql)
failed=0
logs=$(mktemp -d)

fresh_db() {
  dropdb --if-exists "$1"
  createdb "$1"
  local args=(-X -q -v ON_ERROR_STOP=1 -d "$1" -f "$stub")
  for m in "${migrations[@]}"; do args+=(-f "$m"); done
  psql "${args[@]}" >/dev/null 2>"$logs/$1.load.log" || { cat "$logs/$1.load.log"; echo "::error::Les migrations ne passent pas sur une base vide."; exit 1; }
}

for test in supabase/tests/*.sql; do
  [ "$test" = "$stub" ] && continue
  name=$(basename "$test" .sql)
  db="kiko_${name//[^a-zA-Z0-9]/_}"
  fresh_db "$db"
  echo "== $test"
  if grep -q "doit échouer" "$test"; then
    psql -X -q -d "$db" -f "$test" >/dev/null 2>"$logs/$db.err" || true
    expected=$(grep -n "doit échouer" "$test" | cut -d: -f1 | sort -u)
    actual=$(grep -oE "^psql:[^:]+:[0-9]+: ERROR" "$logs/$db.err" | cut -d: -f3 | sort -u)
    missing=$(comm -23 <(echo "$expected") <(echo "$actual") | sed '/^$/d')
    extra=$(comm -13 <(echo "$expected") <(echo "$actual") | sed '/^$/d')
    for l in $missing; do
      echo "::error file=$test,line=$l::Cette ligne devait être refusée par la base, elle est passée : $(sed -n "${l}p" "$test")"
      failed=1
    done
    for l in $extra; do
      echo "::error file=$test,line=$l::Erreur inattendue : $(grep -E "^psql:[^:]+:$l: ERROR" "$logs/$db.err" | head -1)"
      failed=1
    done
    [ -z "$missing$extra" ] && echo "OK : $(echo "$expected" | wc -l) refus attendus, tous obtenus"
  else
    if psql -X -q -v ON_ERROR_STOP=1 -d "$db" -f "$test" >"$logs/$db.out" 2>"$logs/$db.err"; then
      echo "OK"
    else
      cat "$logs/$db.out" "$logs/$db.err"
      echo "::error file=$test::Le scénario s'arrête sur une erreur."
      failed=1
    fi
  fi
done

exit $failed
