-- Notifications, widgets et activités en direct (maquettes 16 à 37 de la tâche design).
--
-- Le chemin d'une notification :
--   1. un message arrive, ou la tâche planifiée trouve une relance à faire ;
--   2. une ligne entre dans push_outbox (la file d'envoi) ;
--   3. la base réveille la fonction Edge « push », qui envoie par FCM (Android) et APNs (iPhone).
--
-- Règles des maquettes : une personne d'abord, une relance par jour au plus, rien entre 22 h et
-- 8 h à l'heure de la personne (un message de nuit arrive sans son), et les relances s'arrêtent
-- après 14 jours sans retour.
--
-- Le moins de données possible : on ne mesure pas les ouvertures, le contenu d'une notification
-- est effacé dès qu'elle est partie, la file ne garde que 30 jours de « quoi et quand » (pour ne
-- pas envoyer deux fois la même relance), et un jeton de téléphone inactif part après 90 jours.
--
-- Le fichier peut être relancé sans erreur. Hors Supabase (base de test locale), pg_net, pg_cron
-- et Vault manquent : les blocs qui en ont besoin passent sans rien faire.

---------------------------------------------------------------------------
-- 0. Outils
---------------------------------------------------------------------------
-- Le corps des messages spéciaux est du JSON écrit par l'app : on le lit sans jamais échouer.
create or replace function public.safe_jsonb(t text)
returns jsonb language plpgsql immutable set search_path = '' as $$
begin
    return t::jsonb;
exception when others then
    return null;
end;
$$;

create or replace function public.safe_timestamptz(t text)
returns timestamptz language plpgsql stable set search_path = '' as $$
begin
    return t::timestamptz;
exception when others then
    return null;
end;
$$;

-- Fuseau d'une personne, UTC si le nom est inconnu.
create or replace function public.safe_time_zone(tz text)
returns text language plpgsql stable set search_path = '' as $$
begin
    perform pg_catalog.timezone(tz, now());
    return tz;
exception when others then
    return 'UTC';
end;
$$;

-- La série à deux sans contrôle d'appartenance, pour la base elle-même (relances, étapes).
create or replace function public.streak_days(conv uuid)
returns int language sql stable security definer set search_path = '' as $$
    with days as (
        select (m.created_at at time zone 'UTC')::date as d
        from public.messages m
        where m.conversation_id = conv
        group by 1
        having count(distinct m.sender_id) >= 2
    ),
    ranked as (
        select d, d + (row_number() over (order by d desc))::int - 1 as anchor from days
    )
    select case
        when (select max(d) from days) is null
          or (select max(d) from days) < (now() at time zone 'UTC')::date - 1 then 0
        else (select count(*)::int from ranked where anchor = (select max(d) from days))
    end;
$$;

revoke execute on function public.streak_days(uuid) from public, anon, authenticated;

