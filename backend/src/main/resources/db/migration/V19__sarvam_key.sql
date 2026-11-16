-- Row 1 is the OpenAI key (the assistant); row 2 is the Sarvam AI key (voice).
ALTER TABLE ai_key_config DROP CONSTRAINT ai_key_config_id_check;
ALTER TABLE ai_key_config ADD CONSTRAINT ai_key_config_id_check CHECK (id IN (1, 2));
