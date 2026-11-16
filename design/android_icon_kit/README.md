# QuizGate — Complete Android Icon Kit

This ZIP contains **three complete, separate sets** based on the retained QuizGate masters:

- `A_gate_question/` — welcoming arch with question mark (suggested main launcher)
- `B_lock_numbers/` — cheerful gold lock with math blocks
- `C_open_gate_success/` — open gate and success star/check

Each set has its own `android_res/`, `wide/`, `transparent/`, `mask_previews/`, `play_store/`, and source assets.

## Install in an Android app

1. Choose ONE design (A, B, or C). Copy the **contents** of its `android_res/` directory into `app/src/main/res/` and merge folders. Do not install multiple sets into the same resource directory, since their resource names intentionally match.
2. Set the icon resources in `AndroidManifest.xml`:

   ```xml
   <application android:icon="@mipmap/ic_launcher"
                android:roundIcon="@mipmap/ic_launcher_round" ... />
   ```
3. On Android 8.0+, the generated `mipmap-anydpi-v26/ic_launcher.xml` uses separate adaptive foreground and background with correct size buckets. The `monochrome` layer enables Android 13+ themed icons.
4. Notifications: use `R.drawable.ic_stat_quizgate` for the status bar's `smallIcon`, **not** the colorful launcher PNG. Android will tint the white/transparent silhouette.
5. If building for Android TV, set `android:banner="@drawable/quizgate_tv_banner"` as appropriate for the TV launcher, and include a compatible Leanback TV activity. This is optional for phone/tablet apps.

## Resource dimensions

| Density | mdpi | hdpi | xhdpi | xxhdpi | xxxhdpi |
|---|---:|---:|---:|---:|---:|
| Legacy launcher icon | 48 | 72 | 96 | 144 | 192 |
| Adaptive foreground/background/monochrome | 108 | 162 | 216 | 324 | 432 |
| Notification smallIcon | 24 | 36 | 48 | 72 | 96 |

**Master:** 1024×1024. **Play Store listing:** 512×512 opaque RGB. **TV banner:** 320×180. **Google Play feature artwork:** 1024×500.

## Transparency and wide icons

- `foreground_1024_transparent.png`: actual RGBA alpha, not a checkerboard.
- `monochrome_1024_white_transparent.png`: solid white silhouette + transparent outside.
- `transparent/`: cutout-only artwork in 1024/512/256/192/128/96/64/48 sizes, plus black and white monochrome silhouettes.
- `wide/`: feature 1024×500, TV 320×180, TV HD 1280×720, 2:1 1024×512, ultrawide 1600×600, and landscape 1200×675. **Each** has an opaque and `_transparent` PNG version.
- `A_gate_question/wide/bonus_illustrated_promo`: optional wider, more detailed illustrated promotional artwork. The release icon masters are unchanged.
- `mask_previews/`: circle, squircle, teardrop, and square simulations. These are previews, **not** replacements for adaptive XML.

Keep important elements inside the approximate 66% adaptive-icon center. Production adaptive icons should use foreground/background resources and let the OS apply the mask. Android TV and Play listing banners are separate from Android phone launcher icons; the phone launcher itself is square/adaptive.

## Key resources

- `mipmap-*/ic_launcher.png` and `ic_launcher_round.png`: legacy fallback
- `mipmap-*/ic_launcher_foreground.png`, `ic_launcher_background.png`, `ic_launcher_monochrome.png`: adaptive layers
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`: adaptive definitions
- `drawable-*/ic_stat_quizgate.png`: notification status icon
- `drawable-nodpi/quizgate_tv_banner.png`: optional Android TV launcher banner
- `drawable-nodpi/quizgate_splash_icon.png`: optional splash symbol

Use `asset_manifest.json` for the icon variant identifiers. Editable vector SVGs are supplied alongside the masters. No faces, text or branding within app icons.
