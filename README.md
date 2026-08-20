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

Six releases in, and everything the roadmap opened with is delivered. What the
plugin does today, grouped by when you meet it.

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

Every check is an ordinary inspection: severity, suppression and the off switch
live in Settings | Editor | Inspections, under *Laravel API*. The plugin's own
page, Settings | Tools | Laravel API, carries one field — the PHP interpreter to
run `api:lint` with. It exists because the PHP plugin does not publish its
interpreter configuration to other plugins, so "the PHP this project uses" is
not a question this plugin can ask the IDE. Left empty, it searches the login
shell's PATH as before.

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

From JetBrains Marketplace — Settings | Plugins | Marketplace, search for
*Laravel API*.

Or from the archive: every [release](https://github.com/dskripchenko/laravel-api-idea/releases)
carries `laravel-api-idea-<version>.zip`, and Settings | Plugins | ⚙ | *Install
Plugin from Disk…* takes it. That is the way in when the IDE cannot reach the
registry — a closed network — or when a version needs to be rolled back to, which
the Marketplace does not offer.

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

## Publishing

The first version cannot be published from here: JetBrains Marketplace accepts
it only through the web form and reviews it by hand. Everything after that goes
by tag.

```bash
./gradlew publishPlugin        # needs JETBRAINS_MARKETPLACE_TOKEN
```

CI is split by what a check actually answers: `build.yml` runs the tests on
every push, while `verify.yml` runs the IDE-compatibility verifier weekly and on
demand. The split is measured rather than guessed — with the verifier inline a
push took 13.5 minutes, of which 6.5 went on downloading six IDE distributions
and three more on storing the cache they left behind. Its answer changes when
JetBrains ships a platform release, not when this repository gets a commit.

`.github/workflows/publish.yml` does the same on `v*` tags, after checking that
the tag and the version in `build.gradle.kts` agree — a tag may well sit on a
release that is not meant for the registry. It runs the tests and the verifier
first: a tag can point at a commit the branch CI never saw, and a version cannot
be withdrawn from the registry once published.

Signing is optional. Marketplace signs unsigned uploads itself; the
`PLUGIN_CERTIFICATE_CHAIN` / `PLUGIN_PRIVATE_KEY` / `PLUGIN_PRIVATE_KEY_PASSWORD`
variables exist so that the archive can be proven to have left this machine
untampered. Without them the signing task simply has nothing to do.

The change notes on the listing come from `CHANGELOG.md` — the section matching
the version being built. Releasing a version the changelog does not mention
fails the build rather than publishing an empty "what's new".

## License

MIT © Denis Skripchenko
