import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  Client,
  EmbedBuilder,
  Events,
  GatewayIntentBits,
  PermissionFlagsBits,
  SlashCommandBuilder,
  type ChatInputCommandInteraction,
  type Interaction
} from "discord.js";
import { generateLinkCode, hashLinkCode } from "../security.js";
import type { AppConfig, ControlPlaneStore } from "../types.js";

export const slashCommands = [
  new SlashCommandBuilder().setName("link").setDescription("Create a one-time Minecraft account linking code"),
  new SlashCommandBuilder().setName("status").setDescription("Show live Kairu server health"),
  new SlashCommandBuilder().setName("players").setDescription("List players from the latest server heartbeat"),
  new SlashCommandBuilder().setName("events").setDescription("List the next scheduled Kairu events"),
  new SlashCommandBuilder()
    .setName("sync")
    .setDescription("Synchronize Discord roles for a linked member")
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageRoles)
    .addUserOption((option) => option.setName("member").setDescription("Linked member to synchronize").setRequired(true)),
  new SlashCommandBuilder().setName("unlink").setDescription("Remove your linked Minecraft identity")
].map((command) => command.toJSON());

function relativeTime(iso: string): string {
  return `<t:${Math.floor(new Date(iso).getTime() / 1000)}:R>`;
}

async function replyStatus(interaction: ChatInputCommandInteraction, store: ControlPlaneStore) {
  const heartbeat = await store.getLatestHeartbeat();
  if (!heartbeat) return interaction.reply({ ephemeral: true, content: "The server has not sent a heartbeat yet, so live status is unavailable." });
  const color = heartbeat.online ? 0x57f287 : 0xed4245;
  return interaction.reply({ embeds: [new EmbedBuilder().setColor(color).setTitle(heartbeat.online ? "Kairu SMP is online" : "Kairu SMP is offline").addFields({ name: "Players", value: `${heartbeat.playerCount}`, inline: true }, { name: "TPS", value: heartbeat.tps.toFixed(2), inline: true }, { name: "Version", value: heartbeat.version, inline: true }, { name: "Last heartbeat", value: relativeTime(heartbeat.receivedAt), inline: false })] });
}

async function replyPlayers(interaction: ChatInputCommandInteraction, store: ControlPlaneStore) {
  const heartbeat = await store.getLatestHeartbeat();
  if (!heartbeat?.online) return interaction.reply({ ephemeral: true, content: "The server is offline or has not sent a heartbeat yet." });
  const display = heartbeat.players.length ? heartbeat.players.join(", ") : "No player names were included in the latest heartbeat.";
  return interaction.reply({ embeds: [new EmbedBuilder().setColor(0x5865f2).setTitle(`Online players (${heartbeat.playerCount})`).setDescription(display.slice(0, 4_096)).setFooter({ text: `Updated ${relativeTime(heartbeat.receivedAt)}` })] });
}

async function replyEvents(interaction: ChatInputCommandInteraction, store: ControlPlaneStore) {
  const events = await store.listEvents(10);
  if (!events.length) return interaction.reply({ ephemeral: true, content: "There are no upcoming events scheduled." });
  const description = events.map((event) => `**${event.title}** — ${relativeTime(event.startsAt)}${event.location ? `\n${event.location}` : ""}${event.description ? `\n${event.description}` : ""}`).join("\n\n");
  return interaction.reply({ embeds: [new EmbedBuilder().setColor(0xfaa61a).setTitle("Upcoming Kairu events").setDescription(description.slice(0, 4_096))] });
}

async function replyLink(interaction: ChatInputCommandInteraction, store: ControlPlaneStore) {
  const existing = await store.getLinkByDiscordUser(interaction.user.id);
  if (existing) return interaction.reply({ ephemeral: true, content: `You are already linked to **${existing.javaUsername}**. Use /unlink first if you need to change identities.` });
  const code = generateLinkCode();
  const expiresAt = new Date(Date.now() + 10 * 60 * 1_000);
  await store.createLinkCode(interaction.user.id, hashLinkCode(code), expiresAt);
  return interaction.reply({ ephemeral: true, embeds: [new EmbedBuilder().setColor(0x57f287).setTitle("Link your Minecraft account").setDescription(`Run the following command in Minecraft within ten minutes:\n\n\`/kairu link ${code}\`\n\nThis code is **single-use** and expires ${relativeTime(expiresAt.toISOString())}. Do not share it.`)] });
}

