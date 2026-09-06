package gg.neonnexus.smpplatform.integrations.link;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Command handlers use this façade so account-link SQL cannot execute on a Paper thread. */
public final class AsyncAccountLinkFacade {
    private final AccountLinkService service; private final Executor databaseExecutor;
    public AsyncAccountLinkFacade(AccountLinkService service, Executor databaseExecutor) { this.service = service; this.databaseExecutor = databaseExecutor; }
    public CompletableFuture<LinkCode> generate(UUID minecraftUuid, String actor, String correlationId) { return CompletableFuture.supplyAsync(() -> service.generate(minecraftUuid, actor, correlationId), databaseExecutor); }
    public CompletableFuture<Optional<LinkRecord>> consume(String code, String actor, String correlationId) { return CompletableFuture.supplyAsync(() -> service.consume(code, actor, correlationId), databaseExecutor); }
    public CompletableFuture<Boolean> revoke(UUID linkId, UUID minecraftUuid, String actor, String correlationId, String reason) { return CompletableFuture.supplyAsync(() -> service.revoke(linkId, minecraftUuid, actor, correlationId, reason), databaseExecutor); }
}
