CREATE TABLE IF NOT EXISTS smp_automatic_point_rewards (
  reward_key VARCHAR(128) NOT NULL,
  player_id UUID NOT NULL,
  claimed_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (reward_key, player_id)
);
