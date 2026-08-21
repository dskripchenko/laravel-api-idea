#!/usr/bin/env python3
"""
Rebuilds docs/versions.md from what the releases actually say.

Run by CI after a release is published, so the table cannot drift from the
archives it points at. It is deliberately a rebuild rather than an append: a
release edited or deleted by hand should show up here as well.
"""
import json
import pathlib
import re
import subprocess

REPO = 'dskripchenko/laravel-api-idea'


def run(*command: str) -> str:
    return subprocess.run(list(command), capture_output=True, text=True, check=True).stdout


def version_key(tag: str) -> list[int]:
    return [int(part) for part in tag.lstrip('v').split('.')]


releases = json.loads(run(
    'gh', 'release', 'list', '--repo', REPO, '--limit', '100',
    '--json', 'tagName,name,publishedAt',
))
releases.sort(key=lambda release: version_key(release['tagName']), reverse=True)

rows = []
for release in releases:
    tag = release['tagName']
    version = tag.lstrip('v')

    # The tag's date rather than the release's: a release page can be created
    # long after the version was cut, and the table is about when it was made.
    date = run('git', 'log', '-1', '--format=%cs', tag).strip()

    title = release['name'] or tag
    summary = re.sub(r'^' + re.escape(version) + r'\s*—\s*', '', title)

    archive = f'https://github.com/{REPO}/releases/download/{tag}/laravel-api-idea-{version}.zip'
    notes = f'https://github.com/{REPO}/releases/tag/{tag}'

    rows.append(f'| **{version}** | {date} | 2025.1+ | {summary} | [zip]({archive}) · [notes]({notes}) |')

header = """# Versions

Every release, newest first. The archive of each is attached to its own release,
so an older version stays installable after a newer one ships — which is what
`updatePlugins.xml` cannot do: a feed answers "what should this IDE install now",
and the plugin manager shows one card per plugin with no version picker.

**To install an older version:** download its `zip` and use Settings | Plugins |
⚙ | *Install Plugin from Disk…*. The plugin repository feed always points at the
newest, so a downgrade has to be explicit.

| Version | Date | IDEs | What it brought | |
|---|---|---|---|---|
"""

footer = """
`0.1.0` is missing on purpose: it was the first upload and predates tagging, so
there is no commit to rebuild it from. Naming it here without an archive would
be a row that promises something.

Compatibility has been `2025.1+` for every release so far, with no upper bound.
When that changes, see the note on compatibility ranges in the
[README](../README.md#compatibility-ranges-in-the-feed) — the feed then has to
carry more than one entry, and this table is where a reader finds out which
version was the last for their IDE.
"""

path = pathlib.Path('docs/versions.md')
path.write_text(header + '\n'.join(rows) + '\n' + footer, encoding='utf-8')
print(f'{len(rows)} versions written to {path}')
