CREATE TABLE IF NOT EXISTS auction_listings (
 id UUID PRIMARY KEY,
 seller_minecraft_uuid UUID NOT NULL,
 item_payload TEXT NOT NULL,
 item_name TEXT NOT NULL,
 quantity INTEGER NOT NULL CHECK(quantity>0),
 listing_type TEXT NOT NULL CHECK(listing_type IN ('FIXED','BID')),
 starting_price NUMERIC(19,2) NOT NULL CHECK(starting_price>=0),
 buy_now_price NUMERIC(19,2),
 current_bid NUMERIC(19,2),
 current_bidder_minecraft_uuid UUID,
 status TEXT NOT NULL CHECK(status IN ('ACTIVE','RESERVED','SOLD','EXPIRED','CANCELLED','REMOVED_BY_STAFF')),
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 expires_at TIMESTAMPTZ NOT NULL,
 sold_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_auction_active_created ON auction_listings(status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_auction_seller ON auction_listings(seller_minecraft_uuid,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_auction_item_name ON auction_listings((lower(item_name)));

CREATE TABLE IF NOT EXISTS auction_bids (
 id UUID PRIMARY KEY,
 listing_id UUID NOT NULL REFERENCES auction_listings(id) ON DELETE CASCADE,
 bidder_minecraft_uuid UUID NOT NULL,
 amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
 status TEXT NOT NULL DEFAULT 'ACTIVE',
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_auction_bids_listing ON auction_bids(listing_id,created_at DESC);

CREATE TABLE IF NOT EXISTS auction_deliveries (
 id UUID PRIMARY KEY,
 minecraft_uuid UUID NOT NULL,
 listing_id UUID REFERENCES auction_listings(id),
 delivery_type TEXT NOT NULL CHECK(delivery_type IN ('ITEM','MONEY')),
 item_payload TEXT,
 money_amount NUMERIC(19,2),
 status TEXT NOT NULL CHECK(status IN ('PENDING','CLAIMING','DELIVERED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 claimed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_auction_delivery_claim ON auction_deliveries(minecraft_uuid,status,created_at);
