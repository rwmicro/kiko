// Vide la file d'envoi (push_outbox) : FCM pour Android, APNs pour iPhone.
// Réveillée par la base à chaque nouvelle ligne, et chaque minute s'il reste des envois en retard.
import { adminClient, authorized, json } from "../_shared/db.ts";
import { deliveries, type Delivery, type OutboxRow } from "../_shared/payloads.ts";
import { sendFcm, type SendResult } from "../_shared/fcm.ts";
import { bundleId, sendApns } from "../_shared/apns.ts";

const BudgetMs = 40_000;
const Parallel = 16;

Deno.serve(async (req) => {
  const db = adminClient();
  if (!(await authorized(req, db))) return json({ error: "unauthorized" }, 401);

  const started = Date.now();
  let sent = 0;
  let failed = 0;
  while (Date.now() - started < BudgetMs) {
    const { data, error } = await db.rpc("claim_push_batch", { max_rows: 100 });
    if (error) return json({ error: error.message }, 500);
    const rows = (data ?? []) as OutboxRow[];
    if (rows.length === 0) break;

    const now = new Date();
    const jobs: { row: OutboxRow; delivery: Delivery }[] = rows.flatMap((row) =>
      deliveries(row, now, bundleId()).map((delivery) => ({ row, delivery }))
    );
    const errors = new Map<number, string>();
    const dead = new Set<string>();

    await pool(jobs, Parallel, async ({ row, delivery }) => {
      let result: SendResult;
      try {
        result = delivery.fcm ? await sendFcm(delivery.fcm) : await sendApns(delivery.apns!);
      } catch (e) {
        result = { ok: false, invalidToken: false, error: String(e) };
      }
      if (result.ok) {
        sent++;
        if (delivery.dropAfter) dead.add(delivery.device.token);
      } else {
        failed++;
        errors.set(row.id, result.error);
        if (result.invalidToken || delivery.dropAfter) dead.add(delivery.device.token);
      }
    });

    for (const token of dead) await db.rpc("drop_push_token", { p_token: token });
    await Promise.all(rows.map((row) => db.rpc("finish_push", { p_id: row.id, p_error: errors.get(row.id) ?? null })));
  }
  return json({ sent, failed });
});

async function pool<T>(items: T[], size: number, work: (item: T) => Promise<void>): Promise<void> {
  let next = 0;
  const runners = Array.from({ length: Math.min(size, items.length) }, async () => {
    while (next < items.length) await work(items[next++]);
  });
  await Promise.all(runners);
}
