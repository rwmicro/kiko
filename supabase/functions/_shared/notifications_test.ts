// deno test supabase/functions/_shared
import assert from "node:assert/strict";
import { choose } from "./variants.ts";
import { mediaPaths } from "./forget.ts";
import { CONTACT, inactivityWarning } from "./mail.ts";
import { type Candidate, cityOf, eligible, formatDay, formatTime, render, templates } from "./copy.ts";
import { deliveries, type OutboxRow } from "./payloads.ts";
import { plan } from "./plan.ts";
import { claims, isUuid, livekitToken } from "./livekit.ts";

const base: Candidate = {
  user_id: "u1",
  kind: "reengage",
  dedupe_key: "k",
  conversation_id: "c1",
  first_name: "Martin",
  partner_name: "Ayu",
  partner_night: false,
  partner_waiting: false,
  streak: 6,
  minutes_left: null,
  days_inactive: 1,
  months: null,
  online_partners: 0,
};

Deno.test("chaque variante a un identifiant unique et un texte non vide", () => {
  const ids = new Set(templates.map((t) => t.id));
  assert.equal(ids.size, templates.length);
  for (const t of templates) {
    const c = { ...base, kind: t.kind, days_inactive: t.day ?? 1, minutes_left: 47, months: 1, partner_waiting: true };
    assert.ok(t.title(c).length > 0 && t.body(c).length > 0, t.id);
    assert.ok(!t.body(c).includes("undefined") && !t.title(c).includes("null"), t.id);
  }
});

Deno.test("chaque jour de relance a au moins une variante, même sans partenaire ou de nuit chez lui", () => {
  for (const day of [1, 2, 3, 7, 14]) {
    assert.ok(eligible({ ...base, days_inactive: day, partner_name: null, conversation_id: null }).length > 0, `jour ${day} sans partenaire`);
    assert.ok(eligible({ ...base, days_inactive: day, partner_night: true }).length > 0, `jour ${day} de nuit`);
  }
  assert.equal(eligible({ ...base, days_inactive: 5 }).length, 0);
});

Deno.test("jamais pousser à écrire à quelqu'un chez qui il fait nuit", () => {
  const night = eligible({ ...base, kind: "streak_risk", partner_night: true, minutes_left: 40 });
  assert.deepEqual(night.map((t) => t.id), ["streak_watching"]);
});

Deno.test("le compte à rebours ne sort que dans les 3 dernières heures", () => {
  assert.ok(eligible({ ...base, kind: "streak_risk", minutes_left: 47 }).some((t) => t.id === "streak_countdown"));
  assert.ok(!eligible({ ...base, kind: "streak_risk", minutes_left: 400 }).some((t) => t.id === "streak_countdown"));
});

Deno.test("le jour 14 annonce qu'on arrête", () => {
  assert.deepEqual(eligible({ ...base, days_inactive: 14 }).map((t) => t.id), ["d14_goodbye"]);
});

Deno.test("variantes : une jamais reçue d'abord, sinon la plus ancienne", () => {
  const now = new Date("2026-11-21T18:00:00Z");
  const day = 86_400_000;
  assert.equal(choose(["a", "b", "c"], new Map()), "a");
  assert.equal(choose(["a", "b", "c"], new Map([["a", now]])), "b");
  const all = new Map([["a", now], ["b", new Date(now.getTime() - 3 * day)], ["c", new Date(now.getTime() - day)]]);
  assert.equal(choose(["a", "b", "c"], all), "b");
  assert.equal(choose([], new Map()), null);
});

Deno.test("plan : une ligne par personne, avec l'échéance de la série", () => {
  const now = new Date("2026-11-21T19:30:00Z");
  const rows = plan([{ ...base, kind: "streak_risk", minutes_left: 270, dedupe_key: "s" }], new Map(), now);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].payload.deadline, "2026-11-22T00:00:00.000Z");
  assert.ok(rows[0].template?.startsWith("streak_"));
});

Deno.test("heures et jours à l'heure de la personne", () => {
  const now = new Date("2026-11-21T10:00:00Z");
  assert.equal(formatTime("2026-11-21T19:00:00Z", "Europe/Paris"), "20:00");
  assert.equal(formatTime("2026-11-21T19:00:00Z", "Asia/Jakarta"), "02:00");
  assert.equal(formatDay("2026-11-21T19:00:00Z", "Europe/Paris", now), "aujourd'hui");
  assert.equal(formatDay("2026-11-21T19:00:00Z", "Asia/Jakarta", now), "demain");
  assert.equal(formatTime("2026-11-21T19:00:00Z", "Pas/UnFuseau"), "19:00");
  assert.equal(cityOf("America/Sao_Paulo"), "Sao Paulo");
});