---------------------------------------------------------------------------
-- 1. Appareils : un jeton par téléphone, et ceux des activités en direct
---------------------------------------------------------------------------
-- kind = 'alert' : notifications ordinaires (FCM sur Android, APNs sur iPhone).
-- kind = 'activity_start' : jeton « push-to-start » d'iOS 17.2+, activity = type ('call', 'streak').
-- kind = 'activity_update' : jeton d'une activité lancée, activity = 'call:<message>' ou 'streak:<conversation>'.
create table if not exists public.push_devices (
    token text primary key check (char_length(token) between 8 and 4096),
    user_id uuid not null references public.profiles (id) on delete cascade,
    platform text not null check (platform in ('android', 'ios')),
    kind text not null default 'alert' check (kind in ('alert', 'activity_start', 'activity_update')),
    activity text check (char_length(activity) <= 80),
    sandbox boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists push_devices_user_idx on public.push_devices (user_id);
alter table public.push_devices enable row level security;
revoke all on public.push_devices from public, anon, authenticated;
grant select, delete on public.push_devices to service_role;

-- Un jeton change de main si quelqu'un d'autre se connecte sur le même téléphone.
create or replace function public.register_push_device(
    p_token text, p_platform text, p_kind text default 'alert', p_activity text default null, p_sandbox boolean default false
)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then
        raise exception 'not signed in';
    end if;
    insert into public.push_devices (token, user_id, platform, kind, activity, sandbox)
    values (p_token, auth.uid(), p_platform, coalesce(p_kind, 'alert'), p_activity, coalesce(p_sandbox, false))
    on conflict (token) do update set
        user_id = excluded.user_id, platform = excluded.platform, kind = excluded.kind,
        activity = excluded.activity, sandbox = excluded.sandbox, updated_at = now();
    -- Pas plus de 30 jetons par personne : les plus anciens partent.
    delete from public.push_devices d
    where d.user_id = auth.uid()
      and d.token in (
          select o.token from public.push_devices o where o.user_id = auth.uid()
          order by o.updated_at desc offset 30
      );
end;
$$;

create or replace function public.unregister_push_device(p_token text)
returns void language sql security definer set search_path = '' as $$
    delete from public.push_devices where token = p_token and user_id = auth.uid();
$$;

revoke execute on function public.register_push_device(text, text, text, text, boolean) from public, anon;
revoke execute on function public.unregister_push_device(text) from public, anon;
grant execute on function public.register_push_device(text, text, text, text, boolean) to authenticated;
grant execute on function public.unregister_push_device(text) to authenticated;

---------------------------------------------------------------------------
-- 2. Préférences : messages et corrections d'un côté, relances de l'autre
---------------------------------------------------------------------------
create table if not exists public.notification_prefs (
    user_id uuid primary key references public.profiles (id) on delete cascade,
    messages boolean not null default true,
    reminders boolean not null default true,
    updated_at timestamptz not null default now()
);
alter table public.notification_prefs enable row level security;
revoke all on public.notification_prefs from public, anon, authenticated;

create or replace function public.my_notification_prefs()
returns table (messages boolean, reminders boolean)
language sql stable security definer set search_path = '' as $$
    select coalesce(p.messages, true), coalesce(p.reminders, true)
    from (select auth.uid() as id) me
    left join public.notification_prefs p on p.user_id = me.id
    where me.id is not null;
$$;

create or replace function public.set_notification_prefs(p_messages boolean, p_reminders boolean)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then
        raise exception 'not signed in';
    end if;
    insert into public.notification_prefs (user_id, messages, reminders)
    values (auth.uid(), p_messages, p_reminders)
    on conflict (user_id) do update set messages = excluded.messages, reminders = excluded.reminders, updated_at = now();
end;
$$;

revoke execute on function public.my_notification_prefs() from public, anon;
revoke execute on function public.set_notification_prefs(boolean, boolean) from public, anon;
grant execute on function public.my_notification_prefs() to authenticated;
grant execute on function public.set_notification_prefs(boolean, boolean) to authenticated;

---------------------------------------------------------------------------
-- 3. File d'envoi
---------------------------------------------------------------------------
-- kind : message, request, correction, call, call_accepted, milestone (série de 7, 30, 100, 365 jours),
-- call_soon (appel dans moins de 15 min), call_end, activity_end, streak_risk, anniversary, reengage.
-- template : la variante de texte d'une relance, pour ne pas répéter la même à la même personne.
-- payload : le contenu (prénom, aperçu du message) ; vidé dès l'envoi par finish_push.
-- dedupe_key : la même relance ne part jamais deux fois.
create table if not exists public.push_outbox (
    id bigint generated always as identity primary key,
    user_id uuid not null references public.profiles (id) on delete cascade,
    kind text not null,
    template text,
    dedupe_key text unique,
    payload jsonb not null default '{}',
    quiet boolean not null default false,
    created_at timestamptz not null default now(),
    claimed_at timestamptz,
    sent_at timestamptz,
    error text
);
create index if not exists push_outbox_pending_idx on public.push_outbox (id) where sent_at is null;
create index if not exists push_outbox_user_idx on public.push_outbox (user_id, created_at desc);
-- Une ancienne version mesurait les ouvertures : la colonne disparaît.
alter table public.push_outbox drop column if exists opened_at;
alter table public.push_outbox enable row level security;
revoke all on public.push_outbox from public, anon, authenticated;
grant select, insert on public.push_outbox to service_role;

-- Les relances comptent pour « une par jour au plus » ; les messages et les étapes non.
create or replace function public.is_reminder_kind(k text)
returns boolean language sql immutable set search_path = '' as $$
    select k in ('streak_risk', 'anniversary', 'reengage');
$$;

-- Nuit chez la personne : de 22 h à 8 h.
create or replace function public.is_quiet_for(tz text, at_time timestamptz default now())
returns boolean language sql stable set search_path = '' as $$
    select extract(hour from at_time at time zone public.safe_time_zone(tz)) not between 8 and 21;
$$;

-- Le texte d'aperçu d'un message, selon sa nature.
create or replace function public.push_preview(k public.message_kind, body text)
returns text language sql immutable set search_path = '' as $$
    select case k
        when 'photo' then '📷 Photo'
        when 'voice' then '🎤 Message vocal'
        when 'call_proposal' then '📞 Proposition d''appel'
        when 'correction' then '✏️ ' || coalesce(left(public.safe_jsonb(body) ->> 'corrected', 300), 'Correction')
        else left(coalesce(body, ''), 300)
    end;
$$;

---------------------------------------------------------------------------
-- 4. Un message → une notification pour l'autre
---------------------------------------------------------------------------
create or replace function public.queue_message_push()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    conv record;
    sender_name text;
    data jsonb;
    k text;
    n int;
begin
    if new.sender_id is null then
        return new;
    end if;
    select c.status, c.requested_by into conv from public.conversations c where c.id = new.conversation_id;
    select p.first_name into sender_name from public.profiles p where p.id = new.sender_id;

    data := jsonb_build_object(
        'conversation_id', new.conversation_id,
        'message_id', new.id,
        'sender_id', new.sender_id,
        'sender_name', coalesce(sender_name, 'Quelqu''un'),
        'message_kind', new.kind,
        'preview', public.push_preview(new.kind, new.body)
    );
    k := case new.kind
        when 'correction' then 'correction'
        when 'call_proposal' then
            case when public.safe_jsonb(new.body) ? 'accepted_at' then 'call_accepted' else 'call' end
        else 'message'
    end;
    if conv.status = 'requested' then
        k := 'request';
    end if;
    if k = 'correction' then
        data := data || jsonb_build_object(
            'original', left(public.safe_jsonb(new.body) ->> 'original', 500),
            'corrected', left(public.safe_jsonb(new.body) ->> 'corrected', 500),
            'note', left(public.safe_jsonb(new.body) ->> 'note', 300)
        );
    elsif k in ('call', 'call_accepted') then
        data := data || jsonb_build_object(
            'at', coalesce(public.safe_jsonb(new.body) ->> 'accepted_at', public.safe_jsonb(new.body) ->> 'at'),
            'minutes', public.safe_jsonb(new.body) -> 'minutes'
        );
    end if;

    insert into public.push_outbox (user_id, kind, payload, quiet)
    select m.user_id, k, data, public.is_quiet_for(p.time_zone)
    from public.conversation_members m
    join public.profiles p on p.id = m.user_id
    left join public.notification_prefs np on np.user_id = m.user_id
    where m.conversation_id = new.conversation_id
      and m.user_id <> new.sender_id
      and conv.status <> 'declined'
      and coalesce(np.messages, true)
      and not exists (
          select 1 from public.blocks b
          where (b.blocker_id = m.user_id and b.blocked_id = new.sender_id)
             or (b.blocker_id = new.sender_id and b.blocked_id = m.user_id)
      );

    -- Écrire sauve la série : l'activité « série en danger » de l'expéditeur se ferme.
    if exists (
        select 1 from public.push_devices d
        where d.user_id = new.sender_id and d.kind = 'activity_update'
          and d.activity = 'streak:' || new.conversation_id
    ) then
        insert into public.push_outbox (user_id, kind, payload)
        values (new.sender_id, 'activity_end', jsonb_build_object('activity', 'streak:' || new.conversation_id));
    end if;

    -- Une étape de série (7, 30, 100, 365 jours) se fête à deux, le jour où elle est atteinte.
    if conv.status = 'accepted' and (
        select count(distinct m.sender_id) from public.messages m
        where m.conversation_id = new.conversation_id
          and (m.created_at at time zone 'UTC')::date = (new.created_at at time zone 'UTC')::date
    ) >= 2 then
        n := public.streak_days(new.conversation_id);
        if n in (7, 30, 100, 365) then
            insert into public.push_outbox (user_id, kind, dedupe_key, payload, quiet)
            select m.user_id, 'milestone',
                   'milestone:' || new.conversation_id || ':' || n || ':' || m.user_id,
                   jsonb_build_object(
                       'conversation_id', new.conversation_id,
                       'streak', n,
                       'partner_name', (select pp.first_name from public.conversation_members o
                                        join public.profiles pp on pp.id = o.user_id
                                        where o.conversation_id = new.conversation_id and o.user_id <> m.user_id limit 1)
                   ),
                   public.is_quiet_for(p.time_zone)
            from public.conversation_members m
            join public.profiles p on p.id = m.user_id
            where m.conversation_id = new.conversation_id
            on conflict (dedupe_key) do nothing;
        end if;
    end if;
    return new;
end;
$$;

revoke execute on function public.queue_message_push() from public, anon, authenticated;

drop trigger if exists messages_queue_push on public.messages;
create trigger messages_queue_push
    after insert on public.messages
    for each row execute function public.queue_message_push();

---------------------------------------------------------------------------
-- 5. Réveiller la fonction d'envoi
---------------------------------------------------------------------------
-- Deux valeurs dans Vault : « project_url » (à créer une fois, voir supabase/functions/README.md)
-- et « push_secret » (créé ici, partagé avec les fonctions qui le vérifient par check_push_secret).
do $$
begin
    if not exists (select 1 from vault.secrets where name = 'push_secret') then
        perform vault.create_secret(
            replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''),
            'push_secret',
            'Entre la base et les fonctions Edge push et reminders'
        );
    end if;
