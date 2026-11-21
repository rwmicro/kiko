do $$ declare r text; begin foreach r in array array['anon', 'authenticated', 'service_role'] loop
  if not exists (select 1 from pg_roles where rolname = r) then execute format('create role %I nologin', r); end if;
end loop; end $$;
alter role service_role bypassrls; -- comme sur Supabase
create schema auth; create table auth.users(id uuid primary key);
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub', true),'')::uuid $$;
grant usage on schema auth to anon, authenticated; grant execute on function auth.uid() to anon, authenticated;
grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated;
alter default privileges in schema public grant all on functions to anon, authenticated;
create publication supabase_realtime;
-- Stockage et temps réel, réduits à ce que lisent les politiques.
create schema storage;
create table storage.buckets(id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]);
create table storage.objects(id uuid primary key default gen_random_uuid(), bucket_id text references storage.buckets(id), name text, owner uuid default auth.uid());
alter table storage.objects enable row level security;
grant usage on schema storage to anon, authenticated; grant select, insert on storage.objects to anon, authenticated;
create schema realtime;
create table realtime.messages(id bigserial primary key, topic text not null, extension text not null);
create function realtime.topic() returns text language sql stable as $$ select nullif(current_setting('realtime.topic', true), '') $$;
alter table realtime.messages enable row level security;
grant usage on schema realtime to anon, authenticated; grant select, insert on realtime.messages to anon, authenticated;
grant usage, select on sequence realtime.messages_id_seq to anon, authenticated;
