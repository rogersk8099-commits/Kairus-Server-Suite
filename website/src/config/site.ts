/**
 * Central, configurable site identity. Change these values to rebrand the
 * whole site — every page reads from here.
 */
export const site = {
  name: "KAIRU SMP",
  shortName: "KAIRU",
  tagline: "Survive. Build. Dominate.",
  description:
    "Kaji Kairu's hand-curated Minecraft SMP built for creators, competitors and community. Seasonal worlds, live events and a portal that tracks every block you place.",
  javaIp: "play.kairusmp.gg",
  bedrockIp: "bedrock.kairusmp.gg",
  bedrockPort: "19132",
  version: "1.21.4",
  discordUrl: "https://discord.gg/cbBj6EvcV4",
  discordName: "Kairu SMP Discord",
  storeUrl: "/membership",
  socials: {
    tiktok: "https://tiktok.com/@kajikairu",
    youtube: "https://youtube.com/@kajikairu",
    twitch: "https://twitch.tv/kajikairu",
    x: "https://x.com/kajikairu",
  },
} as const;
