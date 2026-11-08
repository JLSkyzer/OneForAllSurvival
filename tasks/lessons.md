# Leçons du dépôt

Format : [date] | ce qui a mal tourné | cause | règle pour l'éviter.

- [2026-11-08] | Des déplacements de plans faits par le contrôleur (`git mv`) sont partis dans le commit d'un sous-agent (bcaa1bd, « build.gradle » seul annoncé) | `git mv` met le changement dans l'index partagé ; le sous-agent, qui travaillait dans le même dossier, a fait `git commit` avec cet index | Pendant qu'un agent travaille dans le même dossier, ne rien laisser dans l'index : déplacer avec `Move-Item`/`mv`, puis commiter seulement ses chemins par `git commit -- <chemins>` (qui ignore le reste de l'index), dans la même commande.
