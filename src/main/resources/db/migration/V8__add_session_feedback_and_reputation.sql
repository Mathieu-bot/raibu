CREATE TABLE session_feedback (
    id varchar(36) primary key,
    session_id varchar(36) not null,
    from_user_id varchar(36) not null,
    to_user_id varchar(36) not null,
    liked boolean not null,
    created_at timestamp with time zone not null,
    constraint fk_session_feedback_session foreign key (session_id) references chat_session (id),
    constraint fk_session_feedback_from_user foreign key (from_user_id) references "user" (id),
    constraint fk_session_feedback_to_user foreign key (to_user_id) references "user" (id),
    constraint uq_session_feedback_session_from unique (session_id, from_user_id)
);

CREATE INDEX idx_session_feedback_to_user_created ON session_feedback (to_user_id, created_at DESC);

ALTER TABLE "user" ADD COLUMN reputation_score integer NOT NULL DEFAULT 0;
ALTER TABLE "user" ADD COLUMN positive_feedback_count integer NOT NULL DEFAULT 0;
ALTER TABLE "user" ADD COLUMN negative_feedback_count integer NOT NULL DEFAULT 0;
ALTER TABLE "user" ADD COLUMN strike_count integer NOT NULL DEFAULT 0;
