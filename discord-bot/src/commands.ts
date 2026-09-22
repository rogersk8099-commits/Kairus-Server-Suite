import { randomUUID } from "node:crypto";
import type { Prisma, PrismaClient } from "@prisma/client";
import {
  ActionRowBuilder, ButtonBuilder, ButtonStyle, ChannelType, ModalBuilder, PermissionFlagsBits,
  SlashCommandBuilder, StringSelectMenuBuilder, StringSelectMenuOptionBuilder, TextInputBuilder,
  TextInputStyle, type AnySelectMenuInteraction, type ButtonInteraction, type ChatInputCommandInteraction,
  type Client, type ModalSubmitInteraction, type RESTPostAPIChatInputApplicationCommandsJSONBody
} from "discord.js";
import { commandModules as accountModules } from "./account/index.js";
import type { CommandServices as AccountServices, ComponentInteraction as AccountComponent } from "./account/interfaces.js";
import type { AppLogger } from "./logger.js";
import { handleSetupServerCommand, SETUP_SERVER_COMMAND } from "./setup/commands/admin/setup-server.command.js";
import type { ServerSetupService } from "./setup/services/server-setup.service.js";

const noMentions = { parse: [] as never[] };
type Component = ButtonInteraction | AnySelectMenuInteraction | ModalSubmitInteraction;
type RuntimeCommand = { definition: RESTPostAPIChatInputApplicationCommandsJSONBody; execute(interaction: ChatInputCommandInteraction): Promise<void> };
function accountDefinition(definition: AccountServices extends never ? never : typeof accountModules[number]["definition"]): RESTPostAPIChatInputApplicationCommandsJSONBody { return definition as unknown as RESTPostAPIChatInputApplicationCommandsJSONBody; }

function slash(name: string, description: string, configure?: (builder: SlashCommandBuilder) => void): SlashCommandBuilder {
  const builder = new SlashCommandBuilder().setName(name).setDescription(description).setDMPermission(false);
  configure?.(builder);
  return builder;
}
function staff(builder: SlashCommandBuilder, permission: bigint = PermissionFlagsBits.ManageGuild): void { builder.setDefaultMemberPermissions(permission); }
function str(builder: SlashCommandBuilder, name: string, description: string, required = true): void { builder.addStringOption((option) => option.setName(name).setDescription(description).setRequired(required).setMaxLength(1_000)); }
function user(builder: SlashCommandBuilder, name = "user", description = "Discord member"): void { builder.addUserOption((option) => option.setName(name).setDescription(description).setRequired(true)); }
function integer(builder: SlashCommandBuilder, name: string, description: string, minimum = 0): void { builder.addIntegerOption((option) => option.setName(name).setDescription(description).setRequired(true).setMinValue(minimum)); }
function requiredString(interaction: ChatInputCommandInteraction, name: string): string { const value = interaction.options.getString(name, true).trim(); if (!value) throw new Error(`${name} is required`); return value; }
function date(value: string): Date { const output = new Date(value); if (Number.isNaN(output.getTime())) throw new Error("Use a valid ISO-8601 date/time including timezone."); return output; }
function options(row: { options: Prisma.JsonValue }): string[] { return Array.isArray(row.options) ? row.options.filter((item): item is string => typeof item === "string") : []; }
function has(interaction: ChatInputCommandInteraction | Component, permission: bigint): boolean { return interaction.memberPermissions?.has(permission) === true; }
async function ephemeral(interaction: ChatInputCommandInteraction | Component, content: string): Promise<void> { await interaction.reply({ content: content.slice(0, 2_000), ephemeral: true, allowedMentions: noMentions }); }

