ALTER TABLE "user" ADD COLUMN preferred_languages varchar(255);
ALTER TABLE "user" ADD COLUMN interests text;
ALTER TABLE "user" ADD COLUMN prefer_same_country boolean NOT NULL DEFAULT true;
