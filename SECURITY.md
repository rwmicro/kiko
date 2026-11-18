# Sécurité

## Signaler une faille

Merci de ne pas ouvrir d'issue publique. Utilise le bouton **Report a vulnerability**
de l'onglet Security du dépôt (signalement privé GitHub). Nous répondons sous 72 h.

## Contrôles automatiques

| Contrôle | Outil | Quand |
| --- | --- | --- |
| Secrets dans le code | Gitleaks | Chaque PR, chaque semaine |
| Dépendances vulnérables | Revue des dépendances, Dependabot | Chaque PR, chaque semaine |
| Analyse statique | Semgrep (Kotlin, SQL, secrets, workflows), CodeQL (workflows) | Chaque PR |
| Sécurité mobile | mobsfscan | Chaque PR |
| Qualité du code | ktlint, detekt (mode conseil) | Chaque PR qui touche du Kotlin |
| Règles d'accès de la base | Tests SQL (`supabase/tests`) | Chaque changement de schéma |
| Scan dynamique et fuzzing de l'API | OWASP ZAP, Schemathesis | Chaque nuit, **uniquement** sur l'environnement de test |

Les résultats sont dans Security > Code scanning. Les scans de nuit restent
désactivés tant que l'environnement de test n'existe pas (variable
`STAGING_SUPABASE_URL`).
