-- Discussions, suite : signaler, séries à deux, corrections, appels proposés,
-- fond de la discussion, photos et messages vocaux, « écrit… » en temps réel.
-- Le fichier peut être relancé sans erreur.

---------------------------------------------------------------------------
-- 1. Signaler une personne
---------------------------------------------------------------------------
-- Personne ne lit cette table depuis l'app : l'équipe la consulte dans le tableau de bord.
create table if not exists public.reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references public.profiles (id) on delete cascade,
    reported_id uuid not null references public.profiles (id) on delete cascade,
    conversation_id uuid references public.conversations (id) on delete set null,
    reason text not null check (reason in ('spam', 'inappropriate', 'harassment', 'fake', 'minor', 'other')),
    details text check (char_length(details) <= 1000),
    status text not null default 'open' check (status in ('open', 'reviewed', 'dismissed')),
    created_at timestamptz not null default now(),
    check (reporter_id <> reported_id)
);
create index if not exists reports_open_idx on public.reports (created_at) where status = 'open';
alter table public.reports enable row level security;
revoke all on public.reports from public, anon, authenticated;

create or replace function public.report_user(target uuid, reason text, details text default null, conv uuid default null)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null or target is null or target = auth.uid() then
        raise exception 'invalid report';
    end if;
    -- Une conversation citée doit être la nôtre, avec cette personne.
    if conv is not null and not exists (
        select 1 from public.conversation_members me
        join public.conversation_members other on other.conversation_id = me.conversation_id
        where me.conversation_id = conv and me.user_id = auth.uid() and other.user_id = target
    ) then
        raise exception 'invalid report';
    end if;
    if (select count(*) from public.reports r
        where r.reporter_id = auth.uid() and r.created_at > now() - interval '1 day') >= 20 then
        raise exception 'too many reports';
    end if;
    insert into public.reports (reporter_id, reported_id, conversation_id, reason, details)
    values (auth.uid(), target, conv, reason, nullif(btrim(details), ''));
end;
$$;

revoke execute on function public.report_user(uuid, text, text, uuid) from public, anon;
grant execute on function public.report_user(uuid, text, text, uuid) to authenticated;

---------------------------------------------------------------------------
-- 2. Fond de la discussion (maquette 17) : choisi par l'un, vu par les deux
---------------------------------------------------------------------------
alter table public.conversations add column if not exists background text not null default 'sky';
alter table public.conversations drop constraint if exists conversations_background_check;
alter table public.conversations add constraint conversations_background_check
    check (background in ('sky', 'beach', 'forest', 'lagoon', 'sakura', 'none'));

create or replace function public.set_conversation_background(conv uuid, bg text)
returns void language plpgsql security definer set search_path = '' as $$
begin
    update public.conversations c set background = bg
    where c.id = conv and c.status = 'accepted' and public.can_post_in(conv);
    if not found then
        raise exception 'not allowed';
    end if;
end;
$$;

revoke execute on function public.set_conversation_background(uuid, text) from public, anon;
grant execute on function public.set_conversation_background(uuid, text) to authenticated;

---------------------------------------------------------------------------
-- 3. Série à deux : jours d'affilée où chacun a écrit au moins une fois
---------------------------------------------------------------------------
-- Les jours sont comptés en UTC, pour que les deux voient le même chiffre.
-- La série tient tant que le dernier jour commun est aujourd'hui ou hier.
create or replace function public.conversation_streak(conv uuid)
returns int language sql stable security definer set search_path = '' as $$
    with days as (
        select (m.created_at at time zone 'UTC')::date as d
        from public.messages m
        where m.conversation_id = conv
        group by 1
        having count(distinct m.sender_id) >= 2
    ),
    last_day as (select max(d) as d from days),
    ranked as (
        select d, d + (row_number() over (order by d desc))::int - 1 as anchor from days
    )
    select case
        when not public.is_conversation_member(conv) then null
        when (select d from last_day) is null
          or (select d from last_day) < (now() at time zone 'UTC')::date - 1 then 0
        else (select count(*)::int from ranked where anchor = (select d from last_day))
    end;
$$;

revoke execute on function public.conversation_streak(uuid) from public, anon;
grant execute on function public.conversation_streak(uuid) to authenticated;

-- La liste des discussions gagne la série et le fond.
drop function if exists public.my_conversations();
create function public.my_conversations()
returns table (
    id uuid,
    status public.conversation_status,
    requested_by_me boolean,
    partner_id uuid,
    partner_first_name text,
    partner_verified boolean,
    partner_last_seen timestamptz,
    partner_time_zone text,
    partner_native_language text,
    last_message_body text,
    last_message_kind public.message_kind,
    last_message_mine boolean,
    last_activity timestamptz,
    unread int,
    streak int,
    background text
)
language sql stable security definer set search_path = '' as $$
    select
        c.id,
        c.status,
        c.requested_by = auth.uid(),
        p.id,
        p.first_name,
        p.is_verified,
        p.last_seen_at,
        p.time_zone,
        (select l.language_code from public.user_languages l
          where l.user_id = p.id and l.is_native order by l.language_code limit 1),
        lm.body,
        lm.kind,
        lm.sender_id = auth.uid(),
        coalesce(lm.created_at, c.created_at),
        (select count(*)::int from public.messages m
          where m.conversation_id = c.id
            and m.sender_id is distinct from auth.uid()
            and (me.last_read_message_id is null
                 or m.created_at > (select r.created_at from public.messages r where r.id = me.last_read_message_id))),
        coalesce(public.conversation_streak(c.id), 0),
        c.background
    from public.conversation_members me
    join public.conversations c on c.id = me.conversation_id
    join public.conversation_members other on other.conversation_id = c.id and other.user_id <> me.user_id
    join public.profiles p on p.id = other.user_id
    left join lateral (
        select m.body, m.kind, m.sender_id, m.created_at
        from public.messages m
        where m.conversation_id = c.id
        order by m.created_at desc
        limit 1
    ) lm on true
    where me.user_id = auth.uid()
      and c.status <> 'declined'
      and not exists (
          select 1 from public.blocks b
          where (b.blocker_id = auth.uid() and b.blocked_id = p.id)
             or (b.blocker_id = p.id and b.blocked_id = auth.uid())
      )
    order by coalesce(lm.created_at, c.created_at) desc
    limit 200;
