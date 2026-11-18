-- Écran Partenaires : suggestions de partenaires et premier salut.

-- Suggestions : les personnes dont la langue natale est une langue que j'apprends, et qui
-- apprennent ma langue natale. On écarte les blocages dans les deux sens et les personnes
-- avec qui j'ai déjà une conversation. Les intérêts communs passent en premier.
-- La fonction renvoie l'âge, jamais la date de naissance, et la ville seulement si la
-- personne a choisi de la montrer.
create function public.partner_suggestions(max_results int default 50)
returns table (
    id uuid,
    first_name text,
    age int,
    city text,
    country_code char(2),
    time_zone text,
    is_verified boolean,
    last_seen_at timestamptz,
    native_language text,
    learning_language text,
    learning_level public.cefr_level,
    correction_preference public.correction_preference,
    interests text[],
    common_interests text[]
)
language sql stable security definer set search_path = '' as $$
    with candidates as (
        select distinct on (p.id)
            p.id,
            p.first_name,
            extract(year from age(p.birth_date))::int as age,
            case when p.show_city then p.city end as city,
            p.country_code,
            p.time_zone,
            p.is_verified,
            p.last_seen_at,
            their_native.language_code as native_language,
            their_learning.language_code as learning_language,
            their_learning.level as learning_level,
            p.correction_preference,
            p.interests,
            array(
                select unnest(p.interests)
                intersect
                select unnest(me.interests)
            ) as common_interests
        from public.profiles me
        join public.profiles p on p.id <> me.id
        join public.user_languages their_native
          on their_native.user_id = p.id and their_native.is_native
         and their_native.language_code in (
             select l.language_code from public.user_languages l
             where l.user_id = me.id and not l.is_native
         )
        join public.user_languages their_learning
          on their_learning.user_id = p.id and not their_learning.is_native
         and their_learning.language_code in (
             select l.language_code from public.user_languages l
             where l.user_id = me.id and l.is_native
         )
        where me.id = auth.uid()
          and not exists (
              select 1 from public.blocks b
              where (b.blocker_id = me.id and b.blocked_id = p.id)
                 or (b.blocker_id = p.id and b.blocked_id = me.id)
          )
          and not exists (
              select 1
              from public.conversation_members mine
              join public.conversation_members theirs on theirs.conversation_id = mine.conversation_id
              where mine.user_id = me.id and theirs.user_id = p.id
          )
        order by p.id, their_learning.level
    )
    select * from candidates
    order by cardinality(common_interests) desc, last_seen_at desc
    limit least(greatest(coalesce(max_results, 50), 1), 100);
$$;

revoke execute on function public.partner_suggestions(int) from public, anon;
grant execute on function public.partner_suggestions(int) to authenticated;

-- Saluer : ouvre la demande de discussion et y dépose le premier message, d'un seul coup.
-- Une seule conversation par paire de personnes, et 30 demandes au plus par jour.
create function public.send_greeting(partner uuid, greeting text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    conv uuid;
begin
    if auth.uid() is null or partner is null or partner = auth.uid() then
        raise exception 'invalid partner';
    end if;
    if greeting is null or char_length(btrim(greeting)) = 0 then
        raise exception 'empty greeting';
    end if;
    if not exists (select 1 from public.profiles where id = auth.uid())
       or not exists (select 1 from public.profiles where id = partner) then
        raise exception 'invalid partner';
    end if;
    if exists (
        select 1 from public.blocks b
        where (b.blocker_id = partner and b.blocked_id = auth.uid())
           or (b.blocker_id = auth.uid() and b.blocked_id = partner)
    ) then
        raise exception 'blocked';
    end if;
    if exists (
        select 1
        from public.conversation_members mine
        join public.conversation_members theirs on theirs.conversation_id = mine.conversation_id
        where mine.user_id = auth.uid() and theirs.user_id = partner
    ) then
        raise exception 'already greeted';
    end if;
    if (
        select count(*) from public.conversations c
        where c.requested_by = auth.uid() and c.created_at > now() - interval '1 day'
    ) >= 30 then
        raise exception 'too many greetings';
    end if;

    insert into public.conversations (requested_by) values (auth.uid()) returning id into conv;
    insert into public.conversation_members (conversation_id, user_id)
        values (conv, auth.uid()), (conv, partner);
    insert into public.messages (id, conversation_id, sender_id, kind, body)
        values (gen_random_uuid(), conv, auth.uid(), 'text', btrim(greeting));
    return conv;
end;
$$;

revoke execute on function public.send_greeting(uuid, text) from public, anon;
grant execute on function public.send_greeting(uuid, text) to authenticated;
