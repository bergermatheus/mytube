# MyTube constitution

## Purpose

MyTube exists because watching videos should not mean sitting through repeated advertising interruptions. It is a free, open-source YouTube client for Google TV and Android TV, built around a familiar television interface and the viewer's choices. Anyone can study the source, improve it and contribute under GPL-3.0-or-later.

MyTube is an independent application. It is not Google's official YouTube app, an open-source release of Google's code or an affiliated Google product.

## Principles

1. **Keep watching free of inserted ads.** Do not add advertising SDKs, sponsored placements, pre-roll/mid-roll ads or paid interruptions to the app. Sponsor segments embedded by creators are part of the video; automatic sponsor skipping is a separate, currently unimplemented feature.
2. **Let people watch without a required account.** Browsing, playback, subscriptions, history and preferences work without Google sign-in. A local profile is a name/photo on the device, not an online account.
3. **Keep personal state local.** Subscriptions, interests, history, progress and reactions belong to the viewer. Do not add telemetry, cloud synchronization or a mandatory MyTube backend by default. Explain network use accurately: fetching videos, images, metadata and search suggestions contacts YouTube and its delivery services.
4. **Make the remote sufficient.** Every supported TV flow must work with the D-pad, Select and Back. Focus must be visible and predictable. Selecting a video starts playback directly. Do not require touch, a mouse or a phone to perform ordinary navigation.
5. **Keep navigation responsive.** Render page structure independently from loading data. Retain useful page state, bound caches and append stable video batches. Pending requests must not block navigation or move existing cards. Measure improvements and report their limits.
6. **Respect the viewer's library.** Preserve existing data with additive migrations and in-place updates. Never erase preferences, uninstall the app or clear its data as an ordinary troubleshooting step.
7. **Keep the source independent and open.** Use original implementation and properly licensed dependencies. Do not bundle official YouTube APKs, extracted executable code, private keys or copied account/session data. Maintain the GPL license and dependency notices.
8. **Welcome contributions.** UI, accessibility, translations, extraction compatibility, testing, documentation and bug reports are all useful contributions. Keep decisions understandable and review changes on their merits.
9. **Describe what actually works.** Distinguish implemented features, plans, source-derived conclusions and device observations. Report skipped tests, unsupported content and unavailable evidence. A familiar design does not establish identical internals or complete YouTube feature parity.

## Applying the constitution

These principles guide maintainers and agents. Implementation context lives in [AGENT.md](AGENT.md); startup instructions live in [README.md](README.md). Changes to product direction should explain their effect on these principles and be reviewed with the feature that needs them. Routine reversible implementation, documentation and verification do not need a separate approval ceremony.
