# Sécurité

## Signaler une faille

Merci de ne pas ouvrir d'issue publique. Utilise le bouton **Report a vulnerability**
de l'onglet Security du dépôt (signalement privé GitHub). Nous répondons sous 72 h.

## Contrôles automatiques

| Contrôle | Outil | Quand | Bloque la PR |
| --- | --- | --- | --- |
| Secrets dans le code | Gitleaks (CI + hook pre-commit facultatif) | Chaque PR, chaque semaine | Oui |
| Dépendances vulnérables | Revue des dépendances, Dependabot | Chaque PR, chaque semaine | Oui, à partir de « high » |
| Analyse statique | Semgrep (Kotlin, SQL, secrets, workflows), CodeQL (workflows) | Chaque PR | Oui (Semgrep, gravité ERROR) |
| Sécurité mobile | mobsfscan (+ règles semgrep Kotlin/Swift) | Chaque PR | Oui, gravité ERROR ; faux positifs justifiés dans `.mobsf` |
| Qualité du code | detekt (nouveaux problèmes), ktlint (conseil) | Chaque PR qui touche du Kotlin | detekt oui ; existant dans `config/detekt/baseline.xml` |
| Wrapper Gradle authentique | gradle/actions/wrapper-validation | Chaque PR | Oui |
| Règles d'accès de la base | `tools/ci/rls-tests.sh` : migrations sur Postgres jetable, tests « doit échouer », scénarios, contrôle du schéma (`supabase/tests/schema_lint.sql`) | Chaque PR qui touche `supabase/` | Oui |
| Chaîne d'approvisionnement CI | Actions GitHub épinglées par SHA, jeton en lecture seule par défaut | Toujours | — |
| Scan dynamique et fuzzing de l'API | OWASP ZAP, Schemathesis | Chaque nuit, **uniquement** sur l'environnement de test | Non (ouvre une issue) |

Les résultats sont dans Security > Code scanning. Les scans de nuit restent
désactivés tant que l'environnement de test n'existe pas (variable
`STAGING_SUPABASE_URL`).
