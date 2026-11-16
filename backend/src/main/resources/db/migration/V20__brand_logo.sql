-- The company logo on its own, so exports can carry it even when the email signature is a finished banner.
ALTER TABLE mail_banner ADD COLUMN logo_png bytea;
UPDATE mail_banner SET logo_png = image_png WHERE kind = 'logo';