function communityDefinitions(): SlashCommandBuilder[] {
  const result: SlashCommandBuilder[] = [];
  const add = (builder: SlashCommandBuilder) => result.push(builder);
  add(slash("event-create", "Create a community event", (b) => { staff(b); str(b, "title", "Event title"); str(b, "description", "Event description"); str(b, "starts_at", "ISO-8601 start time"); str(b, "channel_id", "Announcement channel ID", false); }));
  add(slash("event-edit", "Edit a community event", (b) => { staff(b); str(b, "event_id", "Event ID"); str(b, "title", "New title", false); str(b, "description", "New description", false); str(b, "starts_at", "New ISO-8601 start", false); str(b, "channel_id", "New channel ID", false); }));
  add(slash("event-cancel", "Cancel an event", (b) => { staff(b); str(b, "event_id", "Event ID"); str(b, "reason", "Cancellation reason"); }));
  add(slash("event-start", "Mark an event live", (b) => { staff(b); str(b, "event_id", "Event ID"); }));
  add(slash("event-announce", "Announce an event with RSVP controls", (b) => { staff(b); str(b, "event_id", "Event ID"); str(b, "channel_id", "Announcement channel ID", false); }));
  add(slash("streamer-apply", "Apply as a community streamer", (b) => { str(b, "platform", "TWITCH, YOUTUBE, or TIKTOK"); str(b, "channel_url", "Official channel URL"); str(b, "description", "Your content description"); str(b, "channel_id", "Official provider channel/user ID", false); }));
  add(slash("streamer-status", "View your streamer applications"));
  add(slash("streamer-list", "List streamer applications", (b) => { staff(b); str(b, "status", "Optional status filter", false); }));
  for (const action of ["approve", "reject", "suspend"] as const) add(slash(`streamer-${action}`, `${action} a streamer application`, (b) => { staff(b); str(b, "application_id", "Application ID"); str(b, "reason", "Review reason", action !== "approve"); }));
  add(slash("poll-create", "Create a verified-account poll", (b) => { staff(b); str(b, "question", "Poll question"); str(b, "options", "Options separated by | (2-25)"); str(b, "closes_at", "Optional ISO-8601 close time", false); }));
  add(slash("poll-vote", "Vote once with your linked account", (b) => { str(b, "poll_id", "Poll ID"); integer(b, "option", "Zero-based option number", 0); }));
  add(slash("poll-close", "Close a poll", (b) => { staff(b); str(b, "poll_id", "Poll ID"); }));
  add(slash("poll-results", "View poll results", (b) => str(b, "poll_id", "Poll ID")));
  add(slash("suggestion-create", "Submit a suggestion", (b) => { str(b, "title", "Suggestion title"); str(b, "body", "Suggestion detail"); }));
  add(slash("suggestion-vote", "Vote on a suggestion", (b) => { str(b, "suggestion_id", "Suggestion ID"); integer(b, "value", "1 for upvote, 0 for downvote", 0); }));
  add(slash("suggestion-state", "Set suggestion state", (b) => { staff(b, PermissionFlagsBits.ManageMessages); str(b, "suggestion_id", "Suggestion ID"); str(b, "state", "OPEN, UNDER_REVIEW, PLANNED, DECLINED, or IMPLEMENTED"); str(b, "staff_note", "Optional staff note", false); }));
  add(slash("suggestion-list", "List suggestions"));
  add(slash("ticket-menu", "Open the private support selector"));
  add(slash("ticket-open", "Open a private support ticket", (b) => { str(b, "category", "BILLING, TECHNICAL, PLAYER_REPORT, or OTHER"); str(b, "subject", "Ticket subject"); str(b, "description", "Detailed request"); }));
  add(slash("ticket-close", "Close a support ticket", (b) => str(b, "ticket_id", "Ticket ID")));
  add(slash("ticket-reopen", "Reopen a support ticket", (b) => str(b, "ticket_id", "Ticket ID")));
  add(slash("ticket-assign", "Assign a support ticket", (b) => { staff(b, PermissionFlagsBits.ManageChannels); str(b, "ticket_id", "Ticket ID"); user(b, "assignee", "Staff assignee"); }));
  add(slash("ticket-note", "Add a support ticket note", (b) => { str(b, "ticket_id", "Ticket ID"); str(b, "body", "Note body"); b.addBooleanOption((o) => o.setName("internal").setDescription("Staff-only note").setRequired(false)); }));
  add(slash("player-report", "Privately report a player", (b) => { str(b, "player", "Minecraft player"); str(b, "reason", "Detailed report"); str(b, "evidence_urls", "HTTPS evidence URLs separated by |", false); }));
  add(slash("player-report-list", "Review player reports", (b) => { staff(b, PermissionFlagsBits.ManageMessages); str(b, "status", "Optional status", false); }));
  add(slash("player-report-resolve", "Resolve or dismiss a report", (b) => { staff(b, PermissionFlagsBits.ManageMessages); str(b, "report_id", "Report ID"); str(b, "status", "RESOLVED or DISMISSED"); str(b, "resolution", "Resolution"); }));
  add(slash("warn", "Warn a member", (b) => { staff(b, PermissionFlagsBits.ModerateMembers); user(b); str(b, "reason", "Reason"); }));
  add(slash("timeout", "Timeout a member", (b) => { staff(b, PermissionFlagsBits.ModerateMembers); user(b); integer(b, "seconds", "1 to 2419200 seconds", 1); str(b, "reason", "Reason"); }));
  add(slash("kick", "Kick a member", (b) => { staff(b, PermissionFlagsBits.KickMembers); user(b); str(b, "reason", "Reason"); }));
  add(slash("ban", "Ban a member", (b) => { staff(b, PermissionFlagsBits.BanMembers); user(b); str(b, "reason", "Reason"); }));
  add(slash("unban", "Unban a user", (b) => { staff(b, PermissionFlagsBits.BanMembers); str(b, "user_id", "Discord user ID"); str(b, "reason", "Reason"); }));
  add(slash("mod-history", "View moderation history", (b) => { staff(b, PermissionFlagsBits.ModerateMembers); user(b); }));
  add(slash("nuke-channel", "Recreate a text channel after confirmation", (b) => { staff(b, PermissionFlagsBits.ManageChannels); b.addChannelOption((o) => o.setName("channel").setDescription("Text or announcement channel to recreate").setRequired(true).addChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement)); }));
  return result;
}

