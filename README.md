# Laravel API — PhpStorm plugin

IDE support for [`dskripchenko/laravel-api`](https://github.com/dskripchenko/laravel-api):
the docblock markup its OpenAPI generator reads, and the route map its
`getMethods()` declares.

## Why

The package fails quietly by design. An action pointing at a renamed controller
method answers **404** — the same 404 as a mistyped URL. A `@response 200
{UserResponse}` naming a template that was never defined becomes a `$ref` into
nothing, in a spec that still validates. PhpStorm shows all of it as one grey
blob of prose, because the tags are not part of any grammar it knows.

`api:lint` (shipped with the package since 5.7.0) catches this in CI. This
plugin catches it while typing.

## Status

At 1.0.0, and everything the roadmap opened with is delivered. The public
surface — the markup grammar, the inspections and their ids, the settings keys —
is what it will be. What the plugin does today, grouped by when you meet it.

**While you type**

- **Highlighting** of `@input`, `@output`, `@header`, `@response`, `@security`,
  `@default`, `@example` — types, formats, variables, template references and
  status codes are coloured, so a line the generator no longer understands stops
  looking like the ones it does.
- **Markup that does not parse** is reported where it is written, instead of
  vanishing from the generated spec.
- **Unknown types** are flagged with the consequence named: the generator will
  call the field `string`.
- **Markup checked against the validation rules.** A field the method validates
  and the docblock never mentions is reported on the rule itself, with a quick
  fix that writes the tag from it: `email` → `string(email)`, no `required` → an
  optional field, `in:a,b` → an enumeration, `items.*.variables` →
  `$items[].variables`. The description is left for a person. Nothing is said
  about rules assembled at runtime — see the note below.
- **Templates nobody declared** are underlined where they are written, instead
  of surfacing later as a `$ref` into nothing in a published spec, with a quick
  fix that writes `'Name' => []` into `getOpenApiTemplates()` — creating the
  method too when there is none — with the name taken verbatim from the
  docblock. Retyping it by hand in another file is where a second, slightly
  different spelling comes from, and that spelling reads as "declared" to a
  person and "missing" to the generator.
- **An action pointing at a missing method is an error** — the single defect
  worth building this for. At runtime it answers 404, the same 404 as a mistyped
  URL, so nothing distinguishes "this endpoint is gone" from "someone asked for
  nonsense". A method that exists but is not public, or is static, is reported
  the same way: `app()->call()` cannot reach it either. Alt+Enter writes the
  method, placed and shaped like the ones already in the class.
- **`@security` checked** against `getOpenApiSecurityDefinitions()`. Silent while
  a project declares no schemes at all: that application has evidently not taken
  the feature up, and painting every tag red would teach people to look past the
  plugin rather than at it.
- **Completion** of types, formats, template names, security schemes and status
  codes — and, deliberately, of nothing at all once the caret has moved past the
  variable into the description.

**Getting around**

- **Navigation**: Ctrl+Click on `{UserResponse}`, `@OrderRequest` or
  `[buildInputs]` goes to the key in `getOpenApiTemplates()` or to the
  controller's own method.
- **Find Usages on a template declaration** lists every docblock naming it, with
  a gutter arrow from the declaration to them. A template read only by the
  generator otherwise looks unused to the IDE.
- **Gutter arrows both ways** between the route map and the controllers: from
  an action's key to the method it routes, and from a method back to every
  action routing it. An action with no arrow points at a method that is not
  there.
- **An `API` line above every routed method**, with everything that endpoint
  offers under it: its own page in the reference documentation, and the endpoint
  as a request in Bruno, cURL, HTTP Client, Postman or Markdown. The gutter icon
  on the route map's action key opens the same list.

  Neither half can be assembled by hand. The documentation address comes from
  four places at once — the version from the module, the path from the URI
  pattern, the tag from the controller key, the method from the map — and the
  request can only be produced by the application. The list holds what is
  actually possible, so a missing item is the answer to why: a version built at
  runtime has no address, one kept out of the reference index is never loaded by
  the page, a project without `artisan` has nothing to export with.
- **An endpoint list** in a tool window — `v2.order.create → create()`,
  searchable, double-click opens the code. That is the name the package
  registers the route under, minus its `api.` prefix. The version comes from the
  module's `getApiVersionList()`, not from the Api class: without it two
  versions declaring the same controller produce identical lines. Versions a
  module builds at runtime — in a loop, from a registry — cannot be read
  statically, and those rows say nothing rather than guessing.

**On demand**

- **`api:lint` run from the IDE**, its findings clickable. The command carries
  far more rules than an editor can afford to evaluate on every keystroke, and
  asking the application is cheaper than writing them twice.
- **The endpoint under the caret exported as a request**, the same as the `API`
  line does, for anyone who reaches for the context menu instead. It opens in a
  scratch file, ready to send or to save where the collection lives. The
  application produces it through `api:export`; what the plugin adds is knowing
  which endpoint is meant — that the method under the caret is routed as
  `v1.order.create`, by that version and under that controller key.

Every check is an ordinary inspection: severity, suppression and the off switch
live in Settings | Editor | Inspections, under *Laravel API*. The plugin's own
page, Settings | Tools | Laravel API, carries two fields, and both are questions
nothing in a repository can answer: the PHP interpreter to run `api:lint` with —
the PHP plugin does not publish its interpreter configuration to other plugins,
so "the PHP this project uses" is not a question this plugin can ask the IDE —
and the address the documentation is served from, read from `APP_URL` unless a
different one is wanted.

Two implementation notes worth recording.

Navigation is a `GotoDeclarationHandler` rather than a `PsiReference`.
`PhpDocTagImpl` declares itself a `ContributedReferenceHost` and then overrides
`getReferences()` to ignore contributed ones — the providers do run, but nothing
asks them through the tag, so Ctrl+Click sees nothing.

The comparison against validation reads only fully literal rule arrays. A
`array_merge`, a `Rule::when`, rules pulled from config: the whole array is
discarded rather than half-read, because a field missing from a partial reading
looks exactly like a field nobody documented. On the codebases this was built
against, one method in fifty-three writes its rules that way.

How each of those arrived, release by release, is in
[docs/roadmap.md](docs/roadmap.md) — including the lessons the platform
taught the hard way.

Nothing fires unless the project actually has `Dskripchenko\LaravelApi` on its
classpath — `@input` and `@output` are ordinary words elsewhere.

## Installing

The plugin is distributed from this repository. It is not on JetBrains
Marketplace: publication there was refused to the author, over where he once
worked rather than over anything in the code, and the refusal is final. Nothing
about the plugin changes because of it — but the two steps below replace the one
step the Marketplace would have been.

**As a plugin repository — recommended, because updates keep arriving.**
Settings | Plugins | ⚙ | *Manage Plugin Repositories…* | **+**, and paste:

```
https://raw.githubusercontent.com/dskripchenko/laravel-api-idea/main/updatePlugins.xml
```

*Laravel API* then appears in the Marketplace tab and installs from there, and
every later version shows up as an ordinary update notification. The feed is
written by CI on each release, so it points at exactly what was published.

**Or from the archive**, when a machine may not reach GitHub at all: every
[release](https://github.com/dskripchenko/laravel-api-idea/releases) carries
`laravel-api-idea-<version>.zip`, and Settings | Plugins | ⚙ | *Install Plugin
from Disk…* takes it. This is also how to go back a version — an update feed
only ever offers the newest. Every version that ever shipped is listed, with its
archive, in [docs/versions.md](docs/versions.md).

## Which IDEs

Verified with the JetBrains plugin verifier, not assumed:

| IDE | Verdict |
|---|---|
| PhpStorm 2025.1 – 2026.2 | compatible |
| IntelliJ IDEA **Ultimate** | compatible — the PHP plugin has to be installed |
| IntelliJ IDEA **Community** | will not install |

The whole plugin hangs off the PHP plugin's PSI, so it declares
`<depends>com.jetbrains.php</depends>` and can only load where that plugin
exists. JetBrains publishes it for PhpStorm (bundled) and for IDEA Ultimate; for
Community it does not, and the verifier says so plainly — `com.jetbrains.php`
cannot be resolved from the bundled plugins or from Marketplace.

Other IDEs that can install the PHP plugin (WebStorm cannot; the rest of the
family was not checked) would follow the same rule: the plugin loads exactly
where its dependency does.

## Building

```bash
./gradlew buildPlugin     # → build/distributions/
./gradlew test
./gradlew runIde          # a sandbox IDE with the plugin loaded
```

The build compiles against a pinned, downloaded PhpStorm — 2025.2 by default,
overridable with `-PphpstormVersion=…`. Local and CI therefore build against the
same thing.

Compiling against the IDE installed on this machine was tried and dropped. It
saved the download and cost more than it saved: a PhpStorm update changed the
module descriptor format, the local build stopped resolving its own
dependencies, and CI — which has no IDE at all — kept working the whole time.
A build that only breaks on the author's machine is the worst kind.

`sinceBuild` is 251 while the compile target is 252. That is deliberate and
checked: the verifier reports PS-251 as compatible, so the lower bound is real
rather than hopeful. Gradle warns about the pair; the warning is the price of a
supported range wider than the build target.

## Releasing

Everything goes by tag, and the tag is the only trigger: `release.yml` checks
that the tag and the version in `build.gradle.kts` agree — a tag may well sit on
a commit that is not meant to be a release — then runs the tests and the IDE
verifier, builds and signs the archive, publishes the release, and rewrites
`updatePlugins.xml` to point at it.

The order matters. A tag can point at a commit the branch CI never saw, and a
release can be deleted but not un-downloaded.

CI is otherwise split by what a check actually answers: `build.yml` runs the
tests on every push, while `verify.yml` runs the IDE-compatibility verifier
weekly and on demand. The split is measured rather than guessed — with the
verifier inline a push took 13.5 minutes, of which 6.5 went on downloading six
IDE distributions and three more on storing the cache they left behind. Its
answer changes when JetBrains ships a platform release, not when this repository
gets a commit.

**The archives are not signed today.** JetBrains Marketplace used to sign every
upload itself, so no key was ever set up here, and outside it nothing does —
`signPlugin` skips when `PLUGIN_CERTIFICATE_CHAIN` / `PLUGIN_PRIVATE_KEY` /
`PLUGIN_PRIVATE_KEY_PASSWORD` are absent. Configure those three and the release
signs itself with no further change.

Until then each release carries a `.sha256` beside its archive, and the workflow
says out loud in its log that what it published is unsigned. A checksum is not a
signature — it proves the file was not altered in transit, not who built it —
but it is what an unsigned download can be checked against, and silence would
have been worse.

The release notes come from `CHANGELOG.md` — the section matching the version
being built. Tagging a version the changelog does not mention fails the build
rather than publishing an empty "what's new".

### Compatibility ranges in the feed

`updatePlugins.xml` currently carries one entry, because every version so far
declares `since-build="251"` and no upper bound: one archive suits every IDE the
plugin supports.

That stops being true the day `sinceBuild` is raised — when a platform API the
plugin needs stops existing in older IDEs, say. From then on the feed has to
carry **one entry per compatibility range**, with the ranges disjoint:

```xml
<plugins>
  <!-- The last version that worked on the older branch. -->
  <plugin id="dev.dskripchenko.laravel-api" url="…/v1.4.2/laravel-api-idea-1.4.2.zip" version="1.4.2">
    <idea-version since-build="251" until-build="253.*"/>
  </plugin>

  <!-- Everything from here on. -->
  <plugin id="dev.dskripchenko.laravel-api" url="…/v2.0.0/laravel-api-idea-2.0.0.zip" version="2.0.0">
    <idea-version since-build="261"/>
  </plugin>
</plugins>
```

The IDE reads the feed with its own build number in hand and keeps only what
matches — `RepositoryHelper.loadPlugins(url, buildNumber, …)` — so a PhpStorm
2025.3 sees 1.4.2 and a 2026.1 sees 2.0.0, each as an ordinary update. Neither
is told the other exists.

Two things this is **not** for. It is not a version history: the plugin manager
shows one card per plugin id and has no version picker, so overlapping entries
mean the IDE picks one and the rest are invisible. And it is not how a user goes
back a version — that is an archive from [docs/versions.md](docs/versions.md)
and *Install Plugin from Disk…*.

When the split happens, the release workflow has to stop overwriting the whole
file and start replacing only the entry whose range matches the version being
released. Until then, one entry, rewritten each time.

## License

MIT © Denis Skripchenko
