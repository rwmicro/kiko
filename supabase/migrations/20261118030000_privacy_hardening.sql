-- Durcissement après relecture.

-- 1. Profils : un compte connecté lisait toutes les colonnes de tous les profils, dont la
--    date de naissance et la ville cachée. On ne laisse lire que les colonnes publiques.
--    L'âge et la ville (quand elle est montrée) passent par partner_suggestions.
revoke select on public.profiles from authenticated;
grant select (id, first_name, avatar_path, country_code, show_city, time_zone,
              correction_preference, is_verified, created_at, last_seen_at, interests)
    on public.profiles to authenticated;

-- 2. Demandes de discussion : request_conversation permettait d'ouvrir des demandes sans
--    limite ni contrôle de doublon. L'app passe par send_greeting, qui a ces garde-fous.
revoke execute on function public.request_conversation(uuid) from authenticated;

-- 3. Messages : avant l'acceptation, la personne qui demande pouvait envoyer autant de
--    messages qu'elle voulait. Désormais, seul le salut de send_greeting passe avant l'acceptation.
create or replace function public.can_post_in(conv uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select public.is_conversation_member(conv)
    and exists (
        select 1 from public.conversations c
        where c.id = conv and c.status = 'accepted'
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

-- 4. Présence : l'heure « vu à » venait de l'horloge du téléphone, donc modifiable.
--    Elle est maintenant posée par le serveur.
create or replace function public.mark_active()
returns void language sql security definer set search_path = '' as $$
    update public.profiles set last_seen_at = now() where id = auth.uid();
$$;
revoke execute on function public.mark_active() from public, anon;
grant execute on function public.mark_active() to authenticated;
revoke update (last_seen_at) on public.profiles from authenticated;
