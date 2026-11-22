# Règles R8 de la version release.
# À vérifier sur la piste de test interne Play avant toute mise en production :
# un crash au démarrage d'un build release vient presque toujours d'une règle manquante ici.

# Garder les numéros de ligne dans les traces de crash (le fichier de correspondance est envoyé à Play).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization (modèles Supabase annotés @Serializable).
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion { kotlinx.serialization.KSerializer serializer(...); }

# Ktor et OkHttp : classes optionnelles chargées par réflexion.
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class io.ktor.client.engine.okhttp.** { *; }

# Koin : modules déclarés par lambdas, rien à garder de plus.