export class CommandRegistry {
  private readonly commands = new Map<string, RuntimeCommand>();
  public register(command: RuntimeCommand): void { if (this.commands.has(command.definition.name)) throw new Error(`Duplicate command ${command.definition.name}`); this.commands.set(command.definition.name, command); }
  public get(name: string): RuntimeCommand | undefined { return this.commands.get(name); }
  public definitions(): RESTPostAPIChatInputApplicationCommandsJSONBody[] { return [...this.commands.values()].map((item) => item.definition); }
}

interface CommandDependencies { database: PrismaClient; client: Client; logger: AppLogger; account: AccountServices; setup: ServerSetupService; }

export function createCommandRegistry(deps: CommandDependencies): CommandRegistry {
  const registry = new CommandRegistry();
  registry.register({ definition: SETUP_SERVER_COMMAND.toJSON(), execute: (interaction) => handleSetupServerCommand(interaction, { setupService: deps.setup }) });
  for (const module of accountModules) registry.register({ definition: accountDefinition(module.definition), execute: async (interaction) => { await module.execute(interaction, deps.account); } });
  for (const builder of communityDefinitions()) registry.register({ definition: builder.toJSON(), execute: (interaction) => executeCommunity(interaction, deps) });
  return registry;
}

export async function dispatchCommand(interaction: ChatInputCommandInteraction, registry: CommandRegistry, logger: AppLogger): Promise<void> {
  const command = registry.get(interaction.commandName);
  if (!command) return ephemeral(interaction, "That command is not available.");
  try { await command.execute(interaction); }
  catch (error) { logger.error({ err: error, command: interaction.commandName, guildId: interaction.guildId, userId: interaction.user.id }, "interaction command failed"); await safeFailure(interaction); }
}

export async function dispatchComponent(interaction: Component, deps: CommandDependencies): Promise<void> {
  try {
    for (const module of accountModules) if (module.handleComponent && await module.handleComponent(interaction as unknown as AccountComponent, deps.account)) return;
    if (interaction.isButton() && interaction.customId.startsWith("kairu:nuke-channel:")) return handleNukeChannel(interaction, deps);
    if (interaction.isButton() && interaction.customId.startsWith("event:rsvp:")) return handleRsvp(interaction, deps.database);
    if (interaction.isStringSelectMenu() && interaction.customId === "ticket:category") return showTicketModal(interaction);
    if (interaction.isModalSubmit() && interaction.customId.startsWith("ticket:modal:")) return openTicketFromModal(interaction, deps);
    await ephemeral(interaction, "This control has expired or is unavailable.");
  } catch (error) { deps.logger.error({ err: error, customId: interaction.customId, guildId: interaction.guildId }, "interaction component failed"); await safeFailure(interaction); }
}

async function requestNukeChannel(interaction: ChatInputCommandInteraction): Promise<void> {
  if (!has(interaction, PermissionFlagsBits.ManageChannels)) return ephemeral(interaction, "Manage Channels is required.");
  const channel = interaction.options.getChannel("channel", true);
  if (channel.type !== ChannelType.GuildText && channel.type !== ChannelType.GuildAnnouncement) return ephemeral(interaction, "Only text and announcement channels can be recreated.");
  if (!channel.deletable) return ephemeral(interaction, "I cannot delete that channel. Check the bot's Manage Channels permission and role position.");
  const customId = `kairu:nuke-channel:confirm:${interaction.user.id}:${channel.id}`;
  await interaction.reply({ content: `This will clone and then permanently delete ${channel}. Messages cannot be restored. Continue?`, ephemeral: true, allowedMentions: noMentions, components: [new ActionRowBuilder<ButtonBuilder>().addComponents(new ButtonBuilder().setCustomId(customId).setLabel("Clone and delete channel").setStyle(ButtonStyle.Danger), new ButtonBuilder().setCustomId(`kairu:nuke-channel:cancel:${interaction.user.id}:${channel.id}`).setLabel("Cancel").setStyle(ButtonStyle.Secondary))] });
}