Deno.test("textes des messages, corrections et appels", () => {
  const now = new Date("2026-11-21T10:00:00Z");
  assert.deepEqual(render("message", { sender_name: "Ayu", preview: "Halo!" }, "Europe/Paris", now), { title: "Ayu", body: "Halo!" });
  assert.equal(render("correction", { sender_name: "Ayu", corrected: "Saya sangat suka.", note: "Ordre" }, "UTC", now)?.body, "Saya sangat suka.\nOrdre");
  assert.equal(render("call", { sender_name: "Ayu", at: "2026-11-22T11:00:00Z" }, "Europe/Paris", now)?.body, "Demain à 12:00");
  const soon = render("call_soon", { partner_name: "Ayu", at: "2026-11-21T10:09:00Z", partner_time_zone: "Asia/Jakarta" }, "Europe/Paris", now);
  assert.deepEqual(soon, { title: "Appel avec Ayu dans 9 min", body: "11:09 chez toi · 17:09 à Jakarta" });
  assert.equal(render("milestone", { partner_name: "Ayu", streak: 30 }, "UTC", now)?.title, "30 jours avec Ayu !");
});

const devices = [
  { token: "android-1", platform: "android", kind: "alert", activity: null, sandbox: false },
  { token: "ios-1", platform: "ios", kind: "alert", activity: null, sandbox: true },
] as OutboxRow["devices"];

Deno.test("Android reçoit des données seulement, l'iPhone une alerte enrichissable", () => {
  const row: OutboxRow = {
    id: 9, user_id: "u", kind: "message", template: null, quiet: false, time_zone: "Europe/Paris",
    payload: { conversation_id: "c1", sender_name: "Ayu", preview: "Halo!", streak: 3 }, devices,
  };
  const [android, ios] = deliveries(row, new Date(), "app.kiko");
  assert.equal(android.fcm?.data.type, "message");
  assert.equal(android.fcm?.data.streak, "3");
  assert.equal(android.fcm?.android.priority, "HIGH");
  assert.equal(ios.apns?.body.aps.category, "MESSAGE");
  assert.equal(ios.apns?.body.aps["thread-id"], "c1");
  assert.equal(ios.apns?.body.aps["mutable-content"], 1);
  assert.equal(ios.apns?.headers["apns-priority"], "10");
  assert.equal(ios.apns?.sandbox, true);
});

Deno.test("la nuit : pas de son, priorité basse", () => {
  const row: OutboxRow = {
    id: 9, user_id: "u", kind: "message", template: null, quiet: true, time_zone: "UTC",
    payload: { sender_name: "Ayu", preview: "x" }, devices,
  };
  const [android, ios] = deliveries(row, new Date(), "app.kiko");
  assert.equal(android.fcm?.android.priority, "NORMAL");
  assert.equal(ios.apns?.body.aps.sound, undefined);
  assert.equal(ios.apns?.body.aps["interruption-level"], "passive");
});

Deno.test("appel bientôt : activité en direct quand l'iPhone a un jeton push-to-start", () => {
  const now = new Date("2026-11-21T18:51:00Z");
  const row: OutboxRow = {
    id: 3, user_id: "u", kind: "call_soon", template: null, quiet: false, time_zone: "Europe/Paris",
    payload: { call_id: "m1", conversation_id: "c1", partner_name: "Ayu", at: "2026-11-21T19:00:00Z", minutes: 20, partner_time_zone: "Asia/Jakarta" },
    devices: [...devices, { token: "start-1", platform: "ios", kind: "activity_start", activity: "call", sandbox: false }],
  };
  const out = deliveries(row, now, "app.kiko");
  assert.deepEqual(out.map((d) => d.device.token), ["android-1", "start-1"]);
  const aps = out[1].apns!.body.aps;
  assert.equal(out[1].apns!.headers["apns-topic"], "app.kiko.push-type.liveactivity");
  assert.equal(aps.event, "start");
  assert.equal(aps["attributes-type"], "CallAttributes");
  assert.equal(aps.attributes.partnerCity, "Jakarta");
  assert.equal(aps["content-state"].startsAtEpoch, Date.parse("2026-11-21T19:00:00Z") / 1000);
});

Deno.test("fin d'activité : seulement les jetons de cette activité, oubliés ensuite", () => {
  const row: OutboxRow = {
    id: 4, user_id: "u", kind: "activity_end", template: null, quiet: false, time_zone: "UTC",
    payload: { activity: "streak:c1" },
    devices: [
      ...devices,
      { token: "upd-1", platform: "ios", kind: "activity_update", activity: "streak:c1", sandbox: false },
      { token: "upd-2", platform: "ios", kind: "activity_update", activity: "streak:c2", sandbox: false },
    ],
  };
  const out = deliveries(row, new Date(), "app.kiko");
  assert.deepEqual(out.map((d) => d.device.token), ["upd-1"]);
  assert.equal(out[0].dropAfter, true);
  assert.equal(out[0].apns!.body.aps.event, "end");
});

