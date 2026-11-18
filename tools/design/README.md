# Outils design

Les éléments visuels de l'app viennent de la maquette `prototype-lezo.html` (dossier design du projet).

## Pictogrammes et mascotte

`svg2kt.py` convertit un symbole SVG de la maquette en code `ImageVector` Compose :

```
python3 tools/design/svg2kt.py i-chat mono  chemin/vers/prototype-lezo.html   # pictogramme teinté
python3 tools/design/svg2kt.py axo    color chemin/vers/prototype-lezo.html   # illustration en couleurs
```

Le résultat va dans `ui/components/KikoIcons.kt` ou `ui/components/MascotVectors.kt`.

## Police Allo

La maquette embarque la police variable « Allo » (Josh Puckett, dérivée d'Inter, licence SIL Open Font License 1.1).
L'app en garde des instances fixes dans `composeApp/src/commonMain/composeResources/font/` :

| Fichier | Graisse | ROND | opsz |
| --- | --- | --- | --- |
| `allo_text_*` | 500 à 800 | 50 | 14 |
| `allo_round_*` | 800 et 900 | 100 | 24 |

Elles sont produites avec fontTools : `instancer.instantiateVariableFont(font, {"wght": w, "ROND": r, "opsz": o})`.
La licence OFL autorise l'intégration dans l'app ; elle interdit seulement de vendre la police seule.
