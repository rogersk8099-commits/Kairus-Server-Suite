export const visuals = {
  hero: "https://files.manuscdn.com/user_upload_by_module/session_file/310519663923349545/QvxdTNHJiILWvzdN.jpg",
  worlds: {
    spawn:
      "https://files.manuscdn.com/user_upload_by_module/session_file/310519663923349545/OpLwganjCpHcdHjP.jpg",
    creative:
      "https://files.manuscdn.com/user_upload_by_module/session_file/310519663923349545/uzrhCdEVYyZyLtkd.jpg",
    pve: "https://files.manuscdn.com/user_upload_by_module/session_file/310519663923349545/RTUlTbavGrFFAwna.jpg",
    pvp: "https://files.manuscdn.com/user_upload_by_module/session_file/310519663923349545/PhdWthMYqOEIfoFP.jpg",
  },
} as const;

export const worldArtwork = [
  visuals.worlds.pve,
  visuals.worlds.pvp,
  visuals.worlds.creative,
  visuals.worlds.spawn,
  visuals.worlds.pve,
  visuals.worlds.spawn,
] as const;
