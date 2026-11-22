-- Scénario de test des notifications (appareils, préférences, file d'envoi, relances, appels),
-- à lancer sur une base locale chargée avec les migrations. Ne pas lancer sur le projet Supabase.
\set ON_ERROR_STOP 1
insert into auth.users values
 ('00000000-0000-0000-0000-00000000000a'), ('00000000-0000-0000-0000-00000000000b'), ('00000000-0000-0000-0000-00000000000c');
insert into public.profiles (id, first_name, birth_date, time_zone, last_seen_at) values
 ('00000000-0000-0000-0000-00000000000a', 'Martin', '1999-03-14', 'Europe/Paris', now() - interval '2 days'),
 ('00000000-0000-0000-0000-00000000000b', 'Ayu', '2001-07-09', 'Asia/Jakarta', now()),
 ('00000000-0000-0000-0000-00000000000c', 'Budi', '1995-01-01', 'Pas/UnFuseau', now());
insert into public.user_languages values
 ('00000000-0000-0000-0000-00000000000a', 'fr', true, null), ('00000000-0000-0000-0000-00000000000a', 'id', false, 'A2'),
 ('00000000-0000-0000-0000-00000000000b', 'id', true, null), ('00000000-0000-0000-0000-00000000000b', 'fr', false, 'B1'),
 ('00000000-0000-0000-0000-00000000000c', 'id', true, null), ('00000000-0000-0000-0000-00000000000c', 'fr', false, 'A1');

create function pg_temp.refused(sql text) returns text language plpgsql as $$
begin
    execute sql;
    raise exception 'NOT REFUSED: %', sql;
exception when others then
    if sqlerrm like 'NOT REFUSED%' then raise; end if;
    return 'refusé : ' || sqlerrm;
end $$;

create function pg_temp.check(ok boolean, what text) returns text language plpgsql as $$
begin
    if not coalesce(ok, false) then raise exception 'ÉCHEC : %', what; end if;
    return 'ok : ' || what;
end $$;

\echo 'Outils : fuseau inconnu, nuit, JSON mal formé'
select pg_temp.check(public.safe_time_zone('Pas/UnFuseau') = 'UTC' and public.safe_time_zone('Asia/Jakarta') = 'Asia/Jakarta', 'fuseau inconnu → UTC');
select pg_temp.check(public.is_quiet_for('Europe/Paris', '2026-11-20 22:30+00') and not public.is_quiet_for('Europe/Paris', '2026-11-20 12:00+00'), '22 h - 8 h = nuit');
select pg_temp.check(public.safe_jsonb('{pas du json') is null, 'JSON mal formé → null');

set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
\echo 'Martin enregistre son téléphone, mais ne peut pas lire la table'
select public.register_push_device('appareil-android-martin', 'android');
select public.register_push_device('appareil-ios-martin-start', 'ios', 'activity_start', 'call', true);
select pg_temp.refused('select * from public.push_devices');
select pg_temp.refused('select * from public.push_outbox');
select pg_temp.refused($$select public.register_push_device('x', 'android')$$);
select pg_temp.refused($$select public.register_push_device('appareil-windows-martin', 'windows')$$);
\echo 'Les fonctions du serveur sont fermées à l''app'
select pg_temp.refused('select * from public.claim_push_batch(10)');
select pg_temp.refused('select * from public.reminder_candidates()');
select pg_temp.refused($$select public.check_push_secret('x')$$);
select pg_temp.refused('select public.call_edge_function(''push'')');
select pg_temp.refused('select public.streak_days(gen_random_uuid())');

select public.send_greeting('00000000-0000-0000-0000-00000000000b', 'Halo Ayu!') as conv_ayu \gset
select public.send_greeting('00000000-0000-0000-0000-00000000000c', 'Halo Budi!') as conv_budi \gset

set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select public.register_push_device('appareil-android-ayu', 'android');
select public.respond_to_request(:'conv_ayu', true);
select id as greeting from public.messages where conversation_id = :'conv_ayu' \gset
insert into public.messages (id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv_ayu', auth.uid(), 'Halo Martin!');
insert into public.messages (id, conversation_id, sender_id, kind, body, reply_to_id) values
 (gen_random_uuid(), :'conv_ayu', auth.uid(), 'correction', '{"original":"Halo Ayu!","corrected":"Halo, Ayu!","note":"virgule"}', :'greeting');
insert into public.messages (id, conversation_id, sender_id, kind, body) values
 (gen_random_uuid(), :'conv_ayu', auth.uid(), 'call_proposal', '{pas du json');

reset role;
\echo 'File : le salut va à Ayu (demande), puis message, correction et appel vont à Martin'
select kind, payload ->> 'sender_name' as de, payload ->> 'preview' as apercu, (select first_name from public.profiles where id = user_id) as pour
from public.push_outbox order by id;
select pg_temp.check((select count(*) from public.push_outbox where kind = 'request' and user_id = '00000000-0000-0000-0000-00000000000b') = 1, 'demande notifiée à Ayu');
select pg_temp.check((select count(*) from public.push_outbox where kind = 'request' and user_id = '00000000-0000-0000-0000-00000000000c') = 1, 'demande notifiée à Budi');
select pg_temp.check((select payload ->> 'corrected' from public.push_outbox where kind = 'correction') = 'Halo, Ayu!', 'correction détaillée');
select pg_temp.check((select payload ->> 'preview' from public.push_outbox where kind = 'call') like '📞%', 'appel malformé : aperçu générique');
select pg_temp.check(not exists (select 1 from public.push_outbox where user_id = '00000000-0000-0000-0000-00000000000b' and kind <> 'request'), 'pas de notification pour soi-même');

\echo 'Martin coupe les messages : plus rien. Puis il les remet.'
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select public.set_notification_prefs(false, true);
select * from public.my_notification_prefs();
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
insert into public.messages (id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv_ayu', auth.uid(), 'Tu dors ?');
reset role;
select pg_temp.check(not exists (select 1 from public.push_outbox where payload ->> 'preview' = 'Tu dors ?'), 'messages coupés : rien en file');
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select public.set_notification_prefs(true, true);

\echo 'Un appel accepté dans 10 minutes : appels à venir et alerte « bientôt »'
select id as proposal from public.messages where kind = 'call_proposal' limit 1 \gset
insert into public.messages (id, conversation_id, sender_id, kind, body, reply_to_id) values
 ('00000000-0000-0000-0000-0000000000ca', :'conv_ayu', auth.uid(), 'call_proposal',
  json_build_object('accepted_at', now() + interval '10 minutes', 'minutes', 20)::text, :'proposal');
select partner_first_name, minutes, starts_at > now() as futur from public.my_upcoming_calls();
select pg_temp.check((select count(*) from public.my_upcoming_calls()) = 1, 'appel à venir visible');
reset role;
select pg_temp.check((select kind from public.push_outbox where payload ->> 'message_id' = '00000000-0000-0000-0000-0000000000ca') = 'call_accepted', 'acceptation notifiée à Ayu');
select pg_temp.refused('select public.push_opened(1)');
set role service_role;
select pg_temp.check((select count(*) from public.due_call_alerts() where kind = 'call_soon') = 2, 'alerte « bientôt » pour les deux');
select pg_temp.check((select count(*) from public.due_call_alerts(now() + interval '40 minutes') where kind = 'call_end') = 0, 'pas de fin sans activité en direct');
reset role;

\echo 'Série de 7 jours : la fête pour les deux, une seule fois'
insert into public.messages (id, conversation_id, sender_id, body, created_at)
select gen_random_uuid(), :'conv_ayu', s, 'jour ' || d, now() - make_interval(days => d)
from generate_series(1, 6) d, unnest(array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[]) s;
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
insert into public.messages (id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv_ayu', auth.uid(), 'Encore moi');
insert into public.messages (id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv_ayu', auth.uid(), 'Et encore');
reset role;
select pg_temp.check((select count(*) from public.push_outbox where kind = 'milestone') = 2, 'étape 7 jours notifiée aux deux');
select pg_temp.check((select payload ->> 'partner_name' from public.push_outbox where kind = 'milestone' and user_id = '00000000-0000-0000-0000-00000000000a') = 'Ayu', 'Martin fête avec Ayu');

\echo 'Activité « série en danger » : elle se ferme quand Martin écrit'
insert into public.push_devices (token, user_id, platform, kind, activity)
values ('appareil-ios-martin-serie', '00000000-0000-0000-0000-00000000000a', 'ios', 'activity_update', 'streak:' || :'conv_ayu');
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
insert into public.messages (id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv_ayu', auth.uid(), 'Sauvée');
reset role;
select pg_temp.check((select count(*) from public.push_outbox where kind = 'activity_end') = 1, 'fin d''activité en file');

\echo 'Relances : série en danger à 20 h 30 chez Martin, quand Ayu a écrit aujourd''hui'
delete from public.messages where conversation_id = :'conv_ayu' and sender_id = '00000000-0000-0000-0000-00000000000a'
  and (created_at at time zone 'UTC')::date = (now() at time zone 'UTC')::date;
set role service_role;
select kind, partner_name, streak, minutes_left, days_inactive
from public.reminder_candidates(date_trunc('day', now()) + interval '19 hours 30 minutes');
select pg_temp.check((select kind from public.reminder_candidates(date_trunc('day', now()) + interval '19 hours 30 minutes')
                      where user_id = '00000000-0000-0000-0000-00000000000a') = 'streak_risk', 'série en danger pour Martin');
reset role;
begin;
delete from public.messages where conversation_id = :'conv_ayu' and (created_at at time zone 'UTC')::date = (now() at time zone 'UTC')::date;
set local role service_role;
select pg_temp.check((select kind from public.reminder_candidates(date_trunc('day', now()) + interval '18 hours 10 minutes')
                      where user_id = '00000000-0000-0000-0000-00000000000a') = 'reengage', 'sans série en jeu, à 19 h 10 : relance après 2 jours d''absence');
select pg_temp.check((select partner_name from public.reminder_candidates(date_trunc('day', now()) + interval '18 hours 10 minutes')
                      where user_id = '00000000-0000-0000-0000-00000000000a') = 'Ayu', 'la relance parle d''Ayu');
select pg_temp.check(not exists (select 1 from public.reminder_candidates(date_trunc('day', now()) + interval '17 hours 10 minutes')
                      where user_id = '00000000-0000-0000-0000-00000000000a'), 'pas de relance d''absence à 18 h');
rollback;
set role service_role;
select pg_temp.check(not exists (select 1 from public.reminder_candidates(date_trunc('day', now()) + interval '22 hours 30 minutes')
                      where user_id = '00000000-0000-0000-0000-00000000000a'), 'rien à 23 h 30 chez Martin');
select pg_temp.check(not exists (select 1 from public.reminder_candidates(date_trunc('day', now()) + interval '19 hours 30 minutes')
                      where user_id = '00000000-0000-0000-0000-00000000000c'), 'pas de relance sans téléphone enregistré');
reset role;
insert into public.push_outbox (user_id, kind, template, dedupe_key)
values ('00000000-0000-0000-0000-00000000000a', 'reengage', 'reengage_d2_a', 'test-cap');
set role service_role;
select pg_temp.check(not exists (select 1 from public.reminder_candidates() where user_id = '00000000-0000-0000-0000-00000000000a'), 'une relance par jour au plus');
select pg_temp.check((select template from public.reminder_history(array['00000000-0000-0000-0000-00000000000a']::uuid[])) = 'reengage_d2_a', 'historique des variantes');

\echo 'Envoi : un lot pris une fois, puis fini'
create temp table batch as select * from public.claim_push_batch(500);
select pg_temp.check((select count(*) from batch) > 0, 'lot pris');
select pg_temp.check((select bool_and(jsonb_array_length(devices) = 3) from batch where user_id = '00000000-0000-0000-0000-00000000000a'), 'jetons de Martin joints');
select pg_temp.check((select count(*) from public.claim_push_batch(500)) = 0, 'pas pris deux fois');
select public.finish_push(id) from public.push_outbox;
select pg_temp.check(not exists (select 1 from public.push_outbox where payload <> '{}'::jsonb), 'contenu effacé après l''envoi');
select public.drop_push_token('appareil-android-ayu');
reset role;
select pg_temp.check(not exists (select 1 from public.push_outbox where sent_at is null), 'tout est fini');
select pg_temp.check(not exists (select 1 from public.push_devices where token = 'appareil-android-ayu'), 'jeton invalide supprimé');
\echo 'Ménage : file de plus de 30 jours, jetons inactifs depuis 90 jours'
update public.push_outbox set created_at = now() - interval '31 days' where id = 1;
update public.push_devices set updated_at = now() - interval '91 days' where token = 'appareil-ios-martin-start';
select public.purge_push_data();
select pg_temp.check(not exists (select 1 from public.push_outbox where id = 1), 'vieille ligne supprimée');
select pg_temp.check(not exists (select 1 from public.push_devices where token = 'appareil-ios-martin-start'), 'jeton inactif supprimé');
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select pg_temp.refused('select public.purge_push_data()');
reset role;
\echo 'Tout est bon.'
