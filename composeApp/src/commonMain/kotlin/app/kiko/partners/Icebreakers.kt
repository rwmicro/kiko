package app.kiko.partners

/**
 * Une idée de premier message. [text] est écrit dans chaque langue de la bêta :
 * on l'envoie dans la langue natale du partenaire, et on montre la traduction dans la sienne.
 */
data class Icebreaker(val topic: String?, val text: Map<String, String>)

private fun idea(topic: String?, fr: String, id: String) = Icebreaker(topic, mapOf("fr" to fr, "id" to id))

private val ByInterest = listOf(
    idea("Cinéma", "Quel est le dernier film qui t'a fait pleurer ?", "Film apa yang terakhir membuatmu menangis?"),
    idea("Cuisine", "Quel plat de ton pays je devrais goûter en premier ?", "Makanan apa dari negaramu yang harus kucoba pertama kali?"),
    idea("Voyage", "Quel voyage t'a le plus marqué ?", "Perjalanan mana yang paling berkesan untukmu?"),
    idea("Musique", "Tu écoutes quoi en ce moment ?", "Akhir-akhir ini kamu suka dengar lagu apa?"),
    idea("Foot", "Tu supportes quelle équipe ?", "Kamu dukung tim mana?"),
    idea("Lecture", "Quel livre tu offrirais à un ami ?", "Buku apa yang akan kamu berikan ke teman?"),
    idea("Jeux vidéo", "À quel jeu tu joues en ce moment ?", "Sekarang kamu lagi main game apa?"),
    idea("Randonnée", "Quelle est la plus belle randonnée que tu aies faite ?", "Pendakian apa yang paling indah yang pernah kamu lakukan?"),
    idea("Photo", "Tu prends plutôt des photos de gens ou de paysages ?", "Kamu lebih suka memotret orang atau pemandangan?"),
    idea("Art", "Quel artiste de ton pays je devrais connaître ?", "Seniman dari negaramu siapa yang harus kukenal?"),
    idea("Animaux", "Tu as un animal ? Comment il s'appelle ?", "Kamu punya hewan peliharaan? Siapa namanya?"),
    idea("Yoga", "Ça fait longtemps que tu fais du yoga ?", "Sudah berapa lama kamu latihan yoga?"),
    idea("Badminton", "Tu joues au badminton où, d'habitude ?", "Biasanya kamu main bulu tangkis di mana?"),
)

private val Generic = listOf(
    idea(null, "Salut ! Depuis combien de temps tu apprends ma langue ?", "Halo! Sudah berapa lama kamu belajar bahasaku?"),
    idea(null, "Il est quelle heure chez toi ? Tu fais quoi en ce moment ?", "Jam berapa sekarang di tempatmu? Kamu sedang apa?"),
    idea(null, "Tu me conseillerais quel endroit dans ta ville ?", "Ada tempat di kotamu yang kamu rekomendasikan untukku?"),
)

/** Idées pour saluer : d'abord celles des intérêts communs, puis des questions pour tous. */
fun icebreakersFor(commonInterests: List<String>): List<Icebreaker> =
    ByInterest.filter { it.topic in commonInterests } + Generic

/** Le texte d'une idée dans une langue, avec repli sur le français. */
fun Icebreaker.textIn(language: String): String = text[language] ?: text.getValue("fr")
