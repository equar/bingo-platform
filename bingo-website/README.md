# Sunset Bingo Website

This project is a separate static landing site for Sunset Bingo. It does not use React, a database, or backend APIs.
The output is a static Astro build in `dist/`.

## What this site covers

- Sunset Bingo introduction and Android install guidance
- Download page with centrally managed APK metadata
- Help/contact page with verified contact channels only
- Draft privacy notice based on implemented backend behavior
- Draft terms and game rules based on implemented backend behavior

## Local development

```bash
npm install
npm run dev
```

## Production build

```bash
npm run build
```

The build output is written to `dist/`.

## Checks

```bash
npm run check
npm run validate:links
```

`npm run check` runs Astro's project checks. `npm run validate:links` validates the configured public URLs and
ensures missing links stay intentionally unavailable instead of becoming placeholders.

## Configuration

Public configuration lives in `src/config/site.ts` and is driven by environment variables:

- `PUBLIC_SITE_URL` - canonical public site URL
- `PUBLIC_SITE_INDEXABLE` - set to `true` only after an approved public release
- `PUBLIC_ANDROID_APK_URL` - verified APK URL
- `PUBLIC_APP_VERSION` - app version string
- `PUBLIC_ANDROID_APK_SIZE` - file size string
- `PUBLIC_ANDROID_RELEASE_DATE` - release date string
- `PUBLIC_ANDROID_APK_SHA256` - optional SHA-256 checksum
- `PUBLIC_SUPPORT_EMAIL` - support email address
- `PUBLIC_SUPPORT_TELEGRAM_URL` - optional Telegram link
- `PUBLIC_SUPPORT_WHATSAPP_URL` - optional WhatsApp link

If a value is missing, the site falls back to a visible unavailable state instead of inventing data.

## Indexing

Staging and test deployments are noindex by default. To enable indexing for an approved public release, set
`PUBLIC_SITE_INDEXABLE=true` and provide the production `PUBLIC_SITE_URL` before building.

## Deployment fit

The generated `dist/` folder can fit the existing deployment approach as a static artifact. It can be served from the
same style of web host or nginx layer used for the other frontend outputs without adding a backend dependency.
