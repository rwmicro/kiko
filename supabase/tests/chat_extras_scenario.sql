-- Scénario de test de la suite des Discussions (signaler, séries, corrections, fonds, fichiers,
-- « écrit… »), à lancer sur une base locale chargée avec les migrations. Ne pas lancer sur le projet Supabase.
\set ON_ERROR_STOP 1
insert into auth.users values
 ('00000000-0000-0000-0000-00000000000a'), ('00000000-0000-0000-0000-00000000000b'), ('00000000-0000-0000-0000-00000000000c');
insert into public.profiles (id, first_name, birth_date, time_zone) values
 ('00000000-0000-0000-0000-00000000000a', 'Martin', '1999-03-14', 'Europe/Paris'),
 ('00000000-0000-0000-0000-00000000000b', 'Ayu', '2001-07-09', 'Asia/Jakarta'),
 ('00000000-0000-0000-0000-00000000000c', 'Budi', '1995-01-01', 'Asia/Jakarta');
insert into public.user_languages values
 ('00000000-0000-0000-0000-00000000000a', 'fr', true, null), ('00000000-0000-0000-0000-00000000000a', 'id', false, 'A2'),
 ('00000000-0000-0000-0000-00000000000b', 'id', true, null), ('00000000-0000-0000-0000-00000000000b', 'fr', false, 'B1'),
 ('00000000-0000-0000-0000-00000000000c', 'id', true, null), ('00000000-0000-0000-0000-00000000000c', 'fr', false, 'A1');

-- Helper : l'instruction doit être refusée.
create function pg_temp.refused(sql text) returns text language plpgsql as $$
begin
    execute sql;
    raise exception 'NOT REFUSED: %', sql;
exception when others then
    if sqlerrm like 'NOT REFUSED%' then raise; end if;
    return 'refusé : ' || sqlerrm;
end $$;

set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select public.send_greeting('00000000-0000-0000-0000-00000000000b', 'Halo Ayu!') as conv_ayu \gset
select public.send_greeting('00000000-0000-0000-0000-00000000000c', 'Halo Budi!') as conv_budi \gset

\echo 'Avant acceptation : pas de fond, pas de fichier, pas de canal'
select pg_temp.refused(format('select public.set_conversation_background(%L, %L)', :'conv_ayu', 'beach'));
select pg_temp.refused(format('insert into storage.objects (bucket_id, name) values (%L, %L)', 'chat-media', :'conv_ayu' || '/a.jpg'));
select set_config('realtime.topic', 'chat:' || :'conv_ayu', false) is not null as canal;
select pg_temp.refused($$insert into realtime.messages (topic, extension) values (realtime.topic(), 'broadcast')$$);

set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select public.respond_to_request(:'conv_ayu', true);
select id as greeting from public.messages where conversation_id = :'conv_ayu' \gset

\echo 'Ayu corrige le salut de Martin'
insert into public.messages (id, conversation_id, sender_id, kind, body, reply_to_id) values
 (gen_random_uuid(), :'conv_ayu', auth.uid(), 'correction', '{"original":"Halo Ayu!","corrected":"Halo, Ayu!"}', :'greeting');
\echo 'Une correction sans message corrigé, ou de son propre message (attendu : refusé)'
select pg_temp.refused(format($$insert into public.messages (id, conversation_id, sender_id, kind, body) values (gen_random_uuid(), %L, auth.uid(), 'correction', '{}')$$, :'conv_ayu'));
insert into public.messages (id, conversation_id, sender_id, body) values ('00000000-0000-0000-0000-0000000000f1', :'conv_ayu', auth.uid(), 'Bonjour !');
select pg_temp.refused(format($$insert into public.messages (id, conversation_id, sender_id, kind, body, reply_to_id) values (gen_random_uuid(), %L, auth.uid(), 'correction', '{}', '00000000-0000-0000-0000-0000000000f1')$$, :'conv_ayu'));
\echo 'Un message antidaté (attendu : refusé, le serveur date les messages)'
select pg_temp.refused(format($$insert into public.messages (id, conversation_id, sender_id, body, created_at) values (gen_random_uuid(), %L, auth.uid(), 'x', now() - interval '3 days')$$, :'conv_ayu'));

