CREATE TABLE friendship (
    id varchar(36) primary key,
    user_id_1 varchar(36) not null,
    user_id_2 varchar(36) not null,
    created_at timestamp with time zone not null,
    constraint fk_friendship_user1 foreign key (user_id_1) references "user" (id),
    constraint fk_friendship_user2 foreign key (user_id_2) references "user" (id),
    constraint uq_friendship_pair unique (user_id_1, user_id_2)
);

CREATE INDEX idx_friendship_user1 ON friendship (user_id_1);
CREATE INDEX idx_friendship_user2 ON friendship (user_id_2);
