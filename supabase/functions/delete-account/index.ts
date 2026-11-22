// « Supprimer mon compte » depuis l'app (jeton de la personne connectée), et chaque nuit, appelée par
// la base avec le secret rangé dans Vault : e-mail aux comptes sans ouverture de l'app depuis 2 ans,
// puis suppression 30 jours plus tard s'ils ne sont pas revenus.
import { adminClient, authorized } from "../_shared/db.ts";
import { forget } from "../_shared/forget.ts";
import { inactivityWarning, mailConfigured, sendMail } from "../_shared/mail.ts";

// L'aperçu web appelle la fonction depuis le navigateur.
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function reply(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: cors });
  if (req.method !== "POST") return reply({ error: "method not allowed" }, 405);
  const db = adminClient();

  if (await authorized(req, db)) {
    // Sans moyen de prévenir, personne n'est supprimé.
    if (!mailConfigured()) return reply({ warned: 0, deleted: 0, skipped: "BREVO_API_KEY manquant" });

    const due = await db.rpc("accounts_to_warn", { p_limit: 50 });
    if (due.error) return reply({ error: due.error.message }, 500);
    let warned = 0;
    for (const person of (due.data ?? []) as { id: string; first_name: string | null }[]) {
      try {
        const { data: found, error } = await db.auth.admin.getUserById(person.id);
        if (error || !found.user?.email) throw new Error(error?.message ?? "pas d'e-mail");
        await sendMail(found.user.email, inactivityWarning(person.first_name));
        const mark = await db.rpc("mark_inactivity_warned", { p_user: person.id });
        if (mark.error) throw new Error(mark.error.message);
        warned++;
      } catch (e) {
        console.error("inactivity warning not sent:", (e as Error).message);
      }
    }

    const ids = await db.rpc("inactive_accounts", { p_limit: 50 });
    if (ids.error) return reply({ error: ids.error.message }, 500);
    let deleted = 0;
    for (const id of (ids.data ?? []) as string[]) {
      try {
        await forget(db, id);
        deleted++;
      } catch (e) {
        console.error("inactive account not deleted:", (e as Error).message);
      }
    }
    return reply({ warned, deleted });
  }

  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return reply({ error: "unauthorized" }, 401);
  const { data, error } = await db.auth.getUser(token);
  if (error || !data.user) return reply({ error: "unauthorized" }, 401);

  try {
    await forget(db, data.user.id);
  } catch (e) {
    return reply({ error: (e as Error).message }, 500);
  }
  return reply({ deleted: true });
});
