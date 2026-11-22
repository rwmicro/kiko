-- Appels audio entre deux partenaires (LiveKit), qui sonnent comme un vrai appel :
-- CallKit sur iPhone, notification d'appel plein écran et Telecom sur Android.
-- À exécuter après 20261121010000_privacy.sql. Le fichier peut être relancé sans erreur.
--
-- Le chemin d'un appel :
--   1. Martin touche « Appeler » : start_call crée l'appel (« ringing ») et met un push « call_ring »
--      pour Ayu dans la file d'envoi (push VoIP sur iPhone, FCM prioritaire sur Android) ;
--   2. le téléphone d'Ayu sonne ; elle décroche : answer_call (« active »), ou refuse : end_call ;
--   3. chacun demande un jeton à la fonction Edge « livekit-token » et rejoint la salle « call-<id> » ;
--   4. le premier qui raccroche appelle end_call (« ended »). Raccrocher avant la réponse envoie
--      « call_cancel » : le téléphone d'Ayu arrête de sonner.
--
-- Le moins de données possible :
--   - le son passe par LiveKit, chiffré en transit, sans jamais être enregistré ;
--   - la base garde qui a appelé qui, quand et combien de temps, 30 jours, puis l'oublie ;
--   - la file d'envoi efface le prénom de l'appelant dès que le push est parti (finish_push) ;
--   - un appel disparaît avec la discussion (suppression du compte, blocage qui la ferme).
--
-- Règles :
--   - on n'appelle que dans une discussion acceptée, et jamais quelqu'un qu'on a bloqué ou qui nous a bloqués ;
--   - pas de sonnerie entre 22 h et 8 h chez l'autre, sauf pour un appel prévu ensemble (± 15 min) ;
--   - 10 appels par heure au plus, pour que personne ne fasse sonner un téléphone en boucle ;
--   - un appel sans réponse au bout de 45 secondes est manqué.

---------------------------------------------------------------------------
-- 1. Jetons VoIP de l'iPhone (PushKit) : c'est eux qui font sonner CallKit, même app fermée
---------------------------------------------------------------------------
alter table public.push_devices drop constraint if exists push_devices_kind_check;
alter table public.push_devices
    add constraint push_devices_kind_check check (kind in ('alert', 'activity_start', 'activity_update', 'voip'));

---------------------------------------------------------------------------
-- 2. Les appels
---------------------------------------------------------------------------
create table if not exists public.calls (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations (id) on delete cascade,
    caller_id uuid references public.profiles (id) on delete set null,
    callee_id uuid references public.profiles (id) on delete set null,
    status text not null default 'ringing'
        check (status in ('ringing', 'active', 'declined', 'missed', 'cancelled', 'ended')),
    created_at timestamptz not null default now(),
    answered_at timestamptz,
    ended_at timestamptz
);
create index if not exists calls_conversation_idx on public.calls (conversation_id, created_at desc);
create index if not exists calls_caller_idx on public.calls (caller_id, created_at desc);
create index if not exists calls_live_idx on public.calls (status) where status in ('ringing', 'active');
-- Lue et écrite seulement par les fonctions ci-dessous.
alter table public.calls enable row level security;
revoke all on public.calls from public, anon, authenticated;

-- Durées : sonnerie, et appel resté ouvert (téléphone éteint en plein appel).
create or replace function public.call_ring_seconds()
returns int language sql immutable set search_path = '' as $$ select 45 $$;

-- Ce qui a trop sonné est manqué ; ce qui dure depuis 4 heures est fini.
create or replace function public.expire_calls()
returns void language sql security definer set search_path = '' as $$
    update public.calls set status = 'missed', ended_at = now()
    where status = 'ringing' and created_at < now() - make_interval(secs => public.call_ring_seconds());
    update public.calls set status = 'ended', ended_at = now()
    where status = 'active' and answered_at < now() - interval '4 hours';
$$;

