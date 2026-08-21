# Versions

Every release, newest first. The archive of each is attached to its own release,
so an older version stays installable after a newer one ships — which is what
`updatePlugins.xml` cannot do: a feed answers "what should this IDE install now",
and the plugin manager shows one card per plugin with no version picker.

**To install an older version:** download its `zip` and use Settings | Plugins |
⚙ | *Install Plugin from Disk…*. The plugin repository feed always points at the
newest, so a downgrade has to be explicit.

| Version | Date | IDEs | What it brought | |
|---|---|---|---|---|
| **0.9.1** | 2026-08-20 | 2025.1+ | the documentation link reads instead of pointing | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.9.1/laravel-api-idea-0.9.1.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.9.1) |
| **0.9.0** | 2026-08-20 | 2025.1+ | exporting the endpoint under the caret | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.9.0/laravel-api-idea-0.9.0.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.9.0) |
| **0.8.0** | 2026-08-20 | 2025.1+ | the endpoint's own page in the reference | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.8.0/laravel-api-idea-0.8.0.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.8.0) |
| **0.7.0** | 2026-08-19 | 2025.1+ | the version, and which PHP | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.7.0/laravel-api-idea-0.7.0.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.7.0) |
| **0.6.2** | 2026-08-19 | 2025.1+ | a clean compatibility check | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.6.2/laravel-api-idea-0.6.2.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.6.2) |
| **0.6.1** | 2026-08-19 | 2025.1+ | the page describes the plugin again | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.6.1/laravel-api-idea-0.6.1.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.6.1) |
| **0.6.0** | 2026-08-19 | 2025.1+ | markup against validation | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.6.0/laravel-api-idea-0.6.0.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.6.0) |
| **0.5.0** | 2026-08-19 | 2025.1+ | writing the missing action method | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.5.0/laravel-api-idea-0.5.0.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.5.0) |
| **0.4.0** | 2026-08-18 | 2025.1+ | api:lint from the IDE | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.4.0/laravel-api-idea-0.4.0.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.4.0) |
| **0.3.0** | 2026-08-18 | 2025.1+ | navigating the markup | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.3.0/laravel-api-idea-0.3.0.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.3.0) |
| **0.2.0** | 2026-08-18 | 2025.1+ | checks became inspections | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.2.0/laravel-api-idea-0.2.0.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.2.0) |
| **0.1.1** | 2026-08-18 | 2025.1+ | two green tests over features that did not work | [zip](https://github.com/dskripchenko/laravel-api-idea/releases/download/v0.1.1/laravel-api-idea-0.1.1.zip) · [notes](https://github.com/dskripchenko/laravel-api-idea/releases/tag/v0.1.1) |

`0.1.0` is missing on purpose: it was the first upload and predates tagging, so
there is no commit to rebuild it from. Naming it here without an archive would
be a row that promises something.

Compatibility has been `2025.1+` for every release so far, with no upper bound.
When that changes, see the note on compatibility ranges in the
[README](../README.md#compatibility-ranges-in-the-feed) — the feed then has to
carry more than one entry, and this table is where a reader finds out which
version was the last for their IDE.
