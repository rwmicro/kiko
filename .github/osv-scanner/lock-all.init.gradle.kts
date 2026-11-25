// Script d'initialisation Gradle utilisé uniquement par le job OSV-Scanner (security.yml).
//
// OSV-Scanner ne lit pas gradle/libs.versions.toml : il lui faut la liste exacte des
// versions résolues, dépendances transitives comprises. Ce script active le verrouillage
// des dépendances sur tous les modules et ajoute une tâche qui résout toutes les
// configurations. Lancé avec --write-locks, il produit un gradle.lockfile par module,
// que OSV-Scanner sait lire. Rien n'est commité : les fichiers vivent le temps du job.
//
//   ./gradlew --init-script .github/osv-scanner/lock-all.init.gradle.kts \
//     --no-configuration-cache osvResolveAndLockAll --write-locks

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }

    // Capturés à la configuration : Task.project est déconseillé pendant l'exécution.
    val allConfigurations = configurations
    val projectPath = path
    val writeLocks = gradle.startParameter.isWriteDependencyLocks

    tasks.register("osvResolveAndLockAll") {
        description = "Résout toutes les configurations pour écrire les gradle.lockfile (OSV-Scanner)."
        notCompatibleWithConfigurationCache("Parcourt les configurations à l'exécution")
        doFirst {
            require(writeLocks) {
                "Lancer cette tâche avec --write-locks"
            }
        }
        doLast {
            allConfigurations
                .toList() // copie : résoudre peut ajouter des configurations au conteneur
                .filter { it.isCanBeResolved }
                .forEach { configuration ->
                    // Le graphe suffit (pas besoin de télécharger les artefacts). Certaines
                    // configurations Kotlin Multiplatform ne se résolvent pas sous Linux
                    // (cibles iOS) : on les signale sans faire échouer le job.
                    runCatching { configuration.incoming.resolutionResult.root }
                        .onFailure { logger.warn("OSV : $projectPath:${configuration.name} non résolue (${it.message?.lineSequence()?.firstOrNull()})") }
                }
        }
    }
}
