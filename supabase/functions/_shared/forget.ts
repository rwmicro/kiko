// Supprimer un compte pour de bon : fichiers des discussions, discussions, puis le compte lui-même
// (profil, langues, appareils, préférences et notifications partent avec, par cascade).
import type { SupabaseClient } from "npm:@supabase/supabase-js@2";

export const MEDIA_BUCKET = "chat-media";
const PAGE = 100;

/** Chemins « <discussion>/<fichier> » d'une page de liste du stockage. */
export function mediaPaths(conversation: string, files: { name: string }[]): string[] {
  return files.filter((f) => f.name && !f.name.includes("/")).map((f) => `${conversation}/${f.name}`);
}

export async function forget(db: SupabaseClient, userId: string): Promise<void> {
  const convs = await db.rpc("account_conversations", { p_user: userId });
  if (convs.error) throw new Error(convs.error.message);

  for (const conversation of (convs.data ?? []) as string[]) {
    // On efface page par page : chaque tour retire ce qu'il vient de lister.
    for (;;) {
      const list = await db.storage.from(MEDIA_BUCKET).list(conversation, { limit: PAGE });
      if (list.error) throw new Error(list.error.message);
      const paths = mediaPaths(conversation, list.data ?? []);
      if (paths.length === 0) break;
      const removed = await db.storage.from(MEDIA_BUCKET).remove(paths);
      if (removed.error) throw new Error(removed.error.message);
      if (paths.length < PAGE) break;
    }
  }

  const data = await db.rpc("forget_account_data", { p_user: userId });
  if (data.error) throw new Error(data.error.message);

  const user = await db.auth.admin.deleteUser(userId);
  if (user.error && user.error.status !== 404) throw new Error(user.error.message);
}
