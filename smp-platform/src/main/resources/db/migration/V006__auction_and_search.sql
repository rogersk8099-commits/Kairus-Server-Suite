-- Auction/search foundation. Runtime services must use transactions and server-side permission checks.
CREATE TABLE IF NOT EXISTS smp_auction_listings (
  id UUID PRIMARY KEY,
  seller_minecraft_uuid UUID NOT NULL,
  seller_platform_user_id UUID,
  item_data TEXT NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  listing_type VARCHAR(24) NOT NULL CHECK (listing_type IN ('FIXED_PRICE','AUCTION')),
  starting_price BIGINT NOT NULL CHECK (starting_price >= 0),
  buy_now_price BIGINT,
  current_bid BIGINT,
  current_bidder_minecraft_uuid UUID,
  currency VARCHAR(32) NOT NULL,
  world_id VARCHAR(64),
  status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE','SOLD','EXPIRED','CANCELLED','REMOVED_BY_STAFF')),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL,
  sold_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_smp_auction_status_expiry ON smp_auction_listings(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_smp_auction_seller ON smp_auction_listings(seller_minecraft_uuid, status);

CREATE TABLE IF NOT EXISTS smp_auction_bids (
  id UUID PRIMARY KEY,
  listing_id UUID NOT NULL REFERENCES smp_auction_listings(id),
  bidder_minecraft_uuid UUID NOT NULL,
  amount BIGINT NOT NULL CHECK (amount > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_smp_auction_bids_listing ON smp_auction_bids(listing_id, amount DESC);

CREATE TABLE IF NOT EXISTS smp_auction_deliveries (
  id UUID PRIMARY KEY,
  minecraft_uuid UUID NOT NULL,
  listing_id UUID REFERENCES smp_auction_listings(id),
  delivery_type VARCHAR(24) NOT NULL CHECK (delivery_type IN ('ITEM','PROCEEDS','REFUND')),
  payload TEXT NOT NULL,
  claimed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_smp_auction_delivery_unclaimed ON smp_auction_deliveries(minecraft_uuid, claimed_at);

-- Search should use normalized/indexed identities rather than unbounded table scans.
CREATE INDEX IF NOT EXISTS idx_smp_players_username_lower ON smp_players ((lower(username)));
