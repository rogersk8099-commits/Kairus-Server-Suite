CREATE TABLE IF NOT EXISTS smp_auction_listings (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL,
    currency_id VARCHAR(64) NOT NULL,
    item_data BYTEA NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    starting_bid BIGINT NOT NULL CHECK (starting_bid >= 0),
    current_bid BIGINT NOT NULL CHECK (current_bid >= 0),
    highest_bidder UUID,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_smp_auction_browse ON smp_auction_listings(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_smp_auction_seller ON smp_auction_listings(seller_id, status);

CREATE TABLE IF NOT EXISTS smp_auction_bids (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES smp_auction_listings(id) ON DELETE CASCADE,
    bidder_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(listing_id, bidder_id, amount, created_at)
);
CREATE INDEX IF NOT EXISTS idx_smp_auction_bids_listing ON smp_auction_bids(listing_id, created_at);