-- Un appel prévu ensemble (proposition acceptée) commence dans moins de 15 minutes, ou est en cours.
create or replace function public.has_planned_call(conv uuid, p_now timestamptz default now())
returns boolean language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from (
            select public.safe_timestamptz(public.safe_jsonb(m.body) ->> 'accepted_at') as at,
                   coalesce((public.safe_jsonb(m.body) ->> 'minutes')::int, 15) as minutes
            from public.messages m
            where m.conversation_id = conv and m.kind = 'call_proposal'
              and m.created_at > p_now - interval '30 days'
              and public.safe_jsonb(m.body) ? 'accepted_at'
        ) p
        where p.at is not null
          and p_now between p.at - interval '15 minutes' and p.at + make_interval(mins => p.minutes + 15)
    );
$$;

---------------------------------------------------------------------------
-- 3. Côté app
---------------------------------------------------------------------------
-- Appeler : renvoie l'appel créé. Si l'autre appelle au même moment, on décroche son appel.
create or replace function public.start_call(p_conversation uuid)
returns table (call_id uuid, status text)
language plpgsql security definer set search_path = '' as $$
declare
    me uuid := auth.uid();
    other uuid;
    other_tz text;
    existing record;
    new_id uuid;
    my_name text;
begin
    if me is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    if not public.can_post_in(p_conversation) then
        raise exception 'call_not_allowed' using errcode = '42501';
    end if;
    select m.user_id, p.time_zone into other, other_tz
    from public.conversation_members m join public.profiles p on p.id = m.user_id
    where m.conversation_id = p_conversation and m.user_id <> me
    limit 1;
    if other is null then
        raise exception 'call_not_allowed' using errcode = '42501';
    end if;

    perform public.expire_calls();

    select c.id, c.status, c.caller_id into existing
    from public.calls c
    where c.conversation_id = p_conversation and c.status in ('ringing', 'active')
    order by c.created_at desc
    limit 1;
    if existing.id is not null then
        if existing.status = 'ringing' and existing.caller_id = other then
            update public.calls c set status = 'active', answered_at = now() where c.id = existing.id;
            return query select existing.id, 'active'::text;
        else
            return query select existing.id, existing.status;
        end if;
        return;
    end if;

    if public.is_quiet_for(other_tz) and not public.has_planned_call(p_conversation) then
        raise exception 'partner_asleep' using errcode = 'P0001';
    end if;
    if (select count(*) from public.calls c where c.caller_id = me and c.created_at > now() - interval '1 hour') >= 10 then
        raise exception 'too_many_calls' using errcode = 'P0001';
    end if;

    insert into public.calls (conversation_id, caller_id, callee_id)
    values (p_conversation, me, other)
    returning id into new_id;

    select p.first_name into my_name from public.profiles p where p.id = me;
    insert into public.push_outbox (user_id, kind, payload)
    values (other, 'call_ring', jsonb_build_object(
        'call_id', new_id,
        'conversation_id', p_conversation,
        'caller_id', me,
        'caller_name', coalesce(my_name, 'Quelqu''un')
    ));
    return query select new_id, 'ringing'::text;
end;
$$;

-- Décrocher : seulement la personne appelée, et seulement pendant que ça sonne.
create or replace function public.answer_call(p_call uuid)
returns text language plpgsql security definer set search_path = '' as $$
declare
    result text;
begin
    perform public.expire_calls();
    update public.calls c set status = 'active', answered_at = now()
    where c.id = p_call and c.callee_id = auth.uid() and c.status = 'ringing'
    returning c.status into result;
    if result is null then
        select c.status into result from public.calls c
        where c.id = p_call and auth.uid() in (c.caller_id, c.callee_id);
    end if;
    if result is null then
        raise exception 'call_not_found' using errcode = '42501';
    end if;
    return result;
end;
$$;

-- Raccrocher, refuser ou annuler. Annuler avant la réponse fait taire le téléphone de l'autre.
create or replace function public.end_call(p_call uuid)
returns text language plpgsql security definer set search_path = '' as $$
declare
    c record;
    next_status text;
