-- 'banner': a finished image, sent as it is.
-- 'logo': a company logo; the server lays it out beside each sender's name when an email is sent.
ALTER TABLE mail_banner ADD COLUMN kind text NOT NULL DEFAULT 'banner' CHECK (kind IN ('banner', 'logo'));
ALTER TABLE mail_banner ADD COLUMN company_name text;