exception when others then
    raise notice 'Vault indisponible (base locale ?) : %', sqlerrm;
end;
$$;

create or replace function public.call_edge_function(fn text)
returns void language plpgsql security definer set search_path = '' as $$
declare
    base text;
    secret text;
begin
    execute 'select decrypted_secret from vault.decrypted_secrets where name = ''project_url''' into base;
    execute 'select decrypted_secret from vault.decrypted_secrets where name = ''push_secret''' into secret;
    if base is null or secret is null then
        return;
    end if;
    execute 'select net.http_post(url := $1, headers := $2, body := ''{}''::jsonb, timeout_milliseconds := 8000)'
    using rtrim(base, '/') || '/functions/v1/' || fn,
          jsonb_build_object('Content-Type', 'application/json', 'x-kiko-push-secret', secret);
exception when others then
    -- Un envoi raté ne doit jamais empêcher un message d'être enregistré : la tâche planifiée rattrape.
    raise notice 'call_edge_function(%) : %', fn, sqlerrm;
end;
$$;

revoke execute on function public.call_edge_function(text) from public, anon, authenticated;

create or replace function public.poke_push()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    perform public.call_edge_function('push');
    return null;
end;
$$;

revoke execute on function public.poke_push() from public, anon, authenticated;

drop trigger if exists push_outbox_poke on public.push_outbox;
create trigger push_outbox_poke
    after insert on public.push_outbox
    for each statement execute function public.poke_push();

