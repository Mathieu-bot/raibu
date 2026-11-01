-- Sessions are looked up by either participant: user1_id is covered by idx_chat_session_users,
-- user2_id was not. idx_chat_session_status (V1) and idx_direct_message_pair_time (V11) exist.
CREATE INDEX IF NOT EXISTS idx_chat_session_user2 ON chat_session (user2_id);
