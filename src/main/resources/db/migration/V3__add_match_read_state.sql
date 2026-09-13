ALTER TABLE match_info
    ADD COLUMN user_a_last_read_at DATETIME NULL AFTER matched_at,
    ADD COLUMN user_b_last_read_at DATETIME NULL AFTER user_a_last_read_at;
