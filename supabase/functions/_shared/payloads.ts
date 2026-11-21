// Ce que reçoit chaque téléphone pour une ligne de la file d'envoi.
//
// Android : un message FCM « data » seulement. L'app construit elle-même la notification
// (MessagingStyle, réponse rapide, mise à jour en direct d'Android 16), même app fermée.
//
// iPhone : une alerte APNs, que l'extension de notification enrichit (photo de l'expéditeur),
// ou un push d'activité en direct (appel qui approche, série en danger) quand iOS 17.2+ a donné
// un jeton « push-to-start ».

import { cityOf, render } from "./copy.ts";

export interface Device {
  token: string;
  platform: "android" | "ios";
  kind: "alert" | "activity_start" | "activity_update";
  activity: string | null;
  sandbox: boolean;
}

export interface OutboxRow {
  id: number;
  user_id: string;
  kind: string;
  template: string | null;
  // deno-lint-ignore no-explicit-any
  payload: Record<string, any>;
  quiet: boolean;
  time_zone: string | null;
  devices: Device[];
}

export interface FcmMessage {
  token: string;
  data: Record<string, string>;
  android: { priority: "HIGH" | "NORMAL"; ttl: string; collapse_key?: string };
}

export interface ApnsRequest {
  token: string;
  sandbox: boolean;
  headers: Record<string, string>;
  // deno-lint-ignore no-explicit-any
  body: Record<string, any>;
}

export interface Delivery {
  device: Device;
  fcm?: FcmMessage;
  apns?: ApnsRequest;
  /** Le jeton ne sert qu'une fois (fin d'une activité en direct) : on l'oublie après l'envoi. */
  dropAfter?: boolean;
}

const urgent = new Set(["message", "request", "correction", "call", "call_accepted", "call_soon", "streak_risk", "milestone"]);
const ending = new Set(["activity_end", "call_end"]);

const category: Record<string, string> = {
  message: "MESSAGE",
  request: "REQUEST",
  correction: "CORRECTION",
  call: "CALL",
  call_accepted: "CALL",
  call_soon: "CALL_SOON",
  milestone: "MILESTONE",
  streak_risk: "STREAK",
};

export function deliveries(row: OutboxRow, now: Date, bundleId: string): Delivery[] {
  const tz = row.time_zone ?? "UTC";
  const out: Delivery[] = [];
  const seconds = Math.floor(now.getTime() / 1000);

  if (ending.has(row.kind)) {
    for (const d of row.devices) {
      if (d.platform !== "ios" || d.kind !== "activity_update" || d.activity !== row.payload.activity) continue;
      out.push({
        device: d,
        dropAfter: true,
        apns: {
          token: d.token,
          sandbox: d.sandbox,
          headers: liveHeaders(bundleId, 10),
          body: { aps: { timestamp: seconds, event: "end", "dismissal-date": seconds + (row.kind === "call_end" ? 0 : 60) } },
        },
      });
    }
    return out;
  }

  const text = render(row.kind, row.payload, tz, now);
  if (!text) return out;

  // Android
  const ttl = ttlSeconds(row, now);
  for (const d of row.devices) {
    if (d.platform !== "android" || d.kind !== "alert") continue;
    const data: Record<string, string> = {
      type: row.kind,
      outbox_id: String(row.id),
      title: text.title,
      body: text.body,
      quiet: String(row.quiet),
      time_zone: tz,
    };
    for (const [k, v] of Object.entries(row.payload)) {
      if (v == null || k === "title" || k === "body") continue;
      data[k] = typeof v === "string" ? v : JSON.stringify(v);
    }
    out.push({
      device: d,
      fcm: {
        token: d.token,
        data,
        android: {
          priority: urgent.has(row.kind) && !row.quiet ? "HIGH" : "NORMAL",
          ttl: `${ttl}s`,
          ...(row.template ? { collapse_key: "reminder" } : {}),
        },
      },
    });
  }

  // iPhone : activité en direct si possible, sinon alerte.
  const live = liveActivity(row, now, text);
  const starters = live ? row.devices.filter((d) => d.platform === "ios" && d.kind === "activity_start" && d.activity === live.type) : [];
  if (live && starters.length > 0) {
    for (const d of starters) {
      out.push({
        device: d,
        apns: { token: d.token, sandbox: d.sandbox, headers: liveHeaders(bundleId, 10), body: live.body },
      });
    }
    return out;
  }

  const { title: _t, body: _b, ...extra } = row.payload;
  const threadId = row.payload.conversation_id ?? row.kind;
  for (const d of row.devices) {
    if (d.platform !== "ios" || d.kind !== "alert") continue;
    const headers: Record<string, string> = {
      "apns-push-type": "alert",
      "apns-topic": bundleId,
      "apns-priority": urgent.has(row.kind) && !row.quiet ? "10" : "5",
      "apns-expiration": String(seconds + ttl),
    };
    if (row.template) headers["apns-collapse-id"] = "reminder";
    if (row.kind === "call_soon") headers["apns-collapse-id"] = `call-${row.payload.call_id}`;
    out.push({
      device: d,
      apns: {
        token: d.token,
        sandbox: d.sandbox,
        headers,
        body: {
          aps: {
            alert: { title: text.title, body: text.body },
            ...(row.quiet ? {} : { sound: "default" }),
            "thread-id": String(threadId),
            category: category[row.kind] ?? "REMINDER",
            "mutable-content": 1,
            "interruption-level": row.quiet ? "passive" : row.kind === "call_soon" ? "time-sensitive" : "active",
            "relevance-score": row.template ? 0.3 : 0.8,
          },
          kiko: { type: row.kind, outbox_id: row.id, ...extra },
        },
      },
    });
  }
  return out;
}

