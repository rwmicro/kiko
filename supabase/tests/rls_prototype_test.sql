\set ON_ERROR_STOP 0
insert into auth.users values ('00000000-0000-0000-0000-00000000000a'),('00000000-0000-0000-0000-00000000000b'),('00000000-0000-0000-0000-00000000000c');
create or replace function as_user(u text) returns void language plpgsql as $$ begin perform set_config('request.jwt.claim.sub', u, false); end $$;
grant execute on function as_user(text) to authenticated;
set role authenticated;
select as_user('00000000-0000-0000-0000-00000000000a');
insert into profiles(id, first_name, birth_date) values ('00000000-0000-0000-0000-00000000000a','Martin','1999-03-14');
insert into profiles(id, first_name, birth_date) values ('00000000-0000-0000-0000-00000000000b','Mineur','2015-01-01'); -- doit échouer (autre id)
select as_user('00000000-0000-0000-0000-00000000000b');
insert into profiles(id, first_name, birth_date) values ('00000000-0000-0000-0000-00000000000b','Mineur','2015-01-01'); -- doit échouer (âge)
insert into profiles(id, first_name, birth_date) values ('00000000-0000-0000-0000-00000000000b','Putri','2000-05-01');
update profiles set is_verified = true where id = auth.uid(); -- doit échouer
select as_user('00000000-0000-0000-0000-00000000000c');
insert into profiles(id, first_name, birth_date) values ('00000000-0000-0000-0000-00000000000c','Intrus','1990-01-01');
select as_user('00000000-0000-0000-0000-00000000000a');
select request_conversation('00000000-0000-0000-0000-00000000000b') as conv \gset
insert into messages(id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv', auth.uid(), 'Halo!'); -- OK (demandeur)
select respond_to_request(:'conv', true); -- doit échouer (propre demande)
select as_user('00000000-0000-0000-0000-00000000000b');
insert into messages(id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv', auth.uid(), 'Bonjour'); -- doit échouer (pas accepté)
select respond_to_request(:'conv', true); -- OK
insert into messages(id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv', auth.uid(), 'Bonjour'); -- OK
select 'B voit', count(*) from messages;
select as_user('00000000-0000-0000-0000-00000000000c');
select 'C voit', count(*) from messages;
insert into messages(id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv', auth.uid(), 'spam'); -- doit échouer
select as_user('00000000-0000-0000-0000-00000000000b');
insert into blocks values ('00000000-0000-0000-0000-00000000000b','00000000-0000-0000-0000-00000000000a');
select as_user('00000000-0000-0000-0000-00000000000a');
insert into messages(id, conversation_id, sender_id, body) values (gen_random_uuid(), :'conv', auth.uid(), 'encore?'); -- doit échouer (bloqué)
