-- 1. Notifications: the same unread message is never stored twice for one person.
--    Older duplicates are marked read first, so the unique index below can be built on existing data.
UPDATE notification n
SET read_at = now()
FROM (SELECT id, row_number() OVER (PARTITION BY recipient_id, type, md5(title || '|' || coalesce(link, ''))
                                    ORDER BY created_at DESC, id) AS rn
      FROM notification WHERE read_at IS NULL) d
WHERE n.id = d.id AND d.rn > 1;

-- md5 keeps the index entry small however long a title is.
CREATE UNIQUE INDEX notification_unread_unique
  ON notification (recipient_id, type, md5(title || '|' || coalesce(link, '')))
  WHERE read_at IS NULL;

-- 2. Idempotency keys remember what request they belong to, so one key cannot be reused for a different request.
ALTER TABLE idempotency_key ADD COLUMN request_hash text;
