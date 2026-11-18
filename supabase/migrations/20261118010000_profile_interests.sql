-- Centres d'intérêt choisis à l'inscription (au moins 3), modifiables ensuite.
alter table public.profiles add column interests text[] not null default '{}';
grant update (interests) on public.profiles to authenticated;