---------------------------------------------------------------------------
-- 6. Côté fonctions Edge (rôle service_role uniquement)
---------------------------------------------------------------------------
create or replace function public.check_push_secret(candidate text)
returns boolean language plpgsql stable security definer set search_path = '' as $$
declare
    secret text;
begin
    execute 'select decrypted_secret from vault.decrypted_secrets where name = ''push_secret''' into secret;
    return secret is not null and candidate = secret;
exception when others then
    return false;
end;
$$;

-- Prend un lot de notifications à envoyer, avec le fuseau et les jetons de chaque destinataire.
-- Une ligne prise depuis plus de 2 minutes sans être finie est reprise.
create or replace function public.claim_push_batch(max_rows int default 200)
returns table (id bigint, user_id uuid, kind text, template text, payload jsonb, quiet boolean, time_zone text, devices jsonb)
language plpgsql security definer set search_path = '' as $$
begin
    return query
    with picked as (
        select o.id from public.push_outbox o
        where o.sent_at is null
          and (o.claimed_at is null or o.claimed_at < now() - interval '2 minutes')
          and o.created_at > now() - interval '6 hours'
        order by o.id
        limit max_rows
        for update skip locked
    ),
    claimed as (
        update public.push_outbox o set claimed_at = now()
        from picked where o.id = picked.id
        returning o.id, o.user_id, o.kind, o.template, o.payload, o.quiet
    )
    select c.id, c.user_id, c.kind, c.template, c.payload, c.quiet,
           (select public.safe_time_zone(p.time_zone) from public.profiles p where p.id = c.user_id),
           coalesce((select jsonb_agg(jsonb_build_object(
                         'token', d.token, 'platform', d.platform, 'kind', d.kind,
                         'activity', d.activity, 'sandbox', d.sandbox))
                     from public.push_devices d where d.user_id = c.user_id), '[]'::jsonb)
    from claimed c
    order by c.id;
