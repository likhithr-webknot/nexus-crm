-- Remembers the answer to a request that may be retried, so a retry never creates a second record.
-- Rows are deleted after 24 hours.

CREATE TABLE idempotency_key (
  scope            text NOT NULL,                  -- the endpoint name
  key              text NOT NULL,
  response_status  int NOT NULL,
  response_body    jsonb,
  created_at       timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (scope, key)
);
CREATE INDEX idempotency_key_created_at ON idempotency_key (created_at);
