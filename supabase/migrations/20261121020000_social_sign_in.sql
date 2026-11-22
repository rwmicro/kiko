-- Connexion avec Apple et Google : ne garder que ce qui sert à se connecter.
-- Le fichier peut être relancé sans erreur.
--
-- À chaque connexion, Supabase recopie ce que le fournisseur envoie (nom complet, photo, prénom,
-- nom de famille, pseudo…) dans auth.users.raw_user_meta_data et auth.identities.identity_data.
-- L'app n'en a pas besoin : le prénom et la date de naissance sont demandés dans l'app, la photo aussi.
-- On ne garde donc qu'une liste fermée de clés, et on efface le reste à l'écriture, à chaque fois.
--
-- Gardé dans le compte (raw_user_meta_data) :
--   - first_name, birth_date : inscription par e-mail, pour reprendre un parcours interrompu
--     (effacés dès que le profil existe, voir 20261121010000_privacy.sql) ;
--   - email, email_verified, phone_verified, sub, iss, provider_id : identité de connexion.
-- Gardé dans l'identité (identity_data) : email, email_verified, phone_verified, sub, iss, provider_id.
--
-- Une nouvelle donnée de compte devra être ajoutée ici, sinon elle sera effacée.

create or replace function public.keep_only_sign_in_metadata(data jsonb, allowed text[])
returns jsonb language sql immutable set search_path = '' as $$
    select coalesce(jsonb_object_agg(key, value), '{}'::jsonb)
    from jsonb_each(coalesce(data, '{}'::jsonb))
    where key = any (allowed);
$$;

create or replace function public.strip_provider_profile_on_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    new.raw_user_meta_data := public.keep_only_sign_in_metadata(
        new.raw_user_meta_data,
        array['first_name', 'birth_date', 'email', 'email_verified', 'phone_verified', 'sub', 'iss', 'provider_id']
    );
    return new;
end;
$$;

create or replace function public.strip_provider_profile_on_identity()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    new.identity_data := public.keep_only_sign_in_metadata(
        new.identity_data,
        array['email', 'email_verified', 'phone_verified', 'sub', 'iss', 'provider_id']
    );
    return new;
end;
$$;

revoke execute on function public.keep_only_sign_in_metadata(jsonb, text[]) from public, anon, authenticated;
revoke execute on function public.strip_provider_profile_on_user() from public, anon, authenticated;
revoke execute on function public.strip_provider_profile_on_identity() from public, anon, authenticated;

drop trigger if exists users_strip_provider_profile on auth.users;
create trigger users_strip_provider_profile
    before insert or update of raw_user_meta_data on auth.users
    for each row execute function public.strip_provider_profile_on_user();

drop trigger if exists identities_strip_provider_profile on auth.identities;
create trigger identities_strip_provider_profile
    before insert or update of identity_data on auth.identities
    for each row execute function public.strip_provider_profile_on_identity();

-- Comptes déjà créés : même ménage, une fois.
update auth.users
set raw_user_meta_data = raw_user_meta_data
where raw_user_meta_data is not null;

update auth.identities
set identity_data = identity_data
where identity_data is not null;
