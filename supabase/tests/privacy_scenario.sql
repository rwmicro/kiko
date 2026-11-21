-- Scénario de test « garder le moins possible » : inscription, suppression de compte, ménage.
-- À lancer sur une base locale chargée avec les migrations. Ne pas lancer sur le projet Supabase.
\set ON_ERROR_STOP 1

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

insert into auth.users (id, raw_user_meta_data, email_confirmed_at, created_at) values
 ('00000000-0000-0000-0000-00000000000a', '{"first_name":"Martin","birth_date":"1999-03-14","locale":"fr"}', now(), now()),
 ('00000000-0000-0000-0000-00000000000b', '{"first_name":"Ayu","birth_date":"2001-07-09"}', now(), now()),
 ('00000000-0000-0000-0000-00000000000c', '{}', now(), now()),
 -- Inscriptions abandonnées
 ('00000000-0000-0000-0000-0000000000d1', '{"first_name":"Jamais"}', null, now() - interval '8 days'),
 ('00000000-0000-0000-0000-0000000000d2', '{"first_name":"Récent"}', null, now() - interval '2 days'),
 ('00000000-0000-0000-0000-0000000000d3', '{"first_name":"Confirmé"}', now(), now() - interval '31 days');

\echo 'Le profil créé, le compte oublie le prénom et la date de naissance'
insert into public.profiles (id, first_name, birth_date, time_zone) values
 ('00000000-0000-0000-0000-00000000000a', 'Martin', '1999-03-14', 'Europe/Paris'),
 ('00000000-0000-0000-0000-00000000000b', 'Ayu', '2001-07-09', 'Asia/Jakarta'),
 ('00000000-0000-0000-0000-00000000000c', 'Budi', '1995-01-01', 'Asia/Jakarta');
select pg_temp.check((select raw_user_meta_data from auth.users where id = '00000000-0000-0000-0000-00000000000a') = '{"locale":"fr"}', 'prénom et date effacés du compte');
insert into public.user_languages values
 ('00000000-0000-0000-0000-00000000000a', 'fr', true, null), ('00000000-0000-0000-0000-00000000000a', 'id', false, 'A2'),
 ('00000000-0000-0000-0000-00000000000b', 'id', true, null), ('00000000-0000-0000-0000-00000000000b', 'fr', false, 'B1'),
 ('00000000-0000-0000-0000-00000000000c', 'id', true, null), ('00000000-0000-0000-0000-00000000000c', 'fr', false, 'A1');

set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select public.send_greeting('00000000-0000-0000-0000-00000000000b', 'Halo Ayu!') as conv_ayu \gset
select public.send_greeting('00000000-0000-0000-0000-00000000000c', 'Halo Budi!') as conv_budi \gset
select public.report_user('00000000-0000-0000-0000-00000000000c', 'spam');
\echo 'Les fonctions de suppression sont fermées à l''app'
select pg_temp.refused($$select * from public.account_conversations('00000000-0000-0000-0000-00000000000b')$$);
select pg_temp.refused($$select public.forget_account_data('00000000-0000-0000-0000-00000000000b')$$);
select pg_temp.refused('select * from public.inactive_accounts()');
select pg_temp.refused('select public.purge_old_data()');
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select public.respond_to_request(:'conv_ayu', true);
reset role;

\echo 'Martin supprime son compte (ce que fait la fonction Edge, une fois les fichiers effacés)'
set role service_role;
select pg_temp.check((select count(*) from public.account_conversations('00000000-0000-0000-0000-00000000000a')) = 2, 'ses 2 discussions');
select public.forget_account_data('00000000-0000-0000-0000-00000000000a');
reset role;
delete from auth.users where id = '00000000-0000-0000-0000-00000000000a';
select pg_temp.check(not exists (select 1 from public.profiles where id = '00000000-0000-0000-0000-00000000000a'), 'profil supprimé');
select pg_temp.check(not exists (select 1 from public.user_languages where user_id = '00000000-0000-0000-0000-00000000000a'), 'langues supprimées');
select pg_temp.check(not exists (select 1 from public.conversations where id in (:'conv_ayu', :'conv_budi')), 'discussions supprimées');
select pg_temp.check(not exists (select 1 from public.messages where conversation_id in (:'conv_ayu', :'conv_budi')), 'messages supprimés');
select pg_temp.check((select reporter_id is null from public.reports where reported_id = '00000000-0000-0000-0000-00000000000c'), 'signalement gardé, sans son auteur');
select pg_temp.check(exists (select 1 from public.profiles where id = '00000000-0000-0000-0000-00000000000b'), 'Ayu garde son compte');

\echo 'Ménage de nuit'
update public.profiles set last_seen_at = now() - interval '25 months' where id = '00000000-0000-0000-0000-00000000000c';
insert into public.reports (reporter_id, reported_id, reason, status, created_at) values
 ('00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000c', 'fake', 'reviewed', now() - interval '13 months'),
 ('00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000c', 'fake', 'open', now() - interval '13 months');
set role service_role;
select pg_temp.check((select array_agg(i) from public.inactive_accounts() i) = array['00000000-0000-0000-0000-00000000000c'::uuid], 'Budi, inactif depuis 2 ans');
reset role;
select public.purge_old_data();
select pg_temp.check(not exists (select 1 from public.reports where status = 'reviewed'), 'signalement traité depuis 1 an supprimé');
select pg_temp.check(exists (select 1 from public.reports where status = 'open'), 'signalement ouvert gardé');
select pg_temp.check(not exists (select 1 from auth.users where id = '00000000-0000-0000-0000-0000000000d1'), 'inscription jamais confirmée (8 jours) supprimée');
select pg_temp.check(exists (select 1 from auth.users where id = '00000000-0000-0000-0000-0000000000d2'), 'inscription récente gardée');
select pg_temp.check(not exists (select 1 from auth.users where id = '00000000-0000-0000-0000-0000000000d3'), 'inscription sans profil (31 jours) supprimée');
select pg_temp.check(exists (select 1 from auth.users where id = '00000000-0000-0000-0000-00000000000b'), 'compte actif gardé');
\echo 'Tout est bon.'
