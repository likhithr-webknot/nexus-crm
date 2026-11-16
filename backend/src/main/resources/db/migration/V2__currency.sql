-- Currencies and daily exchange rates (shared by leads, deals and reports).
-- Every amount is stored in its own currency; reports convert to the base currency (INR).

CREATE TABLE currency (
  code         char(3) PRIMARY KEY,      -- ISO 4217, for example 'INR'
  name         text NOT NULL,
  minor_units  smallint NOT NULL CHECK (minor_units BETWEEN 0 AND 4),  -- decimals: INR 2, KWD 3
  enabled      boolean NOT NULL DEFAULT true,
  is_base      boolean NOT NULL DEFAULT false
);
-- Exactly one base currency.
CREATE UNIQUE INDEX currency_one_base ON currency (is_base) WHERE is_base;

INSERT INTO currency (code, name, minor_units, enabled, is_base) VALUES
  ('INR', 'Indian rupee',     2, true, true),
  ('USD', 'US dollar',        2, true, false),
  ('AUD', 'Australian dollar', 2, true, false),
  ('SAR', 'Saudi riyal',      2, true, false);

CREATE TABLE exchange_rate (
  currency      char(3) NOT NULL REFERENCES currency(code),
  rate_date     date NOT NULL,
  rate_to_base  numeric(18,8) NOT NULL CHECK (rate_to_base > 0),  -- 1 unit = this many INR
  source        text NOT NULL,                                    -- provider name, or 'MANUAL' for an admin override
  updated_by    uuid REFERENCES app_user(id),                     -- set for manual overrides
  updated_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (currency, rate_date)
);
