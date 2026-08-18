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

Early. What works today:

- **Highlighting** of `@input`, `@output`, `@header`, `@response`, `@security`,
  `@default`, `@example` — types, formats, variables, template references and
  status codes are coloured, so a line the generator no longer understands stops
  looking like the ones it does.
- **Markup that does not parse** is reported where it is written, instead of
  vanishing from the generated spec.
- **Unknown types** are flagged with the consequence named: the generator will
  call the field `string`.
- **Navigation**: Ctrl+Click on `{UserResponse}`, `@OrderRequest` or
  `[buildInputs]` goes to the key in `getOpenApiTemplates()` or to the
  controller's own method.
- **Templates nobody declared** are underlined where they are written, instead
  of surfacing later as a `$ref` into nothing in a published spec.

- **Gutter arrows both ways** between the route map and the controllers: from
  an action's key to the method it routes, and from a method back to every
  action routing it. An action with no arrow points at a method that is not
  there.
- **An action pointing at a missing method is an error** — the single defect
  worth building this for. At runtime it answers 404, the same 404 as a mistyped
  URL, so nothing distinguishes "this endpoint is gone" from "someone asked for
  nonsense". A method that exists but is not public, or is static, is reported
  the same way: `app()->call()` cannot reach it either.

- **`@security` checked** against `getOpenApiSecurityDefinitions()`, with
  Ctrl+Click to the declaration. Silent while a project declares no schemes at
  all: that application has evidently not taken the feature up, and painting
  every tag red would teach people to look past the plugin rather than at it.
- **Completion** of types, formats, template names, security schemes and status
  codes — and, deliberately, of nothing at all once the caret has moved past the
  variable into the description.

- **A quick fix that declares the missing template.** Alt+Enter on the red name
  writes `'Name' => []` into `getOpenApiTemplates()` — creating the method too
  when there is none — with the name taken verbatim from the docblock. Retyping
  it by hand in another file is where a second, slightly different spelling
  comes from, and that spelling reads as "declared" to a person and "missing" to
  the generator. Vendored classes are skipped, and with several API versions the
  target is asked for rather than guessed.

Planned: publication to the JetBrains Marketplace.

One implementation note worth recording: navigation is a `GotoDeclarationHandler`
rather than a `PsiReference`. `PhpDocTagImpl` declares itself a
`ContributedReferenceHost` and then overrides `getReferences()` to ignore
contributed ones — the providers do run, but nothing asks them through the tag,
so Ctrl+Click sees nothing.

Nothing fires unless the project actually has `Dskripchenko\LaravelApi` on its
classpath — `@input` and `@output` are ordinary words elsewhere.

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