async function handleNukeChannel(interaction: ButtonInteraction, deps: CommandDependencies): Promise<void> {
  const match = /^kairu:nuke-channel:(confirm|cancel):(\d{5,25}):(\d{5,25})$/.exec(interaction.customId);
  if (!match) return ephemeral(interaction, "This channel reset confirmation is invalid or expired.");
  const [, action, ownerId, channelId] = match;
  if (interaction.user.id !== ownerId) return ephemeral(interaction, "Only the staff member who requested this confirmation can use it.");
  if (!has(interaction, PermissionFlagsBits.ManageChannels)) return ephemeral(interaction, "Manage Channels is required.");
  if (action === "cancel") { await interaction.update({ content: "Channel reset cancelled.", components: [] }); return; }
  const channel = await deps.client.channels.fetch(channelId).catch(() => null);
  if (!channel || channel.guildId !== interaction.guildId || (channel.type !== ChannelType.GuildText && channel.type !== ChannelType.GuildAnnouncement)) return ephemeral(interaction, "That channel no longer exists or cannot be recreated.");
  if (!channel.deletable) return ephemeral(interaction, "I cannot delete that channel. Check my permissions and role position.");
  const replacement = await channel.clone({ reason: `Kairu channel reset confirmed by ${interaction.user.id}` });
  await deps.database.discordSetupResource.updateMany({ where: { guildId: interaction.guildId!, discordId: channel.id }, data: { discordId: replacement.id } });
  await channel.delete(`Kairu channel reset confirmed by ${interaction.user.id}`);
  deps.logger.warn({ guildId: interaction.guildId, channelId, replacementChannelId: replacement.id, actorDiscordUserId: interaction.user.id }, "Discord channel recreated by confirmed nuke-channel command");
  await interaction.update({ content: `Channel recreated as <#${replacement.id}>. Existing Kairu channel mappings were moved to the replacement; deleted messages cannot be restored.`, components: [] });
}

async function safeFailure(interaction: ChatInputCommandInteraction | Component): Promise<void> {
  const payload = { content: "Something went wrong. Please try again later.", ephemeral: true, allowedMentions: noMentions };
  try { if (interaction.deferred || interaction.replied) await interaction.followUp(payload); else await interaction.reply(payload); } catch { /* response token may have expired */ }
}