async function replySync(interaction: ChatInputCommandInteraction, store: ControlPlaneStore, config: AppConfig) {
  if (!interaction.inGuild() || !interaction.memberPermissions?.has(PermissionFlagsBits.ManageRoles)) return interaction.reply({ ephemeral: true, content: "You need the **Manage Roles** permission to run /sync." });
  const guild = interaction.guild;
  if (!guild) return interaction.reply({ ephemeral: true, content: "This command is only available in a server." });
  const user = interaction.options.getUser("member", true);
  const link = await store.getLinkByDiscordUser(user.id);
  if (!link) return interaction.reply({ ephemeral: true, content: `${user.tag} does not have a linked Minecraft identity.` });
  const snapshot = await store.getPlayerSnapshot(link.minecraftUuid);
  if (!snapshot) return interaction.reply({ ephemeral: true, content: `${user.tag} is linked but has not submitted a player snapshot yet.` });
  const roleId = config.membershipRoleMap[snapshot.rankName];
  if (!roleId) return interaction.reply({ ephemeral: true, content: `No Discord role mapping is configured for the **${snapshot.rankName}** rank.` });
  const member = await guild.members.fetch(user.id);
  const role = await guild.roles.fetch(roleId);
  if (!role) return interaction.reply({ ephemeral: true, content: `The configured role for **${snapshot.rankName}** no longer exists.` });
  const botHighestRole = guild.members.me?.roles.highest;
  if (!botHighestRole || role.position >= botHighestRole.position) return interaction.reply({ ephemeral: true, content: "I cannot assign that role because it is at or above my highest role." });
  if (!member.roles.cache.has(role.id)) await member.roles.add(role, `Kairu /sync: ${snapshot.rankName}`);
  return interaction.reply({ ephemeral: true, content: `Synchronized ${user.tag}: ensured the **${role.name}** role for rank **${snapshot.rankName}**.` });
}

async function handleCommand(interaction: ChatInputCommandInteraction, store: ControlPlaneStore, config: AppConfig) {
  switch (interaction.commandName) {
    case "link": return replyLink(interaction, store);
    case "status": return replyStatus(interaction, store);
    case "players": return replyPlayers(interaction, store);
    case "events": return replyEvents(interaction, store);
    case "sync": return replySync(interaction, store, config);
    case "unlink": {
      const link = await store.getLinkByDiscordUser(interaction.user.id);
      if (!link) return interaction.reply({ ephemeral: true, content: "You do not have a linked Minecraft identity." });
      const confirm = new ButtonBuilder().setCustomId(`unlink:confirm:${interaction.user.id}`).setLabel("Confirm unlink").setStyle(ButtonStyle.Danger);
      const cancel = new ButtonBuilder().setCustomId(`unlink:cancel:${interaction.user.id}`).setLabel("Cancel").setStyle(ButtonStyle.Secondary);
      return interaction.reply({ ephemeral: true, content: `Remove your link to **${link.javaUsername}**? This cannot be undone.`, components: [new ActionRowBuilder<ButtonBuilder>().addComponents(confirm, cancel)] });
    }
  }
}

async function handleButton(interaction: Interaction, store: ControlPlaneStore) {
  if (!interaction.isButton() || !interaction.customId.startsWith("unlink:")) return;
  const [, action, userId] = interaction.customId.split(":");
  if (interaction.user.id !== userId) return interaction.reply({ ephemeral: true, content: "Only the member who requested this confirmation can use it." });
  if (action === "cancel") return interaction.update({ content: "Unlink cancelled.", components: [] });
  const removed = await store.unlinkDiscordUser(userId);
  return interaction.update({ content: removed ? "Your Minecraft identity link has been removed." : "Your Minecraft identity link was already removed.", components: [] });
}

export async function startDiscordBot(config: AppConfig, store: ControlPlaneStore): Promise<Client> {
  if (!config.discordBotToken) throw new Error("DISCORD_BOT_TOKEN is required to start the bot");
  const client = new Client({ intents: [GatewayIntentBits.Guilds] });
  client.once(Events.ClientReady, (readyClient) => console.info(`Discord bot logged in as ${readyClient.user.tag}`));
  client.on(Events.InteractionCreate, async (interaction) => {
    try {
      if (interaction.isChatInputCommand()) await handleCommand(interaction, store, config);
      else await handleButton(interaction, store);
    } catch (error) {
      const message = "I could not complete that command. Please try again later.";
      if (interaction.isRepliable()) {
        if (interaction.replied || interaction.deferred) await interaction.followUp({ ephemeral: true, content: message });
        else await interaction.reply({ ephemeral: true, content: message });
      }
      console.error("Discord interaction error", error);
    }
  });
  await client.login(config.discordBotToken);
  return client;
}