function ttlSeconds(row: OutboxRow, now: Date): number {
  if (row.kind === "call_soon" && row.payload.at) {
    return Math.max(60, Math.round((Date.parse(row.payload.at) - now.getTime()) / 1000) + 600);
  }
  if (row.template) return 4 * 3600;
  return 24 * 3600;
}

function liveHeaders(bundleId: string, priority: 5 | 10): Record<string, string> {
  return {
    "apns-push-type": "liveactivity",
    "apns-topic": `${bundleId}.push-type.liveactivity`,
    "apns-priority": String(priority),
  };
}

/**
 * Les deux activités en direct des maquettes 25 et 32 à 36. Les noms des champs suivent les
 * structures Swift de iosApp/Shared/KikoActivities.swift (dates en secondes depuis 1970).
 */
function liveActivity(
  row: OutboxRow,
  now: Date,
  text: { title: string; body: string },
  // deno-lint-ignore no-explicit-any
): { type: string; body: Record<string, any> } | null {
  const seconds = Math.floor(now.getTime() / 1000);
  const alert = { title: text.title, body: text.body, sound: "default" };
  if (row.kind === "call_soon" && row.payload.at) {
    const startsAt = Date.parse(row.payload.at) / 1000;
    const minutes = Number(row.payload.minutes ?? 15);
    return {
      type: "call",
      body: {
        aps: {
          timestamp: seconds,
          event: "start",
          "attributes-type": "CallAttributes",
          attributes: {
            callId: String(row.payload.call_id),
            conversationId: String(row.payload.conversation_id),
            partnerName: String(row.payload.partner_name ?? ""),
            partnerCity: cityOf(row.payload.partner_time_zone),
            partnerTimeZone: String(row.payload.partner_time_zone ?? "UTC"),
          },
          "content-state": { startsAtEpoch: startsAt, minutes, joined: false },
          "stale-date": Math.round(startsAt + minutes * 60),
          "relevance-score": 100,
          alert,
        },
      },
    };
  }
  if (row.kind === "streak_risk" && row.payload.deadline) {
    return {
      type: "streak",
      body: {
        aps: {
          timestamp: seconds,
          event: "start",
          "attributes-type": "StreakAttributes",
          attributes: {
            conversationId: String(row.payload.conversation_id),
            partnerName: String(row.payload.partner_name ?? ""),
          },
          "content-state": { deadlineEpoch: Date.parse(row.payload.deadline) / 1000, streak: Number(row.payload.streak ?? 0) },
          "stale-date": Math.round(Date.parse(row.payload.deadline) / 1000),
          "relevance-score": 50,
          alert,
        },
      },
    };
  }
  return null;
}
