-- Scénario de test de l'onglet Discussions, à lancer sur une base locale chargée avec les migrations
-- (rôles anon/authenticated et auth.uid() simulés). Ne pas lancer sur le projet Supabase.
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

set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select public.send_greeting('00000000-0000-0000-0000-00000000000b', 'Halo Ayu!') as conv_ayu \gset
select public.send_greeting('00000000-0000-0000-0000-00000000000c', 'Halo Budi!') as conv_budi \gset

\echo 'Martin : deux demandes envoyées, rien de non lu'
select partner_first_name, status, requested_by_me, last_message_body, last_message_mine, unread from public.my_conversations();

set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
\echo 'Ayu : une demande reçue, 1 non lu, sa langue natale est donnée'
select partner_first_name, status, requested_by_me, partner_native_language, unread from public.my_conversations();
\echo 'Ayu ne lit plus le marqueur de lecture directement (attendu : refusé)'
do $$ begin perform last_read_message_id from public.conversation_members; raise exception 'NOT REFUSED';
exception when others then if sqlerrm = 'NOT REFUSED' then raise; end if; raise notice 'refusé : %', sqlerrm; end $$;
select public.respond_to_request(:'conv_ayu', true);
select public.mark_read(:'conv_ayu');
insert into public.messages (id, conversation_id, sender_id, body)
  values (gen_random_uuid(), :'conv_ayu', auth.uid(), 'Bonjour Martin !');
\echo 'Ayu : acceptée, lue'
select partner_first_name, status, unread from public.my_conversations();

set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000c';
\echo 'Budi refuse'
select public.respond_to_request(:'conv_budi', false);
select count(*) as conversations_budi from public.my_conversations();

set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
\echo 'Martin : Ayu a répondu (1 non lu), la demande refusée par Budi a disparu'
select partner_first_name, status, last_message_body, unread from public.my_conversations();
\echo 'Martin voit que Ayu a lu son salut'
select public.partner_read_up_to(:'conv_ayu') is not null as ayu_a_lu;
select public.mark_read(:'conv_ayu');
select unread from public.my_conversations();
\echo 'Martin bloque Ayu : la conversation disparaît de sa liste'
insert into public.blocks values ('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b');
select count(*) as conversations_martin from public.my_conversations();

reset role;
set role anon;
\echo 'anon (attendu : refusé)'
do $$ begin perform public.my_conversations(); raise exception 'NOT REFUSED';
exception when others then if sqlerrm = 'NOT REFUSED' then raise; end if; raise notice 'refusé : %', sqlerrm; end $$;
