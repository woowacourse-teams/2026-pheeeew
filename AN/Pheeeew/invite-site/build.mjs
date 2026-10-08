import { mkdir, readFile, writeFile, copyFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const siteDirectory = dirname(fileURLToPath(import.meta.url));
const outputDirectory = join(siteDirectory, "dist");
const storeLinks = JSON.parse(await readFile(join(siteDirectory, "store-links.json"), "utf8"));

function requireHttpsUrl(value, name, expectedHost) {
  const url = new URL(value);
  if (url.protocol !== "https:" || url.hostname !== expectedHost) {
    throw new Error(`${name} must be an HTTPS URL hosted on ${expectedHost}.`);
  }
  return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;");
}

const replacements = {
  "{{APP_STORE_URL}}": requireHttpsUrl(storeLinks.appStore, "appStore", "apps.apple.com"),
  "{{GOOGLE_PLAY_URL}}": requireHttpsUrl(storeLinks.googlePlay, "googlePlay", "play.google.com"),
};
let html = await readFile(join(siteDirectory, "index.template.html"), "utf8");

for (const [placeholder, value] of Object.entries(replacements)) {
  if (!html.includes(placeholder)) {
    throw new Error(`Missing template placeholder: ${placeholder}`);
  }
  html = html.replaceAll(placeholder, value);
}

await mkdir(outputDirectory, { recursive: true });
await writeFile(join(outputDirectory, "index.html"), html);
await copyFile(join(siteDirectory, "_redirects"), join(outputDirectory, "_redirects"));
console.log(`Generated static invite site in ${outputDirectory}`);