$$;

revoke execute on function public.my_conversations() from public, anon;
grant execute on function public.my_conversations() to authenticated;

---------------------------------------------------------------------------
-- 4. Messages : corrections, propositions d'appel, photos et vocaux
---------------------------------------------------------------------------
-- Une correction répond à un message de l'autre, dans la même conversation.
-- Une photo ou un vocal pointe vers un fichier rangé dans le dossier de la conversation.
create or replace function public.message_fits(conv uuid, k public.message_kind, reply uuid, media text)
returns boolean language sql stable security definer set search_path = '' as $$
    select
        (reply is null or exists (
            select 1 from public.messages r
            where r.id = reply and r.conversation_id = conv
              and (k <> 'correction' or r.sender_id is distinct from auth.uid())
        ))
        and (k <> 'correction' or reply is not null)
        and (case
            when k in ('photo', 'voice') then
                media is not null and media like conv::text || '/%' and position('..' in media) = 0
            else media is null
        end);
$$;

revoke execute on function public.message_fits(uuid, public.message_kind, uuid, text) from public, anon;
grant execute on function public.message_fits(uuid, public.message_kind, uuid, text) to authenticated;

drop policy if exists "members send messages" on public.messages;
create policy "members send messages"
    on public.messages for insert to authenticated
    with check (
        sender_id = auth.uid()
        and public.can_post_in(conversation_id)
        and public.message_fits(conversation_id, kind, reply_to_id, media_path)
    );

-- C'est le serveur qui date les messages : la série à deux ne peut pas être antidatée.
revoke insert on public.messages from authenticated;
grant insert (id, conversation_id, sender_id, kind, body, media_path, reply_to_id) on public.messages to authenticated;

---------------------------------------------------------------------------
-- 5. Fichiers des discussions (photos, vocaux) : un dossier par conversation
---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'chat-media', 'chat-media', false, 10485760,
    array['image/jpeg', 'image/png', 'image/webp',
          'audio/mp4', 'audio/aac', 'audio/x-m4a', 'audio/webm', 'audio/ogg', 'audio/mpeg']
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- « <conversation>/<fichier> » → la conversation, ou null si le nom ne suit pas ce modèle.
create or replace function public.media_conversation(object_name text)
returns uuid language plpgsql immutable set search_path = '' as $$
begin
    return split_part(object_name, '/', 1)::uuid;
exception when others then
    return null;
end;
$$;

-- On ne voit les fichiers qu'une fois la demande acceptée.
create or replace function public.can_read_media(conv uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select public.is_conversation_member(conv)
       and exists (select 1 from public.conversations c where c.id = conv and c.status = 'accepted');
$$;

revoke execute on function public.can_read_media(uuid) from public, anon;
grant execute on function public.can_read_media(uuid) to authenticated;

drop policy if exists "members read chat media" on storage.objects;
create policy "members read chat media"
    on storage.objects for select to authenticated
    using (bucket_id = 'chat-media' and public.can_read_media(public.media_conversation(name)));

drop policy if exists "members upload chat media" on storage.objects;
create policy "members upload chat media"
    on storage.objects for insert to authenticated
    with check (
        bucket_id = 'chat-media'
        and position('..' in name) = 0
        and public.can_read_media(public.media_conversation(name))
        and public.can_post_in(public.media_conversation(name))
    );

---------------------------------------------------------------------------
-- 6. « écrit… » : canal temps réel privé « chat:<conversation> », réservé aux deux membres
---------------------------------------------------------------------------
create or replace function public.topic_conversation(topic text)
returns uuid language plpgsql immutable set search_path = '' as $$
begin
    if topic not like 'chat:%' then
        return null;
    end if;
    return substring(topic from 6)::uuid;
exception when others then
    return null;
end;
$$;

drop policy if exists "members listen to their chat" on realtime.messages;
create policy "members listen to their chat"
    on realtime.messages for select to authenticated
    using (extension = 'broadcast' and public.can_read_media(public.topic_conversation(realtime.topic())));

drop policy if exists "members signal in their chat" on realtime.messages;
create policy "members signal in their chat"
    on realtime.messages for insert to authenticated
    with check (extension = 'broadcast' and public.can_post_in(public.topic_conversation(realtime.topic())));