end;
$$;

-- Une fois partie, la notification ne garde plus son contenu : seulement sa nature et sa date.
create or replace function public.finish_push(p_id bigint, p_error text default null)
returns void language sql security definer set search_path = '' as $$
    update public.push_outbox
       set sent_at = now(), error = left(p_error, 500), payload = '{}'::jsonb
     where id = p_id;
$$;

create or replace function public.drop_push_token(p_token text)
returns void language sql security definer set search_path = '' as $$
    delete from public.push_devices where token = p_token;
$$;

-- Qui doit recevoir une relance maintenant, et pourquoi. Une ligne par personne au plus :
-- série en danger d'abord, puis anniversaire d'amitié, puis relance après une absence.
create or replace function public.reminder_candidates(p_now timestamptz default now())
returns table (
    user_id uuid,
    kind text,
    dedupe_key text,
    conversation_id uuid,
    first_name text,
    partner_name text,
    partner_night boolean,
    partner_waiting boolean,
    streak int,
    minutes_left int,
    days_inactive int,
    months int,
    online_partners int
)
language sql stable security definer set search_path = '' as $$
    with people as (
        select p.id, p.first_name, public.safe_time_zone(p.time_zone) as tz, p.last_seen_at,
               (p_now at time zone public.safe_time_zone(p.time_zone)) as local_now
        from public.profiles p
        left join public.notification_prefs np on np.user_id = p.id
        where coalesce(np.reminders, true)
          and exists (select 1 from public.push_devices d where d.user_id = p.id and d.kind = 'alert')
    ),
    awake as (
        select pe.* from people pe
        where extract(hour from pe.local_now) between 8 and 21
          -- Une relance par jour au plus, comptée à l'heure de la personne.
          and not exists (
              select 1 from public.push_outbox o
              where o.user_id = pe.id and public.is_reminder_kind(o.kind)
                and (o.created_at at time zone pe.tz)::date = pe.local_now::date
          )
    ),
    convs as (
        select a.id as user_id, c.id as conv, c.created_at as conv_created,
               other.user_id as partner_id, pp.first_name as partner_name,
               public.is_quiet_for(pp.time_zone, p_now) as partner_night,
               pp.last_seen_at as partner_seen,
               (select m.sender_id from public.messages m where m.conversation_id = c.id
                 order by m.created_at desc limit 1) as last_sender
        from awake a
        join public.conversation_members me on me.user_id = a.id
        join public.conversations c on c.id = me.conversation_id and c.status = 'accepted'
        join public.conversation_members other on other.conversation_id = c.id and other.user_id <> a.id
        join public.profiles pp on pp.id = other.user_id
        where not exists (
            select 1 from public.blocks b
            where (b.blocker_id = a.id and b.blocked_id = other.user_id)
               or (b.blocker_id = other.user_id and b.blocked_id = a.id)
        )
    ),
    -- La série tient encore (dernier jour commun hier) et l'autre a déjà écrit aujourd'hui (UTC).
    streak_risk as (
        select distinct on (cv.user_id)
               cv.user_id, cv.conv, cv.partner_name, cv.partner_night,
               public.streak_days(cv.conv) as streak,
               (extract(epoch from (date_trunc('day', p_now at time zone 'UTC') + interval '1 day')
                                   - (p_now at time zone 'UTC')) / 60)::int as minutes_left
        from convs cv
        join awake a on a.id = cv.user_id
        where extract(hour from a.local_now) between 18 and 21
          and exists (select 1 from public.messages m where m.conversation_id = cv.conv
                        and m.sender_id = cv.partner_id
                        and (m.created_at at time zone 'UTC')::date = (p_now at time zone 'UTC')::date)
          and not exists (select 1 from public.messages m where m.conversation_id = cv.conv
                            and m.sender_id = cv.user_id
                            and (m.created_at at time zone 'UTC')::date = (p_now at time zone 'UTC')::date)
          and public.streak_days(cv.conv) >= 2
        order by cv.user_id, public.streak_days(cv.conv) desc
    ),
    -- Un mois, puis chaque année, après l'acceptation de la demande.
    anniversary as (
        select distinct on (cv.user_id)
               cv.user_id, cv.conv, cv.partner_name, cv.partner_night,
               (case when age(a.local_now::date, (cv.conv_created at time zone a.tz)::date) = interval '1 month' then 1
                     else 12 * extract(year from age(a.local_now::date, (cv.conv_created at time zone a.tz)::date))::int end) as months
        from convs cv
        join awake a on a.id = cv.user_id
        where extract(hour from a.local_now) between 10 and 20
          and (age(a.local_now::date, (cv.conv_created at time zone a.tz)::date) = interval '1 month'
               or (extract(month from age(a.local_now::date, (cv.conv_created at time zone a.tz)::date)) = 0
                   and extract(day from age(a.local_now::date, (cv.conv_created at time zone a.tz)::date)) = 0
                   and extract(year from age(a.local_now::date, (cv.conv_created at time zone a.tz)::date)) >= 1))
        order by cv.user_id, cv.conv_created
    ),
    -- Absence : jours 1, 2, 3, 7 et 14 à 19 h, puis plus rien.
    absent as (
        select a.id as user_id,
               (a.local_now::date - (a.last_seen_at at time zone a.tz)::date) as days
        from awake a
        where extract(hour from a.local_now) = 19
    ),
    reengage as (
        select ab.user_id, ab.days,
               (select cv.conv from convs cv where cv.user_id = ab.user_id
                 order by (cv.last_sender = cv.partner_id) desc, cv.partner_seen desc nulls last limit 1) as conv
        from absent ab
        where ab.days in (1, 2, 3, 7, 14)
    ),
    picked as (
        select s.user_id, 'streak_risk'::text as kind,
               'streak_risk:' || s.conv || ':' || (p_now at time zone 'UTC')::date || ':' || s.user_id as dedupe_key,
               s.conv, s.streak, s.minutes_left, null::int as days, null::int as months, 1 as rank
        from streak_risk s
        union all
        select an.user_id, 'anniversary', 'anniversary:' || an.conv || ':' || an.months || ':' || an.user_id,
               an.conv, null, null, null, an.months, 2
        from anniversary an
        union all
        select r.user_id, 'reengage',
               'reengage:' || r.user_id || ':' || (p_now at time zone 'UTC')::date,
               r.conv, null, null, r.days, null, 3
        from reengage r
    ),
    best as (
        select distinct on (pk.user_id) pk.* from picked pk order by pk.user_id, pk.rank
    )
    select b.user_id, b.kind, b.dedupe_key, b.conv,
           a.first_name, cv.partner_name, coalesce(cv.partner_night, false),
           coalesce(cv.last_sender = cv.partner_id, false),
           coalesce(b.streak, case when b.conv is not null then public.streak_days(b.conv) end),
           b.minutes_left, b.days, b.months,
           (select count(*)::int from convs o where o.user_id = b.user_id
              and o.partner_seen > p_now - interval '10 minutes')
    from best b
    join awake a on a.id = b.user_id
    left join convs cv on cv.user_id = b.user_id and cv.conv = b.conv;
