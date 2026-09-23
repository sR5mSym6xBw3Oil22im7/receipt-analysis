ALTER TABLE receipt_game_monster ADD COLUMN IF NOT EXISTS visual_seed VARCHAR(64);
ALTER TABLE receipt_game_monster ADD COLUMN IF NOT EXISTS visual_profile_json TEXT;
ALTER TABLE receipt_game_monster ADD COLUMN IF NOT EXISTS image_prompt_version VARCHAR(40);
