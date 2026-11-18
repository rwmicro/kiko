-- Droits explicites sur les tables du prototype.
--
-- Les nouveaux projets Supabase ne donnent plus automatiquement accès aux tables du
-- schéma public via l'API. On accorde donc ici, table par table, ce dont l'app a besoin
-- une fois connectée (rôle authenticated). Les politiques RLS restent le vrai filtre :
-- ces droits ne font qu'ouvrir la porte que les politiques gardent.
-- Les visiteurs non connectés (rôle anon) n'ont accès à rien.

grant usage on schema public to authenticated;

revoke all on public.profiles, public.user_languages, public.blocks,
    public.conversations, public.conversation_members, public.messages from anon;

grant select, insert on public.profiles to authenticated;
-- Mise à jour du profil : seulement les colonnes listées dans la première migration.

grant select, insert, update, delete on public.user_languages to authenticated;

grant select, insert, delete on public.blocks to authenticated;

-- Conversations et membres sont créés par request_conversation (security definer).
grant select on public.conversations to authenticated;
grant select on public.conversation_members to authenticated;
grant update (last_read_message_id) on public.conversation_members to authenticated;

grant select, insert on public.messages to authenticated;
