-- Schéma du prototype : profils, langues, conversations et messages.
-- Toutes les tables ont la Row Level Security activée : la clé « anon » de l'app
-- ne donne accès qu'aux lignes autorisées par les politiques ci-dessous.

-- ---------------------------------------------------------------------------
-- Profils
-- ---------------------------------------------------------------------------
create type public.correction_preference as enum ('all', 'major_only', 'none');
create type public.cefr_level as enum ('A1', 'A2', 'B1', 'B2', 'C1', 'C2');

create table public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    first_name text not null check (char_length(first_name) between 1 and 40),
    birth_date date not null check (birth_date <= (current_date - interval '18 years')),
    avatar_path text,
    city text,
    country_code char(2),
    show_city boolean not null default true,
    time_zone text not null default 'UTC',
    correction_preference public.correction_preference not null default 'all',
    is_verified boolean not null default false,
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- Tout utilisateur connecté peut lire les profils (la date de naissance reste lisible :
-- l'app n'affiche que l'âge ; une vue publique sans date de naissance viendra avec le matching).
create policy "profiles readable by signed-in users"
    on public.profiles for select to authenticated using (true);
create policy "users insert their own profile"
    on public.profiles for insert to authenticated with check (id = auth.uid() and is_verified = false);
create policy "users update their own profile"
    on public.profiles for update to authenticated
    using (id = auth.uid()) with check (id = auth.uid());

-- Le badge « Vérifié » n'est posé que par le serveur (vérification selfie) :
-- l'app ne peut modifier que les colonnes listées ici.
revoke update on public.profiles from anon, authenticated;
grant update (first_name, avatar_path, city, country_code, show_city, time_zone, correction_preference, last_seen_at)
    on public.profiles to authenticated;

-- ---------------------------------------------------------------------------
-- Langues parlées et apprises
-- ---------------------------------------------------------------------------
create table public.user_languages (
    user_id uuid not null references public.profiles (id) on delete cascade,
    language_code text not null check (language_code ~ '^[a-z]{2,3}$'),
    is_native boolean not null,
    level public.cefr_level,
    primary key (user_id, language_code),
    check (is_native or level is not null)
);

alter table public.user_languages enable row level security;

create policy "languages readable by signed-in users"
    on public.user_languages for select to authenticated using (true);
create policy "users manage their own languages"
    on public.user_languages for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- Blocages
-- ---------------------------------------------------------------------------
create table public.blocks (
    blocker_id uuid not null references public.profiles (id) on delete cascade,
    blocked_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);

alter table public.blocks enable row level security;

create policy "users see their own blocks"
    on public.blocks for select to authenticated using (blocker_id = auth.uid());
create policy "users block others"
    on public.blocks for insert to authenticated with check (blocker_id = auth.uid());
create policy "users unblock"
    on public.blocks for delete to authenticated using (blocker_id = auth.uid());

-- ---------------------------------------------------------------------------
-- Conversations et messages
-- ---------------------------------------------------------------------------
create type public.conversation_status as enum ('requested', 'accepted', 'declined');
create type public.message_kind as enum ('text', 'voice', 'photo', 'correction', 'call_proposal');

create table public.conversations (
    id uuid primary key default gen_random_uuid(),
    requested_by uuid not null references public.profiles (id) on delete cascade,
    status public.conversation_status not null default 'requested',
    created_at timestamptz not null default now(),
    last_message_at timestamptz not null default now()
);

create table public.conversation_members (
    conversation_id uuid not null references public.conversations (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    last_read_message_id uuid,
    joined_at timestamptz not null default now(),
    primary key (conversation_id, user_id)
);

create table public.messages (
    -- Identifiant généré par l'app : l'envoi peut être rejoué sans doublon (file d'envoi hors ligne).
    id uuid primary key,
    conversation_id uuid not null references public.conversations (id) on delete cascade,
    sender_id uuid references public.profiles (id) on delete set null,
    kind public.message_kind not null default 'text',
    body text check (char_length(body) <= 4000),
    media_path text,
    reply_to_id uuid references public.messages (id) on delete set null,
    created_at timestamptz not null default now(),
    check (body is not null or media_path is not null)
);

create index messages_conversation_created_idx on public.messages (conversation_id, created_at desc);
create index conversation_members_user_idx on public.conversation_members (user_id);

-- Fonctions d'aide, en security definer pour éviter la récursion des politiques RLS.
create function public.is_conversation_member(conv uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.conversation_members m
        where m.conversation_id = conv and m.user_id = auth.uid()
    );
$$;

create function public.can_post_in(conv uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select public.is_conversation_member(conv)
    and exists (
        select 1 from public.conversations c
        where c.id = conv
          and (c.status = 'accepted' or (c.status = 'requested' and c.requested_by = auth.uid()))
    )
    and not exists (
        select 1
        from public.conversation_members other
        join public.blocks b
          on (b.blocker_id = other.user_id and b.blocked_id = auth.uid())
          or (b.blocker_id = auth.uid() and b.blocked_id = other.user_id)
        where other.conversation_id = conv and other.user_id <> auth.uid()
    );
$$;

alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;

create policy "members read their conversations"
    on public.conversations for select to authenticated using (public.is_conversation_member(id));

create policy "members read membership"
    on public.conversation_members for select to authenticated using (public.is_conversation_member(conversation_id));
create policy "members update their read marker"
    on public.conversation_members for update to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "members read messages"
    on public.messages for select to authenticated using (public.is_conversation_member(conversation_id));
create policy "members send messages"
    on public.messages for insert to authenticated
    with check (sender_id = auth.uid() and public.can_post_in(conversation_id));

-- Ouvrir une demande de discussion : crée la conversation et ses deux membres d'un coup.
create function public.request_conversation(partner uuid)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    conv uuid;
begin
    if auth.uid() is null or partner = auth.uid() then
        raise exception 'invalid partner';
    end if;
    if exists (
        select 1 from public.blocks b
        where (b.blocker_id = partner and b.blocked_id = auth.uid())
           or (b.blocker_id = auth.uid() and b.blocked_id = partner)
    ) then
        raise exception 'blocked';
    end if;

    insert into public.conversations (requested_by) values (auth.uid()) returning id into conv;
    insert into public.conversation_members (conversation_id, user_id)
        values (conv, auth.uid()), (conv, partner);
    return conv;
end;
$$;

revoke execute on function public.request_conversation(uuid) from public, anon;
grant execute on function public.request_conversation(uuid) to authenticated;

-- Répondre à une demande : seule la personne qui l'a reçue peut accepter ou refuser.
create function public.respond_to_request(conv uuid, accept boolean)
returns void language plpgsql security definer set search_path = '' as $$
begin
    update public.conversations c
       set status = case when accept then 'accepted'::public.conversation_status
                         else 'declined'::public.conversation_status end
     where c.id = conv
       and c.status = 'requested'
       and c.requested_by <> auth.uid()
       and public.is_conversation_member(conv);
    if not found then
        raise exception 'no pending request';
    end if;
end;
$$;

revoke execute on function public.respond_to_request(uuid, boolean) from public, anon;
grant execute on function public.respond_to_request(uuid, boolean) to authenticated;

-- Garde la date du dernier message à jour pour trier la liste des discussions.
create function public.touch_conversation()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    update public.conversations set last_message_at = new.created_at where id = new.conversation_id;
    return new;
end;
$$;

create trigger messages_touch_conversation
    after insert on public.messages
    for each row execute function public.touch_conversation();

-- Diffusion temps réel des nouveaux messages aux membres (Supabase Realtime).
alter publication supabase_realtime add table public.messages;
