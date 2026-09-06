# Discord Developer Portal Setup

**Document status:** Follow these steps to create a dedicated Kairu bot application. This guide does **not** state that a Discord application, token, or guild installation has been completed.

## Design decision: interaction-first and least privilege

Kairu should use Discord application commands and interaction payloads for operator actions. This permits a default configuration with **no privileged gateway intents**. Native slash commands do not require the bot to inspect arbitrary message content. Do not enable Presence, Server Members, or Message Content merely as a precaution; each grants access to sensitive event data and must have a documented feature necessity.

| Capability | Portal / runtime setting | Default decision | Reason |
|---|---|---|---|
| Slash commands and buttons | `applications.commands` scope; interaction handling. | Enable. | This is the supported command interface. |
| Server install | `bot` scope in the Guild Install configuration. | Enable. | The dedicated bot requires a guild identity. |
| Standard guild metadata | `GUILDS` Gateway intent only if a Gateway client is used. | Enable only when code needs it. | Standard intent; avoids unused event subscriptions. |
| Message Content | Privileged `MESSAGE_CONTENT` intent. | **Do not enable.** | Kairu’s command interface should not read ordinary messages. |
| Guild Members | Privileged `GUILD_MEMBERS` intent. | **Do not enable.** | Do not cache or list all members without a documented feature requirement. |
| Presence | Privileged `GUILD_PRESENCES` intent. | **Do not enable.** | Presence is not required for commands or Kairu API operations. |

Discord requires privileged intents to be enabled in the application’s Bot settings before the app identifies with them. Apps qualifying for verification need approval for privileged access. A Gateway connection that identifies with an unauthorized privileged intent can close with code `4014`. [1]

## Portal procedure

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) while signed in to the organization-controlled owner account. Create a **new application** named `Kairu <Environment> Bot`, such as `Kairu Staging Bot`. Do not reuse an unrelated community bot.
2. On **General Information**, record the **Application ID** and **Public Key** in the approved secret/configuration system. The public key may be configuration, but treat the surrounding application record as sensitive operational data.
3. Open **Bot**. Confirm a bot user exists. Under **Token**, select **Reset Token** only when ready to place the resulting value directly in approved secret storage. Discord shows a regenerated bot token only at creation/reset time and warns it must not be committed. [2]
4. Under **Privileged Gateway Intents**, leave all three privileged toggles off for the baseline interaction-first bot. If a future feature proposes one, update the threat model, data-retention design, code intent mask, approval record, and this table before enabling it.
5. Open **Installation**. Enable **Guild Install**. Enable **User Install** only if the product explicitly supports personal, command-only usage; it is not required for a dedicated guild bot. Discord distinguishes guild installs, which require an installer with `MANAGE_GUILD`, from user installs, which are command-only for the authorizing user. [2]
6. Set the Guild Install default scopes to `bot` and `applications.commands`. Do not add OAuth scopes that the bot does not consume.
7. Select only the bot permissions in the next table. Generate/copy the Discord-provided install link. The link contains an application ID and scopes, but it must still be reviewed before distribution.
8. In the staging guild, open the link, choose the correct guild, inspect the authorization screen, and complete installation with an authorized administrator. Confirm commands are visible and that the bot’s role is below any role it must not manage.
9. Configure the interaction endpoint or deploy the Gateway runtime according to the implementation. For HTTP interactions, set the production HTTPS endpoint only after it verifies Discord signatures; for Gateway, set only the required standard intents in code. Discord interactions must be validated with the application public key. [3]

## Invite permission baseline

Select permissions by feature rather than giving `Administrator`. Permissions can be granted in the portal’s Guild Install settings and further restricted with guild channels/role overwrites.

| Permission | Baseline | Enable only when the reviewed feature requires it | Notes |
|---|---|---|---|
| View Channels | Yes | N/A | Restrict visibility to Kairu channels where possible. |
| Send Messages | Yes | N/A | Needed for ordinary command responses if the implementation posts messages. |
| Embed Links | Yes | N/A | Needed only for rich response embeds. |
| Attach Files | No | Reports or generated files. | Do not use for secret-bearing files. |
| Read Message History | No | Explicit message-history feature. | Not needed for slash commands; does not bypass Message Content rules. |
| Manage Messages | No | Moderation or cleanup feature. | Requires explicit product and guild approval. |
| Manage Roles | No | `/setup-server` creates/updates only Kairu-managed roles. | Role hierarchy limits what the bot can manage. |
| Manage Channels | No | `/setup-server` creates/updates only Kairu-managed channels. | Prefer existing configured channels. |
| Manage Webhooks | No | Bot must manage a Kairu-owned Discord webhook. | Do not use for external inbound webhook security. |
| Administrator | **Never** | No ordinary feature. | It defeats permission minimization and should not be granted. |

## Verification and change control

After installation, execute a harmless command in the test guild. Confirm that the bot cannot access a channel excluded by overwrites and cannot manage a role above its own role. Review the Developer Portal monthly and following any ownership change: application team, install contexts, redirect URLs, privileged intents, bot role permissions, and token rotation date.

| Change | Required approval | Required test |
|---|---|---|
| Add a privileged intent | Security owner and product owner. | Demonstrate feature failure without it and safe operation with it in staging. |
| Add `Manage Roles` or `Manage Channels` | Guild administrator and release owner. | Prove `/setup-server` creates only marked Kairu resources and is idempotent. |
| Reset bot token | Incident owner or scheduled rotation owner. | Deploy new secret, verify login, revoke old token by reset, and verify old token fails. |
| Change interaction endpoint | Release owner. | Validate Discord request signature and endpoint availability before cutover. |

## References

[1]: https://docs.discord.com/developers/events/gateway "Discord Developer Documentation: Gateway and privileged intents"
[2]: https://docs.discord.com/developers/quick-start/getting-started "Discord Developer Documentation: Getting Started"
[3]: https://docs.discord.com/developers/interactions/overview "Discord Developer Documentation: Interactions Overview"
[4]: https://discord.com/developers/docs/topics/permissions "Discord Developer Documentation: Permissions"
