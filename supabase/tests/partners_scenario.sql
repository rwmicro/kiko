-- Scénario de test des fonctions Partenaires, à lancer sur une base locale chargée avec les migrations
-- (rôles anon/authenticated et auth.uid() simulés). Ne pas lancer sur le projet Supabase.
\set ON_ERROR_STOP 1
-- Personnes : Martin (fr natif, apprend id), Ayu (id natif, apprend fr), Budi (id, apprend fr, a bloqué Martin),
-- Sari (id, apprend en : pas compatible), Rina (id natif, apprend fr, ville cachée).
insert into auth.users values
 ('00000000-0000-0000-0000-00000000000a'), ('00000000-0000-0000-0000-00000000000b'),
 ('00000000-0000-0000-0000-00000000000c'), ('00000000-0000-0000-0000-00000000000d'),
 ('00000000-0000-0000-0000-00000000000e');
insert into public.profiles (id, first_name, birth_date, city, show_city, time_zone, interests) values
 ('00000000-0000-0000-0000-00000000000a', 'Martin', '1999-03-14', 'Lyon', true, 'Europe/Paris', '{Cinéma,Cuisine,Voyage}'),
 ('00000000-0000-0000-0000-00000000000b', 'Ayu', '2001-07-09', 'Bandung', true, 'Asia/Jakarta', '{Cinéma,Cuisine,Art}'),
 ('00000000-0000-0000-0000-00000000000c', 'Budi', '1995-01-01', 'Jakarta', true, 'Asia/Jakarta', '{Foot}'),
 ('00000000-0000-0000-0000-00000000000d', 'Sari', '1990-01-01', 'Bali', true, 'Asia/Makassar', '{Yoga}'),
 ('00000000-0000-0000-0000-00000000000e', 'Rina', '1998-05-05', 'Surabaya', false, 'Asia/Jakarta', '{Voyage}');
insert into public.user_languages values
 ('00000000-0000-0000-0000-00000000000a', 'fr', true, null), ('00000000-0000-0000-0000-00000000000a', 'id', false, 'A2'),
 ('00000000-0000-0000-0000-00000000000b', 'id', true, null), ('00000000-0000-0000-0000-00000000000b', 'fr', false, 'B1'),
 ('00000000-0000-0000-0000-00000000000c', 'id', true, null), ('00000000-0000-0000-0000-00000000000c', 'fr', false, 'A1'),
 ('00000000-0000-0000-0000-00000000000d', 'id', true, null), ('00000000-0000-0000-0000-00000000000d', 'en', false, 'B2'),
 ('00000000-0000-0000-0000-00000000000e', 'id', true, null), ('00000000-0000-0000-0000-00000000000e', 'fr', false, 'A2');
insert into public.blocks values ('00000000-0000-0000-0000-00000000000c', '00000000-0000-0000-0000-00000000000a');

set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
\echo 'Suggestions pour Martin (attendu : Ayu puis Rina, ville de Rina cachée)'
select first_name, age, city, native_language, learning_level, correction_preference, common_interests from public.partner_suggestions();

\echo 'Saluer Ayu'
select public.send_greeting('00000000-0000-0000-0000-00000000000b', '  Halo Ayu !  ') is not null as greeted;
select first_name from public.partner_suggestions();
select body from public.messages;

\echo 'Deuxième salut à Ayu (attendu : refusé)'
do $$ begin perform public.send_greeting('00000000-0000-0000-0000-00000000000b', 'Encore'); raise exception 'NOT REFUSED';
exception when others then if sqlerrm = 'NOT REFUSED' then raise; end if; raise notice 'refusé : %', sqlerrm; end $$;
\echo 'Saluer Budi qui a bloqué Martin (attendu : refusé)'
do $$ begin perform public.send_greeting('00000000-0000-0000-0000-00000000000c', 'Halo'); raise exception 'NOT REFUSED';
exception when others then if sqlerrm = 'NOT REFUSED' then raise; end if; raise notice 'refusé : %', sqlerrm; end $$;
\echo 'Message vide (attendu : refusé)'
do $$ begin perform public.send_greeting('00000000-0000-0000-0000-00000000000e', '   '); raise exception 'NOT REFUSED';
exception when others then if sqlerrm = 'NOT REFUSED' then raise; end if; raise notice 'refusé : %', sqlerrm; end $$;

\echo 'Ayu voit la demande et le message'
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select status from public.conversations;
select body from public.messages;
\echo 'Ayu ne voit plus Martin en suggestion'
select count(*) as suggestions_ayu from public.partner_suggestions();

reset role;
set role anon;
\echo 'anon (attendu : refusé)'
do $$ begin perform public.partner_suggestions(); raise exception 'NOT REFUSED';
exception when others then if sqlerrm = 'NOT REFUSED' then raise; end if; raise notice 'refusé : %', sqlerrm; end $$;
