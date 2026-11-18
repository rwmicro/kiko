-- Onglet Discussions : la liste des conversations, les non-lus et la lecture.

-- Mes conversations, la plus récente en premier : la personne en face, le dernier message
-- et le nombre de messages reçus depuis ma dernière lecture. Les demandes refusées et les
-- personnes bloquées (dans un sens ou dans l'autre) n'apparaissent pas.
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
    unread int
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
                 or m.created_at > (select r.created_at from public.messages r where r.id = me.last_read_message_id)))
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

-- Marque une conversation comme lue jusqu'à son dernier message.
create or replace function public.mark_read(conv uuid)
returns void language sql security definer set search_path = '' as $$
    update public.conversation_members
       set last_read_message_id = (
           select m.id from public.messages m
           where m.conversation_id = conv
           order by m.created_at desc
           limit 1
       )
     where conversation_id = conv and user_id = auth.uid();
$$;

revoke execute on function public.mark_read(uuid) from public, anon;
grant execute on function public.mark_read(uuid) to authenticated;

-- L'accusé de lecture n'est visible qu'une fois la demande acceptée (maquette 15 :
-- « tant que tu n'as pas accepté, elle ne voit pas si tu as lu »).
create or replace function public.partner_read_up_to(conv uuid)
returns timestamptz language sql stable security definer set search_path = '' as $$
    select r.created_at
    from public.conversation_members other
    join public.conversations c on c.id = other.conversation_id
    join public.messages r on r.id = other.last_read_message_id
    where other.conversation_id = conv
      and other.user_id <> auth.uid()
      and c.status = 'accepted'
      and public.is_conversation_member(conv);
$$;

revoke execute on function public.partner_read_up_to(uuid) from public, anon;
grant execute on function public.partner_read_up_to(uuid) to authenticated;

-- Le marqueur de lecture de l'autre se lisait directement dans conversation_members :
-- on passe désormais par partner_read_up_to, qui respecte la règle ci-dessus.
revoke select on public.conversation_members from authenticated;
grant select (conversation_id, user_id, joined_at) on public.conversation_members to authenticated;
revoke update (last_read_message_id) on public.conversation_members from authenticated;
