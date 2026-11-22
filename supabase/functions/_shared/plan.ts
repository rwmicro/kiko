// De la liste des personnes à relancer aux lignes de la file d'envoi.
import { choose } from "./variants.ts";
import { type Candidate, eligible, templates } from "./copy.ts";

export interface Planned {
  user_id: string;
  kind: string;
  template: string | null;
  dedupe_key: string;
  // deno-lint-ignore no-explicit-any
  payload: Record<string, any>;
}

export function plan(people: Candidate[], history: Map<string, Map<string, Date>>, now: Date): Planned[] {
  const out: Planned[] = [];
  for (const c of people) {
    const options = eligible(c);
    const id = choose(options.map((t) => t.id), history.get(c.user_id) ?? new Map());
    const t = templates.find((x) => x.id === id);
    if (!t) continue;
    const payload: Record<string, unknown> = {
      title: t.title(c),
      body: t.body(c),
      conversation_id: c.conversation_id,
      partner_name: c.partner_name,
      streak: c.streak,
    };
    if (c.kind === "streak_risk" && c.minutes_left != null) {
      payload.deadline = new Date(now.getTime() + c.minutes_left * 60_000).toISOString();
    }
    out.push({ user_id: c.user_id, kind: c.kind, template: t.id, dedupe_key: c.dedupe_key, payload });
  }
  return out;
}
