create table "user" (
    id varchar(36) primary key,
    username varchar(255),
    session_id varchar(255),
    status varchar(32),
    created_at timestamp with time zone,
    last_active_at timestamp with time zone,
    banned boolean not null default false
);

create table chat_session (
    id varchar(36) primary key,
    user1_id varchar(36) not null,
    user2_id varchar(36) not null,
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    status varchar(32),
    constraint fk_chat_session_user1 foreign key (user1_id) references "user" (id),
    constraint fk_chat_session_user2 foreign key (user2_id) references "user" (id)
);

create table report (
    id varchar(36) primary key,
    reporter_id varchar(36) not null,
    reported_user_id varchar(36) not null,
    session_id varchar(36),
    reason varchar(32),
    description text,
    created_at timestamp with time zone,
    status varchar(32),
    constraint fk_report_reporter foreign key (reporter_id) references "user" (id),
    constraint fk_report_reported_user foreign key (reported_user_id) references "user" (id),
    constraint fk_report_session foreign key (session_id) references chat_session (id)
);

create index idx_user_status on "user" (status);
create index idx_user_session_id on "user" (session_id);

create index idx_chat_session_users on chat_session (user1_id, user2_id);
create index idx_chat_session_status on chat_session (status);

create index idx_report_status on report (status);
create index idx_report_reported_user on report (reported_user_id);