async function executeCommunity(i: ChatInputCommandInteraction, deps: CommandDependencies): Promise<void> {
  if (!i.guildId) return ephemeral(i, "This command is server-only.");
  const db = deps.database, now = new Date(), name = i.commandName;
  if (name.startsWith("event-") && !has(i, PermissionFlagsBits.ManageGuild)) return ephemeral(i, "Manage Server is required.");
  switch (name) {
    case "nuke-channel": return requestNukeChannel(i);
    case "event-create": { const event = await db.discordEvent.create({ data: { guildId: i.guildId, title: requiredString(i, "title"), description: requiredString(i, "description"), startsAt: date(requiredString(i, "starts_at")), createdBy: i.user.id, announcementChannelId: i.options.getString("channel_id") } }); return ephemeral(i, `Created event \`${event.id}\` as a draft.`); }
    case "event-edit": { const id = requiredString(i, "event_id"); const current = await db.discordEvent.findFirst({ where: { id, guildId: i.guildId } }); if (!current) return ephemeral(i, "Event not found."); const start = i.options.getString("starts_at"); const event = await db.discordEvent.update({ where: { id }, data: { title: i.options.getString("title") ?? undefined, description: i.options.getString("description") ?? undefined, startsAt: start ? date(start) : undefined, announcementChannelId: i.options.getString("channel_id") ?? undefined } }); return ephemeral(i, `Updated **${event.title}**.`); }
    case "event-cancel": { const event = await db.discordEvent.update({ where: { id: requiredString(i, "event_id") }, data: { status: "CANCELLED", cancellationReason: requiredString(i, "reason"), cancelledAt: now } }); return ephemeral(i, `Cancelled **${event.title}**.`); }
    case "event-start": { const event = await db.discordEvent.update({ where: { id: requiredString(i, "event_id") }, data: { status: "LIVE", startedAt: now } }); return ephemeral(i, `**${event.title}** is live.`); }
    case "event-announce": return announceEvent(i, deps);
    case "streamer-apply": { const platform = requiredString(i, "platform").toUpperCase(); if (!["TWITCH", "YOUTUBE", "TIKTOK"].includes(platform)) return ephemeral(i, "Platform must be TWITCH, YOUTUBE, or TIKTOK."); const app = await db.discordStreamer.create({ data: { guildId: i.guildId, discordUserId: i.user.id, platform, channelUrl: requiredString(i, "channel_url"), channelExternalId: i.options.getString("channel_id"), contentDescription: requiredString(i, "description") } }); return ephemeral(i, `Streamer application \`${app.id}\` submitted. TikTok automated discovery remains disabled.`); }
    case "streamer-status": { const rows = await db.discordStreamer.findMany({ where: { guildId: i.guildId, discordUserId: i.user.id }, orderBy: { createdAt: "desc" }, take: 20 }); return ephemeral(i, rows.map((row) => `\`${row.id}\` ${row.platform}: **${row.status}**`).join("\n") || "No applications found."); }
    case "streamer-list": { if (!has(i, PermissionFlagsBits.ManageGuild)) return ephemeral(i, "Manage Server is required."); const rows = await db.discordStreamer.findMany({ where: { guildId: i.guildId, status: i.options.getString("status")?.toUpperCase() }, take: 50 }); return ephemeral(i, rows.map((row) => `\`${row.id}\` <@${row.discordUserId}> ${row.platform}: **${row.status}**`).join("\n") || "No applications found."); }
    case "streamer-approve": case "streamer-reject": case "streamer-suspend": { if (!has(i, PermissionFlagsBits.ManageGuild)) return ephemeral(i, "Manage Server is required."); const status = name.slice(9).toUpperCase(); const app = await db.discordStreamer.update({ where: { id: requiredString(i, "application_id") }, data: { status: status === "APPROVE" ? "APPROVED" : status === "REJECT" ? "REJECTED" : "SUSPENDED", reviewedBy: i.user.id, reviewReason: i.options.getString("reason") } }); return ephemeral(i, `Application \`${app.id}\` is **${app.status}**.`); }
    case "poll-create": { if (!has(i, PermissionFlagsBits.ManageGuild)) return ephemeral(i, "Manage Server is required."); const choices = requiredString(i, "options").split("|").map((item) => item.trim()).filter(Boolean); if (choices.length < 2 || choices.length > 25 || new Set(choices.map((x) => x.toLowerCase())).size !== choices.length) return ephemeral(i, "Provide 2-25 distinct options separated by |."); const close = i.options.getString("closes_at"); const poll = await db.discordPoll.create({ data: { guildId: i.guildId, question: requiredString(i, "question"), options: choices, createdBy: i.user.id, closesAt: close ? date(close) : null } }); return ephemeral(i, `Created poll \`${poll.id}\`.`); }
    case "poll-vote": { const poll = await db.discordPoll.findFirst({ where: { id: requiredString(i, "poll_id"), guildId: i.guildId, status: "OPEN" } }); if (!poll || poll.closesAt && poll.closesAt <= now) return ephemeral(i, "Poll not found or closed."); const choice = i.options.getInteger("option", true), values = options(poll); if (choice < 0 || choice >= values.length) return ephemeral(i, "That option does not exist."); const link = await db.playerLink.findFirst({ where: { discordUserId: i.user.id }, orderBy: [{ isPrimary: "desc" }, { linkedAt: "asc" }] }); if (!link) return ephemeral(i, "Link your Minecraft account before voting."); try { await db.discordPollVote.create({ data: { pollId: poll.id, linkedAccountId: link.minecraftUuid, discordUserId: i.user.id, optionIndex: choice } }); } catch (error) { if ((error as { code?: string }).code === "P2002") return ephemeral(i, "Your Discord account has already voted in this poll."); throw error; } return ephemeral(i, "Vote recorded."); }
    case "poll-close": { if (!has(i, PermissionFlagsBits.ManageGuild)) return ephemeral(i, "Manage Server is required."); await db.discordPoll.update({ where: { id: requiredString(i, "poll_id") }, data: { status: "CLOSED" } }); return ephemeral(i, "Poll closed."); }
    case "poll-results": { const poll = await db.discordPoll.findFirst({ where: { id: requiredString(i, "poll_id"), guildId: i.guildId }, include: { votes: true } }); if (!poll) return ephemeral(i, "Poll not found."); const values = options(poll), totals = values.map((_, index) => poll.votes.filter((vote) => vote.optionIndex === index).length); await i.reply({ content: `**${poll.question}**\n${values.map((value, index) => `${index}. ${value}: ${totals[index]}`).join("\n")}`, allowedMentions: noMentions }); return; }
    case "suggestion-create": { const row = await db.discordSuggestion.create({ data: { guildId: i.guildId, authorDiscordId: i.user.id, title: requiredString(i, "title"), body: requiredString(i, "body") } }); return ephemeral(i, `Suggestion \`${row.id}\` submitted.`); }
    case "suggestion-vote": { const raw = i.options.getInteger("value", true), value = raw === 1 ? 1 : -1; await db.discordSuggestionVote.upsert({ where: { suggestionId_discordUserId: { suggestionId: requiredString(i, "suggestion_id"), discordUserId: i.user.id } }, create: { suggestionId: requiredString(i, "suggestion_id"), discordUserId: i.user.id, value }, update: { value } }); return ephemeral(i, "Suggestion vote recorded."); }
    case "suggestion-state": { if (!has(i, PermissionFlagsBits.ManageMessages)) return ephemeral(i, "Manage Messages is required."); const state = requiredString(i, "state").toUpperCase(); if (!["OPEN", "UNDER_REVIEW", "PLANNED", "DECLINED", "IMPLEMENTED"].includes(state)) return ephemeral(i, "Invalid suggestion state."); const row = await db.discordSuggestion.update({ where: { id: requiredString(i, "suggestion_id") }, data: { state, staffNote: i.options.getString("staff_note") } }); return ephemeral(i, `Suggestion \`${row.id}\` moved to **${row.state}**.`); }
    case "suggestion-list": { const rows = await db.discordSuggestion.findMany({ where: { guildId: i.guildId }, orderBy: { createdAt: "desc" }, take: 25 }); return ephemeral(i, rows.map((row) => `\`${row.id}\` **${row.state}** ${row.title}`).join("\n") || "No suggestions found."); }
    case "ticket-menu": return ticketMenu(i);
    case "ticket-open": return openTicket(i, deps, requiredString(i, "category"), requiredString(i, "subject"), requiredString(i, "description"));
    case "ticket-close": case "ticket-reopen": return changeTicketState(i, deps, name === "ticket-close" ? "CLOSED" : "OPEN");
    case "ticket-assign": return assignTicket(i, deps);
    case "ticket-note": return addTicketNote(i, deps);
    case "player-report": { const urls = (i.options.getString("evidence_urls") ?? "").split("|").map((url) => url.trim()).filter((url) => /^https:\/\//i.test(url)); const report = await db.discordReport.create({ data: { guildId: i.guildId, reporterDiscordId: i.user.id, accusedPlayer: requiredString(i, "player"), reason: requiredString(i, "reason"), evidenceUrls: urls } }); return ephemeral(i, `Player report \`${report.id}\` submitted privately.`); }
    case "player-report-list": { if (!has(i, PermissionFlagsBits.ManageMessages)) return ephemeral(i, "Manage Messages is required."); const rows = await db.discordReport.findMany({ where: { guildId: i.guildId, status: i.options.getString("status")?.toUpperCase() }, take: 50 }); return ephemeral(i, rows.map((row) => `\`${row.id}\` **${row.status}** ${row.accusedPlayer}`).join("\n") || "No reports found."); }
    case "player-report-resolve": { if (!has(i, PermissionFlagsBits.ManageMessages)) return ephemeral(i, "Manage Messages is required."); const status = requiredString(i, "status").toUpperCase(); if (status !== "RESOLVED" && status !== "DISMISSED") return ephemeral(i, "Status must be RESOLVED or DISMISSED."); const report = await db.discordReport.update({ where: { id: requiredString(i, "report_id") }, data: { status, resolution: requiredString(i, "resolution"), reviewedBy: i.user.id } }); return ephemeral(i, `Report \`${report.id}\` marked **${status}**.`); }
    case "warn": case "timeout": case "kick": case "ban": case "unban": return moderation(i, deps);
    case "mod-history": { if (!has(i, PermissionFlagsBits.ModerateMembers)) return ephemeral(i, "Moderate Members is required."); const target = i.options.getUser("user", true); const rows = await db.discordModerationAction.findMany({ where: { guildId: i.guildId, targetDiscordId: target.id }, orderBy: { createdAt: "desc" }, take: 50 }); return ephemeral(i, rows.map((row) => `**${row.action}** by <@${row.moderatorDiscordId}> — ${row.reason}`).join("\n") || "No moderation history."); }
  }
}

async function announceEvent(i: ChatInputCommandInteraction, deps: CommandDependencies): Promise<void> {
  const event = await deps.database.discordEvent.findFirst({ where: { id: requiredString(i, "event_id"), guildId: i.guildId! } }); if (!event) return ephemeral(i, "Event not found.");
  const resource = await deps.database.discordSetupResource.findUnique({ where: { guildId_resourceKey: { guildId: i.guildId!, resourceKey: "text-channel.events" } } });
  const channelId = i.options.getString("channel_id") ?? event.announcementChannelId ?? resource?.discordId; if (!channelId) return ephemeral(i, "No events channel is configured.");
  const channel = await deps.client.channels.fetch(channelId); if (channel?.type !== ChannelType.GuildText) return ephemeral(i, "The events channel is unavailable.");
  const row = new ActionRowBuilder<ButtonBuilder>().addComponents(new ButtonBuilder().setCustomId(`event:rsvp:${event.id}:GOING`).setLabel("Going").setStyle(ButtonStyle.Primary), new ButtonBuilder().setCustomId(`event:rsvp:${event.id}:MAYBE`).setLabel("Maybe").setStyle(ButtonStyle.Secondary), new ButtonBuilder().setCustomId(`event:rsvp:${event.id}:DECLINED`).setLabel("Can't go").setStyle(ButtonStyle.Danger));
  const sent = await channel.send({ content: `**${event.title}**\n${event.description}\nStarts <t:${Math.floor(event.startsAt.getTime() / 1_000)}:F>`, components: [row], allowedMentions: noMentions });
  await deps.database.discordEvent.update({ where: { id: event.id }, data: { status: "SCHEDULED", announcementChannelId: channelId, announcementMessageId: sent.id } });
  for (const [kind, offset] of [["24H", 86_400_000], ["1H", 3_600_000], ["15M", 900_000], ["NOW", 0]] as const) { const dueAt = new Date(event.startsAt.getTime() - offset); if (dueAt > new Date()) await deps.database.discordEventReminder.upsert({ where: { dedupeKey: `event-reminder:${event.id}:${kind}` }, create: { eventId: event.id, kind, dueAt, dedupeKey: `event-reminder:${event.id}:${kind}` }, update: { dueAt, deliveredAt: null } }); }
  return ephemeral(i, `Announced **${event.title}** and scheduled durable reminders.`);
}

async function handleRsvp(i: ButtonInteraction, db: PrismaClient): Promise<void> { const [, , eventId, status] = i.customId.split(":"); if (!eventId || (status !== "GOING" && status !== "MAYBE" && status !== "DECLINED")) return ephemeral(i, "Invalid RSVP control."); await db.discordEventRsvp.upsert({ where: { eventId_discordUserId: { eventId, discordUserId: i.user.id } }, create: { eventId, discordUserId: i.user.id, status }, update: { status } }); const rows = await db.discordEventRsvp.groupBy({ by: ["status"], where: { eventId }, _count: true }); const count = (key: string) => rows.find((row) => row.status === key)?._count ?? 0; await ephemeral(i, `RSVP saved: **${status}**. Going ${count("GOING")} · Maybe ${count("MAYBE")} · Can't go ${count("DECLINED")}`); }
async function ticketMenu(i: ChatInputCommandInteraction): Promise<void> { const menu = new StringSelectMenuBuilder().setCustomId("ticket:category").setPlaceholder("Choose a support category").addOptions(["BILLING", "TECHNICAL", "PLAYER_REPORT", "OTHER"].map((value) => new StringSelectMenuOptionBuilder().setLabel(value.replace("_", " ")).setValue(value))); await i.reply({ content: "Select a category:", ephemeral: true, components: [new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(menu)], allowedMentions: noMentions }); }
async function showTicketModal(i: AnySelectMenuInteraction): Promise<void> { if (!i.isStringSelectMenu()) return; const category = i.values[0]; if (!category) return ephemeral(i, "Choose a category."); const modal = new ModalBuilder().setCustomId(`ticket:modal:${category}`).setTitle("Open Kairu support ticket"); modal.addComponents(new ActionRowBuilder<TextInputBuilder>().addComponents(new TextInputBuilder().setCustomId("subject").setLabel("Subject").setStyle(TextInputStyle.Short).setRequired(true).setMaxLength(100)), new ActionRowBuilder<TextInputBuilder>().addComponents(new TextInputBuilder().setCustomId("description").setLabel("Description").setStyle(TextInputStyle.Paragraph).setRequired(true).setMaxLength(1_000))); await i.showModal(modal); }
async function openTicketFromModal(i: ModalSubmitInteraction, deps: CommandDependencies): Promise<void> { const category = i.customId.split(":")[2] ?? "OTHER"; await openTicket(i as unknown as ChatInputCommandInteraction, deps, category, i.fields.getTextInputValue("subject"), i.fields.getTextInputValue("description")); }
async function openTicket(i: ChatInputCommandInteraction, deps: CommandDependencies, category: string, subject: string, description: string): Promise<void> { if (!i.guildId || !i.guild) return ephemeral(i, "This command is server-only."); if (!["BILLING", "TECHNICAL", "PLAYER_REPORT", "OTHER"].includes(category.toUpperCase())) return ephemeral(i, "Invalid ticket category."); const count = await deps.database.discordTicket.count({ where: { guildId: i.guildId, requesterDiscordId: i.user.id, state: "OPEN" } }); if (count >= 3) return ephemeral(i, "You already have three open tickets."); const parent = await deps.database.discordSetupResource.findUnique({ where: { guildId_resourceKey: { guildId: i.guildId, resourceKey: "category.support" } } }); const staff = await deps.database.discordSetupResource.findMany({ where: { guildId: i.guildId, resourceKey: { in: ["role.owner", "role.administrator", "role.moderator", "role.helper"] } } }); const ticketId = randomUUID(); const channel = await i.guild.channels.create({ name: `ticket-${i.user.username}`.toLowerCase().replace(/[^a-z0-9-]/g, "-").slice(0, 90), type: ChannelType.GuildText, parent: parent?.discordId, permissionOverwrites: [{ id: i.guildId, deny: [PermissionFlagsBits.ViewChannel] }, { id: i.user.id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory] }, ...staff.map((role) => ({ id: role.discordId, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory] }))], reason: `Kairu ticket ${ticketId}` }); const ticket = await deps.database.discordTicket.create({ data: { id: ticketId, guildId: i.guildId, requesterDiscordId: i.user.id, category: category.toUpperCase(), subject, description, channelId: channel.id } }); await channel.send({ content: `<@${i.user.id}> **${subject}**\n${description}`, allowedMentions: { users: [i.user.id], roles: [] } }); await ephemeral(i, `Created private ticket <#${channel.id}> (\`${ticket.id}\`).`); }
async function ticketFor(i: ChatInputCommandInteraction, deps: CommandDependencies) { const ticket = await deps.database.discordTicket.findFirst({ where: { id: requiredString(i, "ticket_id"), guildId: i.guildId! } }); if (!ticket) throw new Error("Ticket not found"); if (ticket.requesterDiscordId !== i.user.id && ticket.assignedTo !== i.user.id && !has(i, PermissionFlagsBits.ManageChannels)) throw new Error("You do not have access to this ticket"); return ticket; }
async function changeTicketState(i: ChatInputCommandInteraction, deps: CommandDependencies, state: "OPEN" | "CLOSED"): Promise<void> { const ticket = await ticketFor(i, deps); const updated = await deps.database.discordTicket.update({ where: { id: ticket.id }, data: state === "CLOSED" ? { state, closedAt: new Date(), closedBy: i.user.id } : { state, closedAt: null, closedBy: null } }); const channel = await deps.client.channels.fetch(ticket.channelId).catch(() => null); if (channel?.type === ChannelType.GuildText) await channel.permissionOverwrites.edit(ticket.requesterDiscordId, state === "OPEN" ? { ViewChannel: true, SendMessages: true } : { ViewChannel: false, SendMessages: false }); return ephemeral(i, `${state === "OPEN" ? "Reopened" : "Closed"} ticket \`${updated.id}\`.`); }
async function assignTicket(i: ChatInputCommandInteraction, deps: CommandDependencies): Promise<void> { if (!has(i, PermissionFlagsBits.ManageChannels)) return ephemeral(i, "Manage Channels is required."); const ticket = await ticketFor(i, deps), assignee = i.options.getUser("assignee", true); await deps.database.discordTicket.update({ where: { id: ticket.id }, data: { assignedTo: assignee.id } }); const channel = await deps.client.channels.fetch(ticket.channelId).catch(() => null); if (channel?.type === ChannelType.GuildText) await channel.permissionOverwrites.edit(assignee.id, { ViewChannel: true, SendMessages: true, ReadMessageHistory: true }); return ephemeral(i, `Assigned ticket to <@${assignee.id}>.`); }
async function addTicketNote(i: ChatInputCommandInteraction, deps: CommandDependencies): Promise<void> { const ticket = await ticketFor(i, deps), internal = i.options.getBoolean("internal") ?? true; if (internal && !has(i, PermissionFlagsBits.ManageChannels)) return ephemeral(i, "Manage Channels is required for internal notes."); await deps.database.discordTicketNote.create({ data: { ticketId: ticket.id, authorDiscordId: i.user.id, body: requiredString(i, "body"), internal } }); return ephemeral(i, `Added ${internal ? "internal" : "requester-visible"} note.`); }
async function moderation(i: ChatInputCommandInteraction, deps: CommandDependencies): Promise<void> { if (!i.guildId || !i.guild) return ephemeral(i, "This command is server-only."); const action = i.commandName.toUpperCase(); const required = action === "BAN" || action === "UNBAN" ? PermissionFlagsBits.BanMembers : action === "KICK" ? PermissionFlagsBits.KickMembers : PermissionFlagsBits.ModerateMembers; if (!has(i, required)) return ephemeral(i, "You do not have the required moderation permission."); const targetId = action === "UNBAN" ? requiredString(i, "user_id") : i.options.getUser("user", true).id, reason = requiredString(i, "reason"); if (targetId === i.user.id) return ephemeral(i, "You cannot moderate yourself."); let durationSeconds: number | null = null; if (action === "TIMEOUT") { durationSeconds = i.options.getInteger("seconds", true); if (durationSeconds > 2_419_200) return ephemeral(i, "Timeout cannot exceed 28 days."); const member = await i.guild.members.fetch(targetId); await member.timeout(durationSeconds * 1_000, reason); } else if (action === "KICK") await (await i.guild.members.fetch(targetId)).kick(reason); else if (action === "BAN") await i.guild.members.ban(targetId, { reason }); else if (action === "UNBAN") await i.guild.members.unban(targetId, reason); const row = await deps.database.discordModerationAction.create({ data: { guildId: i.guildId, targetDiscordId: targetId, moderatorDiscordId: i.user.id, action, reason, durationSeconds } }); return ephemeral(i, `${action} recorded as case \`${row.id}\`.`); }
