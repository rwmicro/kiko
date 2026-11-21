// Relances et appels qui approchent, toutes les 5 minutes (tâche pg_cron « kiko-reminders »).
// La base dit qui relancer et pourquoi (reminder_candidates) ; ici on choisit le texte.
import { adminClient, authorized, json } from "../_shared/db.ts";
import { plan, type Planned } from "../_shared/plan.ts";
import type { Candidate } from "../_shared/copy.ts";
import type { Stats } from "../_shared/bandit.ts";

Deno.serve(async (req) => {
  const db = adminClient();
  if (!(await authorized(req, db))) return json({ error: "unauthorized" }, 401);
  const now = new Date();

  const calls = await db.rpc("due_call_alerts", { p_now: now.toISOString() });
  if (calls.error) return json({ error: calls.error.message }, 500);

  const candidates = await db.rpc("reminder_candidates", { p_now: now.toISOString() });
  if (candidates.error) return json({ error: candidates.error.message }, 500);
  const people = (candidates.data ?? []) as Candidate[];

  const stats = new Map<string, Stats>();
  const history = new Map<string, Map<string, Date>>();
  if (people.length > 0) {
    const s = await db.rpc("template_stats");
    for (const r of (s.data ?? []) as { template: string; sent: number; opened: number }[]) {
      stats.set(r.template, { sent: r.sent, opened: r.opened });
    }
    const h = await db.rpc("reminder_history", { users: people.map((p) => p.user_id) });
    for (const r of (h.data ?? []) as { user_id: string; template: string; sent_at: string }[]) {
      if (!history.has(r.user_id)) history.set(r.user_id, new Map());
      history.get(r.user_id)!.set(r.template, new Date(r.sent_at));
    }
  }

  const rows: Planned[] = [
    ...((calls.data ?? []) as Planned[]).map((c) => ({ ...c, template: null })),
    ...plan(people, stats, history, now),
  ];
  if (rows.length > 0) {
    const { error } = await db.from("push_outbox").upsert(rows, { onConflict: "dedupe_key", ignoreDuplicates: true });
    if (error) return json({ error: error.message }, 500);
  }
  return json({ queued: rows.length });
});
