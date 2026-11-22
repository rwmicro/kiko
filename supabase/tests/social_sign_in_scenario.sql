-- Connexion Apple et Google : le nom et la photo envoyés par le fournisseur ne sont jamais gardés.
\set ON_ERROR_STOP 1

-- Un compte Google, tel que Supabase l'écrit à la première connexion.
insert into auth.users (id, raw_user_meta_data) values (
    '00000000-0000-0000-0000-0000000000a1',
    '{"iss": "https://accounts.google.com", "sub": "1234", "email": "ayu@exemple.id", "email_verified": true,
      "name": "Ayu Lestari", "full_name": "Ayu Lestari", "given_name": "Ayu", "family_name": "Lestari",
      "picture": "https://lh3.googleusercontent.com/a/x", "avatar_url": "https://lh3.googleusercontent.com/a/x",
      "provider_id": "1234", "custom_claims": {"hd": "exemple.id"}}'
);
insert into auth.identities (user_id, provider, identity_data) values (
    '00000000-0000-0000-0000-0000000000a1', 'google',
    '{"iss": "https://accounts.google.com", "sub": "1234", "email": "ayu@exemple.id", "email_verified": true,
      "name": "Ayu Lestari", "picture": "https://lh3.googleusercontent.com/a/x", "provider_id": "1234"}'
);

do $$
declare meta jsonb; ident jsonb;
begin
    select raw_user_meta_data into meta from auth.users where id = '00000000-0000-0000-0000-0000000000a1';
    select identity_data into ident from auth.identities where user_id = '00000000-0000-0000-0000-0000000000a1';
    if meta ?| array['name', 'full_name', 'given_name', 'family_name', 'picture', 'avatar_url', 'custom_claims'] then
        raise exception 'Le compte garde le nom ou la photo du fournisseur : %', meta;
    end if;
    if ident ?| array['name', 'picture'] then
        raise exception 'L''identité garde le nom ou la photo du fournisseur : %', ident;
    end if;
    if meta ->> 'email' <> 'ayu@exemple.id' or meta ->> 'sub' <> '1234' or ident ->> 'sub' <> '1234' then
        raise exception 'L''identité de connexion a été perdue : % / %', meta, ident;
    end if;
end $$;

-- Connexion suivante : Supabase réécrit les données du fournisseur, elles sont de nouveau effacées.
update auth.users
set raw_user_meta_data = raw_user_meta_data || '{"name": "Ayu L.", "picture": "https://exemple/photo.jpg"}'
where id = '00000000-0000-0000-0000-0000000000a1';
update auth.identities
set identity_data = identity_data || '{"full_name": "Ayu L."}'
where user_id = '00000000-0000-0000-0000-0000000000a1';

do $$
begin
    if exists (select 1 from auth.users where id = '00000000-0000-0000-0000-0000000000a1'
               and raw_user_meta_data ?| array['name', 'picture']) then
        raise exception 'Le nom ou la photo sont revenus après une nouvelle connexion';
    end if;
    if exists (select 1 from auth.identities where user_id = '00000000-0000-0000-0000-0000000000a1'
               and identity_data ? 'full_name') then
        raise exception 'Le nom est revenu dans l''identité après une nouvelle connexion';
    end if;
end $$;

-- Inscription par e-mail : prénom et date de naissance gardés pour reprendre le parcours.
insert into auth.users (id, raw_user_meta_data) values (
    '00000000-0000-0000-0000-0000000000b2',
    '{"first_name": "Martin", "birth_date": "1999-03-14", "email_verified": false}'
);

do $$
begin
    if not exists (select 1 from auth.users where id = '00000000-0000-0000-0000-0000000000b2'
                   and raw_user_meta_data ->> 'first_name' = 'Martin'
                   and raw_user_meta_data ->> 'birth_date' = '1999-03-14') then
        raise exception 'L''inscription par e-mail a perdu le prénom ou la date de naissance';
    end if;
end $$;

-- Personne ne peut appeler ces fonctions depuis l'app.
do $$
begin
    if has_function_privilege('authenticated', 'public.keep_only_sign_in_metadata(jsonb, text[])', 'execute')
       or has_function_privilege('anon', 'public.strip_provider_profile_on_user()', 'execute') then
        raise exception 'Les fonctions de ménage sont appelables depuis l''app';
    end if;
end $$;
