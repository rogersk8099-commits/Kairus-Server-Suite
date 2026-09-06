export type ValidatedAuthUrls = {
  websiteOrigin: string;
  redirectUri: string;
};

function parseHttpsOrLocalhost(value: string, label: string): URL {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error(`${label} must be an absolute URL`);
  }
  if (url.username || url.password || url.hash || url.search) {
    throw new Error(`${label} must not contain credentials, a query, or a fragment`);
  }
  const local = url.hostname === "localhost" || url.hostname === "127.0.0.1";
  if (url.protocol !== "https:" && !(local && url.protocol === "http:")) {
    throw new Error(`${label} must use HTTPS (HTTP is allowed only for localhost)`);
  }
  return url;
}

export function validateConfiguredAuthUrls(websiteUrl: string, redirectUri: string): ValidatedAuthUrls {
  const website = parseHttpsOrLocalhost(websiteUrl, "WEBSITE_URL");
  if (website.pathname !== "/") throw new Error("WEBSITE_URL must be an origin without a path");
  const callback = parseHttpsOrLocalhost(redirectUri, "DISCORD_OAUTH_REDIRECT_URI");
  const expected = `${website.origin}/auth/discord/callback`;
  if (callback.toString() !== expected || redirectUri !== expected) {
    throw new Error(`DISCORD_OAUTH_REDIRECT_URI must exactly equal ${expected}`);
  }
  return { websiteOrigin: website.origin, redirectUri: expected };
}

export function assertExactRedirectUri(
  requested: string,
  configuredWebsiteUrl: string,
  configuredRedirectUri: string,
): void {
  const validated = validateConfiguredAuthUrls(configuredWebsiteUrl, configuredRedirectUri);
  if (requested !== validated.redirectUri) throw new Error("OAuth redirect URI is not allowlisted");
}
