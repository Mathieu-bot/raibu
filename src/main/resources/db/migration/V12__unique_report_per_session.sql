-- One report per (reporter, reported user, session). A NULL session is treated as the empty string
-- so that several session-less reports of the same pair are also deduplicated.

-- Keep the oldest report of each group and drop the other duplicates (idempotent).
delete from report r
    using report d
where r.reporter_id = d.reporter_id
  and r.reported_user_id = d.reported_user_id
  and coalesce(r.session_id, '') = coalesce(d.session_id, '')
  and (coalesce(r.created_at, '-infinity'::timestamptz), r.id)
    > (coalesce(d.created_at, '-infinity'::timestamptz), d.id);

create unique index if not exists uq_report_reporter_reported_session
    on report (reporter_id, reported_user_id, (coalesce(session_id, '')));
