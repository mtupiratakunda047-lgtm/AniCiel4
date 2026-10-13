# AniCiel MediaHub NEW — provenance

## Only inputs
1. **MediaHub UI mockup images** — design, hubs, adult age gate
2. **Google Drive base.apk** — full wefeed API surface (filtered)

## base.apk identity
- Packages: com.community.oneroom / com.community.moviebox
- File: base.apk (~29.4 MB) from Google Drive

## Counts
- Total wefeed paths in APK: 124
- **Allowed (wired):** 91
- **Excluded:** 33 (money, vip, ad, activity, fission, login/register/sms)

## Excluded on purpose
- /wefeed-mobile-bff/money/*
- /wefeed-mobile-bff/vip/*
- /wefeed-mobile-bff/ad/*
- /wefeed-mobile-bff/activity/*
- /wefeed-mobile-bff/fission/*
- user-api login, register, third-login, sms, password reset

## Still available (optional social, not VIP)
- comments, feedback, groups, posts, shorts, learning, share, playlist
- user-api profile/info/logout/modify/block (no login UI required to ship)
