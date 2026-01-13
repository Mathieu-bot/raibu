CREATE TABLE chat_message (
    id varchar(36) primary key,
    session_id varchar(36) not null,
    sender_id varchar(36) not null,
    content text not null,
    sent_at timestamp with time zone not null,
    constraint fk_chat_message_session foreign key (session_id) references chat_session (id),
    constraint fk_chat_message_sender foreign key (sender_id) references "user" (id)
);

CREATE INDEX idx_chat_message_session_time ON chat_message (session_id, sent_at);
