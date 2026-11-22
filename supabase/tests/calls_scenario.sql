-- Scénario de test des appels audio (sonnerie, décrocher, raccrocher, nuit, blocage, jetons LiveKit),
-- à lancer sur une base locale chargée avec les migrations. Ne pas lancer sur le projet Supabase.
\set ON_ERROR_STOP 1
insert into auth.users values
 ('00000000-0000-0000-0000-00000000000a'), ('00000000-0000-0000-0000-00000000000b'),
 ('00000000-0000-0000-0000-00000000000c'), ('00000000-0000-0000-0000-00000000000d');
-- Des fuseaux où il est midi et 3 h du matin en ce moment : le test ne dépend pas de l'heure à laquelle il tourne.
select name as midi from pg_timezone_names where name like 'Etc/GMT%' and extract(hour from now() at time zone name) = 12 limit 1 \gset
select name as nuit from pg_timezone_names where name like 'Etc/GMT%' and extract(hour from now() at time zone name) = 3 limit 1 \gset
insert into public.profiles (id, first_name, birth_date, time_zone, last_seen_at) values
 ('00000000-0000-0000-0000-00000000000a', 'Martin', '1999-03-14', :'midi', now()),
 ('00000000-0000-0000-0000-00000000000b', 'Ayu', '2001-07-09', :'midi', now()),
 ('00000000-0000-0000-0000-00000000000c', 'Budi', '1995-01-01', :'nuit', now()),
 ('00000000-0000-0000-0000-00000000000d', 'Sari', '1998-05-05', :'midi', now());
insert into public.user_languages values
 ('00000000-0000-0000-0000-00000000000a', 'fr', true, null), ('00000000-0000-0000-0000-00000000000a', 'id', false, 'A2'),
 ('00000000-0000-0000-0000-00000000000b', 'id', true, null), ('00000000-0000-0000-0000-00000000000b', 'fr', false, 'B1'),
 ('00000000-0000-0000-0000-00000000000c', 'id', true, null), ('00000000-0000-0000-0000-00000000000c', 'fr', false, 'A1'),
 ('00000000-0000-0000-0000-00000000000d', 'id', true, null), ('00000000-0000-0000-0000-00000000000d', 'fr', false, 'A1');

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

-- Martin écrit à Ayu, Budi et Sari ; Ayu et Budi acceptent, Sari pas encore.
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select public.register_push_device('appareil-android-martin', 'android');
select public.send_greeting('00000000-0000-0000-0000-00000000000b', 'Halo Ayu!') as conv_ayu \gset
select public.send_greeting('00000000-0000-0000-0000-00000000000c', 'Halo Budi!') as conv_budi \gset
select public.send_greeting('00000000-0000-0000-0000-00000000000d', 'Halo Sari!') as conv_sari \gset
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select public.register_push_device('appareil-ios-ayu', 'ios');
select public.register_push_device('voip-ios-ayu-0000', 'ios', 'voip', null, true);
select public.respond_to_request(:'conv_ayu', true);
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000c';
select public.respond_to_request(:'conv_budi', true);

\echo 'La table des appels reste fermée, et les fonctions du serveur aussi'
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select pg_temp.refused('select * from public.calls');
select pg_temp.refused('select public.call_room_for(gen_random_uuid(), auth.uid())');
select pg_temp.refused('select public.expire_calls()');
select pg_temp.refused($$select public.register_push_device('voip-windows', 'ios', 'pager')$$);

\echo 'Pas d''appel avant que la demande soit acceptée'
select pg_temp.refused(format('select * from public.start_call(%L)', :'conv_sari'));

\echo 'Pas de sonnerie la nuit chez Budi, sauf appel prévu'
select pg_temp.refused(format('select * from public.start_call(%L)', :'conv_budi'));
insert into public.messages (id, conversation_id, sender_id, kind, body) values
 (gen_random_uuid(), :'conv_budi', auth.uid(), 'call_proposal', json_build_object('accepted_at', now() + interval '5 minutes', 'minutes', 15)::text);
select call_id as call_budi from public.start_call(:'conv_budi') \gset
select pg_temp.check(public.end_call(:'call_budi') = 'cancelled', 'Martin annule avant la réponse');

\echo 'Martin appelle Ayu : ça sonne, un push VoIP part'
select call_id as call1, status from public.start_call(:'conv_ayu') \gset
select pg_temp.check(:'status' = 'ringing', 'ça sonne');
select pg_temp.check((select s.call_id from public.start_call(:'conv_ayu') s) = :'call1', 'rappeler pendant la sonnerie ne crée pas un deuxième appel');
select pg_temp.check((select caller from public.call_state(:'call1')), 'Martin est l''appelant');

\echo 'Budi ne voit pas l''appel des autres, ni ne peut y répondre'
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000c';
select pg_temp.check(not exists (select 1 from public.call_state(:'call1')), 'appel invisible pour Budi');
select pg_temp.refused(format('select public.answer_call(%L)', :'call1'));
select pg_temp.refused(format('select public.end_call(%L)', :'call1'));

