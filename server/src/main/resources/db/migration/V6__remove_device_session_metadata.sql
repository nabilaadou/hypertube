ALTER TABLE user_sessions
    DROP COLUMN device_info,
    DROP COLUMN ip_address,
    DROP COLUMN created_at,
    DROP COLUMN last_used_at;
