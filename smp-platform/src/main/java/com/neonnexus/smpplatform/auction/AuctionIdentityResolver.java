package com.neonnexus.smpplatform.auction;
import java.util.UUID;
/** Control Plane validates that the Minecraft UUID is linked before auction mutations. */
public final class AuctionIdentityResolver {
    public UUID minecraftAccountId(UUID minecraftUuid){ return minecraftUuid; }
}
