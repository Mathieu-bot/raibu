CREATE TABLE direct_message (
    id varchar(36) primary key,
    sender_id varchar(36) not null,
    recipient_id varchar(36) not null,
    content text not null,
    sent_at timestamp with time zone not null,
    constraint fk_direct_message_sender foreign key (sender_id) references "user" (id),
    constraint fk_direct_message_recipient foreign key (recipient_id) references "user" (id)
);

CREATE INDEX idx_direct_message_pair_time ON direct_message (sender_id, recipient_id, sent_at);
