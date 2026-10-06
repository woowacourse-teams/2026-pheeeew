# Invite store-router page

This is a static page for `https://invite.pheeeew.com/invite`. It sends iPhone and iPad visitors to the App Store and Android visitors to Google Play. The page also shows both store links so desktop users and browsers without JavaScript can choose manually.

Maintain store destinations in [`store-links.json`](store-links.json). Run `node build.mjs` from this directory to generate `dist/index.html` and `dist/_redirects`. The generated HTML keeps the store links available without JavaScript, while the redirect script reads those same links from the page.

The share text carries the group name, invite code, and this URL. The link does not open the installed app or contain the invite code. Recipients copy the code from the message and enter it after installing or opening the app. No backend API, Android App Links, or iOS Universal Links are required for this store-router flow.

## Cloudflare Pages deployment

Connect the GitHub repository to Cloudflare Pages after this change is available on the branch selected for deployment. Use these settings for this monorepo:

| Setting | Value |
| --- | --- |
| Project name | `pheeeew-invite` (or another available lowercase name) |
| Production branch | `an/dev` while that is the intended release source |
| Root directory | Leave at the repository root |
| Build command | `node AN/Pheeeew/invite-site/build.mjs` |
| Build output directory | `AN/Pheeeew/invite-site/dist` |

For a Direct Upload project, run `node build.mjs` from `AN/Pheeeew/invite-site` and upload the generated `dist` directory. The rule in `_redirects` serves `index.html` for `/invite`. Before attaching the custom domain, verify the deployment at `https://<project>.pages.dev/invite` on desktop, iOS, and Android.

Use the Pages project’s **Custom domains** flow to attach `invite.pheeeew.com`; when the zone is already in the same Cloudflare account, Pages can create the corresponding DNS record during setup. Do not manually point the DNS name at `pages.dev` before associating the hostname with the Pages project. See [Pages custom domains](https://developers.cloudflare.com/pages/configuration/custom-domains/) and [static HTML deployment](https://developers.cloudflare.com/pages/framework-guides/deploy-anything/).

Git integration is preferred so future changes deploy from the selected branch. Cloudflare does not let a Direct Upload project switch to Git integration later; see [Direct Upload](https://developers.cloudflare.com/pages/get-started/direct-upload/).
