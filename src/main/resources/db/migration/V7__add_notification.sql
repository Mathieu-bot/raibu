CREATE TABLE notification (
    id varchar(36) primary key,
    user_id varchar(36) not null,
    type varchar(64) not null,
    message text not null,
    data text,
    created_at timestamp with time zone not null,
    "read" boolean not null default false,
    read_at timestamp with time zone,
    constraint fk_notification_user foreign key (user_id) references "user" (id)
);

CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_user_read ON notification (user_id, "read");
