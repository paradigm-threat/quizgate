# QuizGate - Website, Favicon, PWA and Theme Kit

Complete web assets based on the retained QuizGate Android vector masters.

## Quick start (zero dependencies)

1. Open `website/index.html` in a browser to preview the page. For service-worker and PWA testing, serve `website/` over HTTPS or localhost.
2. Run `python3 -m http.server 8000 --directory website` from this folder and visit `http://localhost:8000`.
3. Try Sunshine, Sky, Twilight and High Contrast. Switch between Gate, Lock, and Open Gate icons. The quiz exercise is an interactive **design demo**, not a working parental-control/screen-time enforcement feature.

## Folder structure

- `website/` - ready-to-use responsive single-page demo with no build or third-party dependencies; `css/themes.css`, `css/site.css`, `js/main.js`, manifest, favicon and PWA assets.
- `icon-packs/gate`, `icon-packs/lock`, `icon-packs/open` - complete, separate favicon, Apple, PWA, maskable, SVG, transparent raster, social preview and wordmark packs.
- `design-tokens/themes.css`, `design-tokens/themes.json` - 4 CSS-variable-based color schemes.
- `nextjs-starter/` - App Router metadata, default icons and styles; copy these into an existing Next.js project.
- `source-hero-artwork.png` - full-resolution optional illustrated hero background.

## Favicon / website head tags

Add this in `<head>` after copying the chosen `icon-packs/<variant>/` files to the root of your website:

```html
<link rel="icon" href="/favicon.ico" sizes="any">
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
<link rel="manifest" href="/site.webmanifest">
<meta name="theme-color" content="#0A91D9">
<link rel="mask-icon" href="/safari-pinned-tab.svg" color="#087FC0">
<meta property="og:image" content="https://YOUR-DOMAIN/social-og-1200x630.png">
```

`https://YOUR-DOMAIN/...` is a placeholder: replace it with a real absolute URL when you deploy. If the manifest is moved to another directory, update its icon paths and scope accordingly.

## Pack contents (per concept)

- `favicon.ico` = 16/32/48 resolution multi-image ICO; `favicon.svg`; 16/32/48/64/96/128/256 standalone PNGs.
- `apple-touch-icon.png` = 180x180 opaque PNG.
- `pwa-192.png`, `pwa-512.png` = opaque PWA icons.
- `pwa-maskable-192.png`, `pwa-maskable-512.png` = maskable PWA icons, safely padded inside central maskable safe zone.
- `icon-transparent-{64,128,180,192,256,512,1024}.png` = actual RGBA transparency.
- `icon-transparent.svg` = simplified transparent logo mark; `original-foreground.svg` = original editable master vector.
- `logo-horizontal-*`, `logo-stacked-*` (color and white): transparent SVG and PNG wordmarks.
- `wide-wordmark-transparent-1200x400.png` = wide transparent masthead branding.
- `social-og-1200x630.png` = share/social thumbnail; `safari-pinned-tab.svg` = one-color silhouette.

## Theme usage

```html
<html lang="en" data-theme="sunshine">
<link rel="stylesheet" href="/css/themes.css">
```

Change `document.documentElement.dataset.theme = 'sky'` (or `twilight`, `contrast`). Use semantic CSS variables like `background:var(--surface); color:var(--text)` and `background:var(--primary);color:var(--primary-text)`. Theme switching is implemented in the demo JS and persisted locally. It uses no external services or tracking.

## Browser / PWA notes

- Modern Chromium uses `favicon.svg` where available, ICO provides older browser compatibility. Changing the sample icon is for visual preview only; its installed PWA manifest still defaults to Gate + Question.
- Apple homescreen icon is opaque as expected by iOS.
- PWA manifest has both standard and maskable 192/512 PNG icon purposes. A small service worker caches demo assets for offline reloads on HTTPS/localhost.
- Browser tab favicons are intentionally simplified for small-size legibility; they visually match the Android masters rather than attempting to squeeze detailed 1024px artwork into 16px.
- Notification icons and Android resource density folders are not repeated here; they are in the prior Android icon package.

## Accessibility and integration

Use descriptive labels, visible keyboard focus, color+text feedback and 44px+ interactive targets. The high-contrast theme intentionally makes outlines stronger. The quiz example does not implement actual screen time enforcement. Test on your deployment host, including favicon cache behavior and installed PWA masks.
