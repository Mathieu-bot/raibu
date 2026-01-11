alter table "user" add column provider varchar(32);
alter table "user" add column provider_id varchar(255);
alter table "user" add column email varchar(255);
alter table "user" add column display_name varchar(255);
alter table "user" add column avatar_url varchar(255);

create index idx_user_provider_provider_id on "user" (provider, provider_id);
create index idx_user_email on "user" (email);