$$;

-- Appels acceptés qui commencent dans moins de 15 minutes (call_soon), ou finis depuis peu (call_end).
create or replace function public.due_call_alerts(p_now timestamptz default now())
returns table (user_id uuid, kind text, dedupe_key text, payload jsonb)
language sql stable security definer set search_path = '' as $$
    with accepted as (
        select m.id, m.conversation_id, m.reply_to_id,
               public.safe_timestamptz(public.safe_jsonb(m.body) ->> 'accepted_at') as at,
               coalesce((public.safe_jsonb(m.body) ->> 'minutes')::int, 15) as minutes
        from public.messages m
        where m.kind = 'call_proposal'
          and m.created_at > p_now - interval '30 days'
          and public.safe_jsonb(m.body) ? 'accepted_at'
    ),
    calls as (
        select a.*, mem.user_id, pp.first_name as partner_name, public.safe_time_zone(pp.time_zone) as partner_tz,
               public.safe_time_zone(me.time_zone) as my_tz
        from accepted a
        join public.conversations c on c.id = a.conversation_id and c.status = 'accepted'
        join public.conversation_members mem on mem.conversation_id = a.conversation_id
        join public.profiles me on me.id = mem.user_id
        join public.conversation_members o on o.conversation_id = a.conversation_id and o.user_id <> mem.user_id
        join public.profiles pp on pp.id = o.user_id
        where a.at is not null
    )
    select c.user_id, 'call_soon', 'call_soon:' || c.id || ':' || c.user_id,
           jsonb_build_object('call_id', c.id, 'conversation_id', c.conversation_id, 'partner_name', c.partner_name,
                              'at', c.at, 'minutes', c.minutes, 'partner_time_zone', c.partner_tz, 'time_zone', c.my_tz)
    from calls c
    where c.at > p_now and c.at <= p_now + interval '15 minutes'
    union all
    select c.user_id, 'call_end', 'call_end:' || c.id || ':' || c.user_id,
           jsonb_build_object('activity', 'call:' || c.id)
    from calls c
    where c.at + make_interval(mins => c.minutes) <= p_now
      and c.at + make_interval(mins => c.minutes) > p_now - interval '1 hour'
      and exists (select 1 from public.push_devices d where d.user_id = c.user_id
                    and d.kind = 'activity_update' and d.activity = 'call:' || c.id);