\echo 'Ayu envoie une photo rangée dans le dossier de la conversation'
insert into storage.objects (bucket_id, name) values ('chat-media', :'conv_ayu' || '/p1.jpg');
insert into public.messages (id, conversation_id, sender_id, kind, media_path) values
 (gen_random_uuid(), :'conv_ayu', auth.uid(), 'photo', :'conv_ayu' || '/p1.jpg');
\echo 'Un fichier hors du dossier, ou une photo qui pointe ailleurs (attendu : refusé)'
select pg_temp.refused(format('insert into storage.objects (bucket_id, name) values (%L, %L)', 'chat-media', :'conv_budi' || '/x.jpg'));
select pg_temp.refused(format($$insert into public.messages (id, conversation_id, sender_id, kind, media_path) values (gen_random_uuid(), %L, auth.uid(), 'photo', %L)$$, :'conv_ayu', :'conv_budi' || '/x.jpg'));
select pg_temp.refused(format($$insert into public.messages (id, conversation_id, sender_id, kind, body) values (gen_random_uuid(), %L, auth.uid(), 'voice', 'x')$$, :'conv_ayu'));

\echo 'Ayu choisit la plage, et peut signaler « écrit… » dans le canal'
select public.set_conversation_background(:'conv_ayu', 'beach');
select pg_temp.refused(format('select public.set_conversation_background(%L, %L)', :'conv_ayu', 'disco'));
insert into realtime.messages (topic, extension) values (realtime.topic(), 'broadcast');
select count(*) as signaux_vus from realtime.messages;

set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000c';
\echo 'Budi ne voit ni les fichiers, ni le canal de Martin et Ayu'
select count(*) as fichiers_vus_par_budi from storage.objects;
select count(*) as signaux_vus_par_budi from realtime.messages;
select pg_temp.refused($$insert into realtime.messages (topic, extension) values (realtime.topic(), 'broadcast')$$);

set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
\echo 'Martin voit la photo, le fond choisi par Ayu, et une série d''un jour'
select count(*) as fichiers_vus_par_martin from storage.objects;
select partner_first_name, background, streak, last_message_kind from public.my_conversations() order by partner_first_name;

\echo 'Série : hier et avant-hier à deux, puis un trou (attendu : 2)'
reset role;
insert into public.messages (id, conversation_id, sender_id, body, created_at)
select gen_random_uuid(), :'conv_ayu', s, 'x', now() - (d || ' days')::interval
from unnest(array[1, 2, 5]) d,
     unnest(array['00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b']::uuid[]) s;
delete from public.messages where conversation_id = :'conv_ayu' and created_at > now() - interval '1 hour';
set role authenticated;
select public.conversation_streak(:'conv_ayu') as serie_jusqu_a_hier;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000c';
select public.conversation_streak(:'conv_ayu') is null as budi_ne_voit_pas_la_serie;

\echo 'Signaler'
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select public.report_user('00000000-0000-0000-0000-00000000000c', 'spam', '  ', :'conv_budi');
select pg_temp.refused($$select public.report_user('00000000-0000-0000-0000-00000000000a', 'spam')$$);
select pg_temp.refused(format($$select public.report_user('00000000-0000-0000-0000-00000000000c', 'spam', null, %L)$$, :'conv_ayu'));
select pg_temp.refused($$select public.report_user('00000000-0000-0000-0000-00000000000c', 'boring')$$);
select pg_temp.refused('select count(*) from public.reports');
reset role;
select reporter_id, reported_id, reason, details is null as sans_details from public.reports;

set role anon;
\echo 'anon (attendu : refusé)'
select pg_temp.refused($$select public.report_user('00000000-0000-0000-0000-00000000000c', 'spam')$$);
select pg_temp.refused(format('select public.conversation_streak(%L)', :'conv_ayu'));