begin
    select x.id, x.status, x.caller_id, x.callee_id, x.conversation_id, x.created_at into c
    from public.calls x
    where x.id = p_call and auth.uid() in (x.caller_id, x.callee_id)
    for update;
    if c.id is null then
        raise exception 'call_not_found' using errcode = '42501';
    end if;
    if c.status not in ('ringing', 'active') then
        return c.status;
    end if;
    next_status := case
        when c.status = 'active' then 'ended'
        when c.created_at < now() - make_interval(secs => public.call_ring_seconds()) then 'missed'
        when auth.uid() = c.caller_id then 'cancelled'
        else 'declined'
    end;
    update public.calls x set status = next_status, ended_at = now() where x.id = c.id;
    -- Le téléphone appelé sonne encore : on lui dit d'arrêter (et l'iPhone note l'appel manqué).
    if c.status = 'ringing' and auth.uid() = c.caller_id and c.callee_id is not null then
        insert into public.push_outbox (user_id, kind, payload)
        values (c.callee_id, 'call_cancel', jsonb_build_object('call_id', c.id, 'conversation_id', c.conversation_id));
    end if;
    return next_status;
end;
$$;

-- Où en est un appel : l'app qui appelle regarde si l'autre a décroché ou refusé.
create or replace function public.call_state(p_call uuid)
returns table (status text, caller boolean, answered_at timestamptz, conversation_id uuid)
language plpgsql security definer set search_path = '' as $$
begin
    perform public.expire_calls();
    return query
    select c.status, c.caller_id = auth.uid(), c.answered_at, c.conversation_id
    from public.calls c
    where c.id = p_call and auth.uid() in (c.caller_id, c.callee_id);
end;
$$;

---------------------------------------------------------------------------
-- 4. Côté fonction Edge « livekit-token » (rôle service_role uniquement)
---------------------------------------------------------------------------
-- La salle LiveKit d'un appel, si cette personne peut y entrer maintenant :
-- l'appelant dès que ça sonne, la personne appelée une fois qu'elle a décroché.
create or replace function public.call_room_for(p_call uuid, p_user uuid)
returns text language plpgsql security definer set search_path = '' as $$
declare
    c record;
begin
    perform public.expire_calls();
    select x.id, x.status, x.caller_id, x.callee_id into c from public.calls x where x.id = p_call;
    if c.id is null then
        return null;
    end if;
    if (c.status = 'active' and p_user in (c.caller_id, c.callee_id))
       or (c.status = 'ringing' and p_user = c.caller_id) then
        return 'call-' || c.id;
    end if;
    return null;
end;
$$;

---------------------------------------------------------------------------
-- 5. Droits
---------------------------------------------------------------------------
do $$
declare
    f text;
begin
    foreach f in array array[
        'public.start_call(uuid)', 'public.answer_call(uuid)', 'public.end_call(uuid)', 'public.call_state(uuid)'
    ] loop
        execute format('revoke execute on function %s from public, anon', f);
        execute format('grant execute on function %s to authenticated', f);
    end loop;
    foreach f in array array[
        'public.call_room_for(uuid, uuid)', 'public.expire_calls()', 'public.has_planned_call(uuid, timestamptz)',
        'public.call_ring_seconds()'
    ] loop
        execute format('revoke execute on function %s from public, anon, authenticated', f);
        execute format('grant execute on function %s to service_role', f);
    end loop;
end;
$$;

---------------------------------------------------------------------------
-- 6. Ménage de nuit : 30 jours d'historique au plus
---------------------------------------------------------------------------
create or replace function public.purge_calls()
returns void language sql security definer set search_path = '' as $$
    select public.expire_calls();
    delete from public.calls where created_at < now() - interval '30 days';
$$;

revoke execute on function public.purge_calls() from public, anon, authenticated;

do $$
begin
    perform cron.schedule('kiko-calls-cleanup', '47 3 * * *', 'select public.purge_calls()');
exception when others then
    raise notice 'pg_cron indisponible (base locale ?) : %', sqlerrm;
end;
$$;
