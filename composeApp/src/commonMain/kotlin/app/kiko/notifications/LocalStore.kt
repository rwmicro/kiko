package app.kiko.notifications

/** Petits réglages gardés sur le téléphone : la demande de notifications déjà faite, par exemple. */
expect object LocalStore {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}
