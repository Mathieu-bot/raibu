ALTER TABLE "user" ADD COLUMN search_mode varchar(64);

CREATE INDEX idx_user_search_mode ON "user" (search_mode);
