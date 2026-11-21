-- Garder le moins de données possible, et pas plus longtemps qu'il ne faut.
-- À exécuter après 20261121000000_notifications.sql. Le fichier peut être relancé sans erreur.
--
-- Ce qui est effacé, et quand :
--   - prénom et date de naissance recopiés dans le compte à l'inscription : dès que le profil existe ;
--   - inscription jamais confirmée : 7 jours ; confirmée mais sans profil : 30 jours ;
--   - signalement traité : 1 an (un signalement ouvert reste jusqu'à ce que l'équipe le traite) ;
--   - compte sans aucune ouverture de l'app depuis 2 ans : supprimé, comme s'il l'avait demandé ;
--   - compte supprimé (bouton « Supprimer mon compte ») : profil, langues, discussions, photos et
--     vocaux, appareils, préférences, tout part. Voir la fonction Edge « delete-account ».

---------------------------------------------------------------------------
-- 1. L'inscription n'a plus besoin du prénom et de la date de naissance une fois le profil créé
---------------------------------------------------------------------------
-- Ils servent à reprendre un parcours interrompu (voir AccountService.restore). Ensuite, le profil
-- fait foi : on ne garde pas une deuxième copie dans auth.users.
create or replace function public.forget_signup_metadata()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    update auth.users
    set raw_user_meta_data = coalesce(raw_user_meta_data, '{}'::jsonb) - 'first_name' - 'birth_date'
    where id = new.id;
    return null;
end;
$$;

revoke execute on function public.forget_signup_metadata() from public, anon, authenticated;

drop trigger if exists profiles_forget_signup_metadata on public.profiles;
create trigger profiles_forget_signup_metadata
    after insert on public.profiles
    for each row execute function public.forget_signup_metadata();

update auth.users u
set raw_user_meta_data = u.raw_user_meta_data - 'first_name' - 'birth_date'
where exists (select 1 from public.profiles p where p.id = u.id)
  and (u.raw_user_meta_data ? 'first_name' or u.raw_user_meta_data ? 'birth_date');

---------------------------------------------------------------------------
-- 2. Un signalement survit au départ de la personne qui l'a fait, sans son nom
---------------------------------------------------------------------------
alter table public.reports alter column reporter_id drop not null;
alter table public.reports drop constraint if exists reports_reporter_id_fkey;
alter table public.reports
    add constraint reports_reporter_id_fkey
    foreign key (reporter_id) references public.profiles (id) on delete set null;

---------------------------------------------------------------------------
-- 3. Supprimer un compte (appelé par la fonction Edge « delete-account », rôle service_role)
---------------------------------------------------------------------------
-- Les discussions de la personne : la fonction Edge efface d'abord leurs fichiers (photos, vocaux).
create or replace function public.account_conversations(p_user uuid)
returns setof uuid language sql stable security definer set search_path = '' as $$
    select m.conversation_id from public.conversation_members m where m.user_id = p_user;
$$;

-- Une discussion se fait à deux : quand l'un part, elle part avec lui, messages compris.
create or replace function public.forget_account_data(p_user uuid)
returns void language sql security definer set search_path = '' as $$
    delete from public.conversations c
    where exists (select 1 from public.conversation_members m
                  where m.conversation_id = c.id and m.user_id = p_user);
$$;

-- Comptes sans aucune ouverture de l'app depuis 2 ans.
create or replace function public.inactive_accounts(p_limit int default 50)
returns setof uuid language sql stable security definer set search_path = '' as $$
    select p.id from public.profiles p
    where p.last_seen_at < now() - interval '2 years'
    order by p.last_seen_at
    limit greatest(1, least(p_limit, 200));
$$;

revoke execute on function public.account_conversations(uuid) from public, anon, authenticated;
revoke execute on function public.forget_account_data(uuid) from public, anon, authenticated;
revoke execute on function public.inactive_accounts(int) from public, anon, authenticated;
grant execute on function public.account_conversations(uuid) to service_role;
grant execute on function public.forget_account_data(uuid) to service_role;
grant execute on function public.inactive_accounts(int) to service_role;

---------------------------------------------------------------------------
-- 4. Ménage de nuit
---------------------------------------------------------------------------
create or replace function public.purge_old_data()
returns void language plpgsql security definer set search_path = '' as $$
begin
    delete from public.reports
    where status <> 'open' and created_at < now() - interval '1 year';

    -- Inscriptions abandonnées : un e-mail, et parfois un prénom et une date de naissance, sans profil.
    delete from auth.users u
    where not exists (select 1 from public.profiles p where p.id = u.id)
      and ((u.email_confirmed_at is null and u.created_at < now() - interval '7 days')
           or u.created_at < now() - interval '30 days');

    -- Les comptes inactifs passent par la fonction Edge, qui sait effacer les fichiers.
    if exists (select 1 from public.profiles p where p.last_seen_at < now() - interval '2 years') then
        perform public.call_edge_function('delete-account');
    end if;
end;
$$;

revoke execute on function public.purge_old_data() from public, anon, authenticated;

do $$
begin
    perform cron.schedule('kiko-privacy-cleanup', '37 3 * * *', 'select public.purge_old_data()');
exception when others then
    raise notice 'pg_cron indisponible (base locale ?) : %', sqlerrm;
end;
$$;