$$;

-- Ce que chaque personne a reçu comme relance ces 30 derniers jours : la variante et la date, pour ne pas radoter.
create or replace function public.reminder_history(users uuid[])
returns table (user_id uuid, template text, sent_at timestamptz)
language sql stable security definer set search_path = '' as $$
    select o.user_id, o.template, max(o.created_at)
    from public.push_outbox o
    where o.user_id = any(users) and o.template is not null and o.created_at > now() - interval '30 days'
    group by o.user_id, o.template;
$$;

drop function if exists public.template_stats();

do $$
declare
    f text;
begin
    foreach f in array array[
        'public.check_push_secret(text)', 'public.claim_push_batch(int)', 'public.finish_push(bigint, text)',
        'public.drop_push_token(text)', 'public.reminder_candidates(timestamptz)', 'public.due_call_alerts(timestamptz)',
        'public.reminder_history(uuid[])'
    ] loop
        execute format('revoke execute on function %s from public, anon, authenticated', f);
        execute format('grant execute on function %s to service_role', f);
    end loop;
end;
$$;

---------------------------------------------------------------------------
-- 7. Côté app : appels à venir (widgets, activités en direct)
---------------------------------------------------------------------------
drop function if exists public.push_opened(bigint);

-- Mes appels acceptés à venir, ou en cours, le plus proche en premier.
create or replace function public.my_upcoming_calls()
returns table (
    call_id uuid, conversation_id uuid, partner_id uuid, partner_first_name text, partner_time_zone text,
    starts_at timestamptz, minutes int
)
language sql stable security definer set search_path = '' as $$
    select x.id, x.conversation_id, x.partner_id, x.first_name, x.time_zone, x.at, x.minutes
    from (
        select m.id, m.conversation_id, pp.id as partner_id, pp.first_name, pp.time_zone,
               public.safe_timestamptz(public.safe_jsonb(m.body) ->> 'accepted_at') as at,
               coalesce((public.safe_jsonb(m.body) ->> 'minutes')::int, 15) as minutes
        from public.conversation_members me
        join public.conversations c on c.id = me.conversation_id and c.status = 'accepted'
        join public.messages m on m.conversation_id = c.id and m.kind = 'call_proposal'
        join public.conversation_members o on o.conversation_id = c.id and o.user_id <> me.user_id
        join public.profiles pp on pp.id = o.user_id
        where me.user_id = auth.uid()
          and m.created_at > now() - interval '30 days'
          and public.safe_jsonb(m.body) ? 'accepted_at'
    ) x
    where x.at is not null and x.at + make_interval(mins => x.minutes) > now()
    order by x.at
    limit 10;
