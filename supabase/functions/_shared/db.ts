// Accès à la base depuis les fonctions Edge. SUPABASE_URL et SUPABASE_SERVICE_ROLE_KEY sont
// fournis par Supabase à chaque fonction : la clé ne quitte jamais le serveur.
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";

export function adminClient(): SupabaseClient {
  return createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

/** Seule la base (pg_net, avec le secret rangé dans Vault) réveille ces fonctions. */
export async function authorized(req: Request, db: SupabaseClient): Promise<boolean> {
  const candidate = req.headers.get("x-kiko-push-secret");
  if (!candidate) return false;
  const { data, error } = await db.rpc("check_push_secret", { candidate });
  return !error && data === true;
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
