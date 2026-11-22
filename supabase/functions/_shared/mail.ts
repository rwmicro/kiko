// E-mail de prévenance avant la suppression d'un compte inactif, envoyé par Brevo (service français,
// données hébergées en Europe). Secrets : BREVO_API_KEY ; MAIL_FROM est facultatif.
// Sans clé, aucun e-mail ne part et aucun compte inactif n'est supprimé.

export const CONTACT = "contact@ziko.help";

export interface Mail {
  subject: string;
  text: string;
}

/** En français et en indonésien : l'app relie les deux langues, on ne sait pas laquelle la personne lit. */
export function inactivityWarning(firstName: string | null): Mail {
  const name = firstName?.trim() || "";
  const fr = name ? `Bonjour ${name},` : "Bonjour,";
  const id = name ? `Halo ${name},` : "Halo,";
  return {
    subject: "Ton compte Kiko sera supprimé dans 30 jours · Akun Kiko-mu akan dihapus dalam 30 hari",
    text: [
      fr,
      "",
      "Tu n'as pas ouvert Kiko depuis 2 ans. Pour ne pas garder tes données sans raison, ton compte, " +
      "tes discussions, tes photos et tes vocaux seront supprimés dans 30 jours.",
      "",
      "Tu veux le garder ? Ouvre simplement l'app et connecte-toi : rien ne sera supprimé.",
      "",
      `Une question : ${CONTACT}`,
      "",
      "—",
      "",
      id,
      "",
      "Kamu belum membuka Kiko selama 2 tahun. Agar datamu tidak disimpan tanpa alasan, akun, " +
      "percakapan, foto, dan pesan suaramu akan dihapus dalam 30 hari.",
      "",
      "Ingin tetap menyimpannya? Cukup buka aplikasinya dan masuk: tidak ada yang akan dihapus.",
      "",
      `Ada pertanyaan: ${CONTACT}`,
    ].join("\n"),
  };
}

export function mailConfigured(): boolean {
  return !!Deno.env.get("BREVO_API_KEY");
}

export async function sendMail(to: string, mail: Mail): Promise<void> {
  const key = Deno.env.get("BREVO_API_KEY");
  if (!key) throw new Error("BREVO_API_KEY manquant");
  const res = await fetch("https://api.brevo.com/v3/smtp/email", {
    method: "POST",
    headers: { "api-key": key, "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({
      sender: { name: "Kiko", email: Deno.env.get("MAIL_FROM") || CONTACT },
      to: [{ email: to }],
      subject: mail.subject,
      textContent: mail.text,
      // Pas de pixel ni de liens suivis : Brevo ne mesure ni ouverture ni clic sur un e-mail texte.
    }),
  });
  if (!res.ok) throw new Error(`Brevo ${res.status}: ${(await res.text()).slice(0, 200)}`);
}
