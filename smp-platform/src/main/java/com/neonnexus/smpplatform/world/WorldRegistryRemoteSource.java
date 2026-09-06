package com.neonnexus.smpplatform.world;

import java.util.concurrent.CompletionStage;

/** Central API adapter contract. Implementations must never call or await on the Paper main thread. */
@FunctionalInterface
public interface WorldRegistryRemoteSource {
    CompletionStage<RegistryDocument> fetch(long knownRevision);
}