Deno.test("suppression de compte : seuls les fichiers de la discussion sont visés", () => {
  const conv = "11111111-1111-1111-1111-111111111111";
  assert.deepEqual(
    mediaPaths(conv, [{ name: "a.jpg" }, { name: "" }, { name: "sous/dossier.m4a" }, { name: "b.m4a" }]),
    [`${conv}/a.jpg`, `${conv}/b.m4a`],
  );
});

Deno.test("e-mail d'inactivité : prénom, deux langues, adresse de contact", () => {
  const mail = inactivityWarning("Ayu");
  assert.ok(mail.text.startsWith("Bonjour Ayu,"));
  assert.ok(mail.text.includes("Halo Ayu,"));
  assert.ok(mail.text.includes(CONTACT));
  assert.ok(inactivityWarning(null).text.startsWith("Bonjour,\n"));
});

const ringRow: OutboxRow = {
  id: 9,
  user_id: "u2",
  kind: "call_ring",
  template: null,
  payload: { call_id: "11111111-1111-1111-1111-111111111111", conversation_id: "c1", caller_id: "u1", caller_name: "Martin" },
  quiet: true,
  time_zone: "Asia/Jakarta",
  devices: [
    { token: "android-1", platform: "android", kind: "alert", activity: null, sandbox: false },
    { token: "ios-alert", platform: "ios", kind: "alert", activity: null, sandbox: false },
    { token: "ios-voip", platform: "ios", kind: "voip", activity: null, sandbox: true },
  ],
};

Deno.test("un appel sonne par VoIP sur iPhone et par FCM prioritaire sur Android, même la nuit", () => {
  const out = deliveries(ringRow, new Date("2026-11-21T12:00:00Z"), "app.kiko");
  assert.equal(out.length, 2);
  const android = out.find((d) => d.fcm)!;
  assert.equal(android.fcm!.android.priority, "HIGH");
  assert.equal(android.fcm!.android.ttl, "45s");
  assert.equal(android.fcm!.data.caller_name, "Martin");
  const ios = out.find((d) => d.apns)!;
  assert.equal(ios.device.token, "ios-voip");
  assert.equal(ios.apns!.headers["apns-push-type"], "voip");
  assert.equal(ios.apns!.headers["apns-topic"], "app.kiko.voip");
  assert.equal(ios.apns!.body.kiko.call_id, ringRow.payload.call_id);
});

Deno.test("sans jeton VoIP, l'iPhone reçoit une alerte d'appel ; un appel annulé ne part qu'en VoIP", () => {
  const noVoip = { ...ringRow, devices: ringRow.devices.filter((d) => d.kind !== "voip") };
  const alert = deliveries(noVoip, new Date(), "app.kiko").find((d) => d.apns)!;
  assert.equal(alert.apns!.headers["apns-push-type"], "alert");
  assert.equal(alert.apns!.body.aps["interruption-level"], "time-sensitive");
  const cancel = deliveries({ ...noVoip, kind: "call_cancel" }, new Date(), "app.kiko");
  assert.deepEqual(cancel.map((d) => d.device.token), ["android-1"]);
  assert.equal(cancel[0].fcm!.data.caller_name, undefined);
});

Deno.test("le jeton LiveKit ne donne que le micro, dans une seule salle, pour 10 minutes", async () => {
  const now = new Date("2026-11-21T12:00:00Z");
  const c = claims({ apiKey: "APIkey", apiSecret: "secret", identity: "u1", room: "call-1", now });
  // deno-lint-ignore no-explicit-any
  const video = c.video as any;
  assert.equal(video.room, "call-1");
  assert.deepEqual(video.canPublishSources, ["microphone"]);
  assert.equal(video.canPublishData, false);
  assert.equal((c.exp as number) - Math.floor(now.getTime() / 1000), 600);
  const token = await livekitToken({ apiKey: "APIkey", apiSecret: "secret", identity: "u1", room: "call-1", now });
  const [header, payload, signature] = token.split(".");
  assert.ok(signature.length > 0);
  assert.equal(JSON.parse(atob(header.replace(/-/g, "+").replace(/_/g, "/"))).alg, "HS256");
  const body = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - payload.length % 4) % 4)));
  assert.equal(body.sub, "u1");
  assert.ok(isUuid("11111111-1111-1111-1111-111111111111") && !isUuid("call-1; drop table"));
});
