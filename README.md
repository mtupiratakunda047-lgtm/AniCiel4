# AniCiel MediaHub (NEW)

Fresh AniCiel lineage — **not** the Netplay-based tree.

## Inputs only
1. MediaHub UI mockup images (design + adult gate + nav)
2. Google Drive `base.apk` (MovieBox / OneRoom wefeed APIs)

## Policy
- No account sign-in UI
- No VIP / coins / tokens
- No ads surfaces
- Adult section age-gated (18+)
- Downloads → public `/storage/emulated/0/Download/AniCiel`

## Layout
```
SOURCES.md
sources/          # base.apk API dumps
app/              # Theme, nav, adult gate, public downloads, source registry
engine/           # MovieBox path constants from base.apk
docs/
```

## How to use
Merge `app/` and `engine/src/providers/moviebox/paths.rs` into a clean Android+Rust project
(or replace corresponding files in a fork that is **not** the Netplay-patched AniCiel-main).

This zip is a **foundation kit**, not a full standalone APK. Wire `paths.rs` into your
MovieBox client and apply the Compose theme/nav/gate screens.