\echo 'Ayu décroche'
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select pg_temp.check(public.answer_call(:'call1') = 'active', 'Ayu décroche');
select pg_temp.check((select status from public.call_state(:'call1')) = 'active', 'Martin voit l''appel décroché');

reset role;
set role service_role;
\echo 'Jetons LiveKit : les deux dans la salle, personne d''autre'
select pg_temp.check(public.call_room_for(:'call1', '00000000-0000-0000-0000-00000000000a') = 'call-' || :'call1', 'salle pour Martin');
select pg_temp.check(public.call_room_for(:'call1', '00000000-0000-0000-0000-00000000000b') = 'call-' || :'call1', 'salle pour Ayu');
select pg_temp.check(public.call_room_for(:'call1', '00000000-0000-0000-0000-00000000000c') is null, 'pas de salle pour Budi');
\echo 'La file d''envoi : un call_ring pour Ayu, avec le prénom de Martin, et les jetons VoIP'
select pg_temp.check((select count(*) from public.push_outbox where kind = 'call_ring' and user_id = '00000000-0000-0000-0000-00000000000b'
                       and payload ->> 'caller_name' = 'Martin' and payload ->> 'call_id' = :'call1') = 1, 'un seul call_ring pour Ayu');
select pg_temp.check((select count(*) from public.push_outbox where kind = 'call_cancel' and user_id = '00000000-0000-0000-0000-00000000000c') = 1, 'call_cancel pour Budi');
create temp table batch as select * from public.claim_push_batch(500);
select pg_temp.check((select devices @> '[{"kind":"voip","token":"voip-ios-ayu-0000"}]' from batch where kind = 'call_ring' and user_id = '00000000-0000-0000-0000-00000000000b'), 'le jeton VoIP part avec le call_ring');
select public.finish_push(id, null) from batch;
select pg_temp.check(not exists (select 1 from public.push_outbox where kind like 'call_%' and payload <> '{}'), 'le prénom est effacé une fois parti');
reset role;
set role authenticated;

\echo 'Martin raccroche'
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select pg_temp.check(public.end_call(:'call1') = 'ended', 'appel fini');
select pg_temp.check(public.end_call(:'call1') = 'ended', 'raccrocher deux fois ne change rien');
reset role;
select pg_temp.check(public.call_room_for(:'call1', '00000000-0000-0000-0000-00000000000a') is null, 'plus de jeton après la fin');
set role authenticated;

\echo 'Ayu refuse ; un appel sans réponse est manqué'
select call_id as call2 from public.start_call(:'conv_ayu') \gset
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select pg_temp.check(public.end_call(:'call2') = 'declined', 'Ayu refuse');
reset role;
select pg_temp.check(public.call_room_for(:'call2', '00000000-0000-0000-0000-00000000000b') is null, 'pas de salle après un refus');
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select call_id as call3 from public.start_call(:'conv_ayu') \gset
reset role;
update public.calls set created_at = now() - interval '1 minute' where id = :'call3';
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select pg_temp.check((select status from public.call_state(:'call3')) = 'missed', 'manqué après 45 secondes');

\echo 'Appels croisés : Ayu appelle pendant que Martin l''appelle, les deux se retrouvent'
select call_id as call4 from public.start_call(:'conv_ayu') \gset
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
select pg_temp.check((select s.call_id = :'call4' and s.status = 'active' from public.start_call(:'conv_ayu') s), 'Ayu décroche l''appel de Martin');
select public.end_call(:'call4');

\echo 'Pas plus de 10 appels par heure'
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
-- Déjà 5 appels de Martin dans l'heure : 5 de plus, puis le 11e est refusé.
do $$ begin
    for i in 1..5 loop
        perform public.end_call((select s.call_id from public.start_call((select c.id from public.conversations c
            join public.conversation_members m on m.conversation_id = c.id and m.user_id = '00000000-0000-0000-0000-00000000000b'
            where c.status = 'accepted' limit 1)) s));
    end loop;
end $$;
select pg_temp.refused(format('select * from public.start_call(%L)', :'conv_ayu'));

\echo 'Blocage : plus d''appel'
reset role;
delete from public.calls;
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000b';
insert into public.blocks (blocker_id, blocked_id) values (auth.uid(), '00000000-0000-0000-0000-00000000000a');
set request.jwt.claim.sub = '00000000-0000-0000-0000-00000000000a';
select pg_temp.refused(format('select * from public.start_call(%L)', :'conv_ayu'));
reset role;

\echo 'Ménage : 30 jours d''historique au plus, et l''appel part avec la discussion'
insert into public.calls (conversation_id, caller_id, callee_id, status, created_at)
values (:'conv_budi', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000c', 'ended', now() - interval '31 days'),
       (:'conv_budi', '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000c', 'ended', now() - interval '1 day');
select public.purge_calls();
select pg_temp.check((select count(*) from public.calls where conversation_id = :'conv_budi') = 1, 'appel de plus de 30 jours oublié');
select public.forget_account_data('00000000-0000-0000-0000-00000000000c');
select pg_temp.check(not exists (select 1 from public.calls where conversation_id = :'conv_budi'), 'compte supprimé : ses appels aussi');
\echo 'Scénario des appels : tout est bon'
