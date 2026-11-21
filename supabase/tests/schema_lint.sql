-- Règles de sécurité du schéma, vérifiées après chaque migration (équivalent local du Security Advisor Supabase).
-- Chaque requête liste les fautes ; le bloc final échoue s'il y en a une seule.
\set ON_ERROR_STOP 1

create temp table lint_faults(rule text, object text);

-- 1. Toute table du schéma public a la Row Level Security activée.
insert into lint_faults
select 'Table sans RLS', c.relname
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind in ('r', 'p') and not c.relrowsecurity;

-- 2. Toute fonction SECURITY DEFINER fixe son search_path (sinon détournable par un objet homonyme).
insert into lint_faults
select 'SECURITY DEFINER sans search_path', p.oid::regprocedure::text
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.prosecdef
  and not exists (select 1 from unnest(coalesce(p.proconfig, '{}')) cfg where cfg like 'search_path=%');

-- 3. Un visiteur non connecté (anon) ne peut rien écrire.
insert into lint_faults
select distinct 'anon peut écrire', table_name || ' (' || privilege_type || ')'
from information_schema.role_table_grants
where grantee = 'anon' and table_schema = 'public'
  and privilege_type in ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE');

-- 4. Aucune vue ne contourne la RLS des tables qu'elle lit (vue sans security_invoker).
insert into lint_faults
select 'Vue sans security_invoker', c.relname
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'v'
  and not coalesce(c.reloptions @> array['security_invoker=true'], false);

select rule as "règle", object as "objet" from lint_faults order by 1, 2;

do $$
declare n int;
begin
  select count(*) into n from lint_faults;
  if n > 0 then raise exception '% faute(s) de sécurité dans le schéma, voir la liste ci-dessus', n; end if;
end $$;