$$;

revoke execute on function public.my_upcoming_calls() from public, anon;
grant execute on function public.my_upcoming_calls() to authenticated;

---------------------------------------------------------------------------
-- 8. Tâches planifiées : relances toutes les 5 minutes, rattrapage des envois chaque minute
---------------------------------------------------------------------------
create or replace function public.run_reminders()
returns void language sql security definer set search_path = '' as $$
    select public.call_edge_function('reminders');
$$;

create or replace function public.run_push_retry()
returns void language plpgsql security definer set search_path = '' as $$
begin
    if exists (select 1 from public.push_outbox o where o.sent_at is null and o.created_at > now() - interval '6 hours') then
        perform public.call_edge_function('push');
    end if;
end;
$$;

-- Ménage : 30 jours de file au plus, aucun contenu dans une notification jamais partie,
-- et les jetons des téléphones qui n'ont pas ouvert l'app depuis 90 jours.
create or replace function public.purge_push_data()
returns void language sql security definer set search_path = '' as $$
    delete from public.push_outbox where created_at < now() - interval '30 days';
    update public.push_outbox set payload = '{}'::jsonb
     where sent_at is null and created_at < now() - interval '6 hours' and payload <> '{}'::jsonb;
    delete from public.push_devices where updated_at < now() - interval '90 days';
$$;

revoke execute on function public.purge_push_data() from public, anon, authenticated;
revoke execute on function public.run_reminders() from public, anon, authenticated;
revoke execute on function public.run_push_retry() from public, anon, authenticated;

do $$
begin
    create extension if not exists pg_net with schema extensions;
    create extension if not exists pg_cron;
    perform cron.schedule('kiko-reminders', '*/5 * * * *', 'select public.run_reminders()');
    perform cron.schedule('kiko-push-retry', '* * * * *', 'select public.run_push_retry()');
    -- Ménage chaque nuit (voir purge_push_data).
    perform cron.schedule('kiko-push-cleanup', '17 3 * * *', 'select public.purge_push_data()');
exception when others then
    raise notice 'pg_net ou pg_cron indisponible (base locale ?) : %', sqlerrm;
end;
$$;
