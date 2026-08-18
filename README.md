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

Planned, in order: `@security` schemes checked against
`getOpenApiSecurityDefinitions()`, completion for types, template names and
status codes, and an intention that writes the missing template into
`getOpenApiTemplates()`.

One implementation note worth recording: navigation is a `GotoDeclarationHandler`
rather than a `PsiReference`. `PhpDocTagImpl` declares itself a
`ContributedReferenceHost` and then overrides `getReferences()` to ignore
contributed ones — the providers do run, but nothing asks them through the tag,
so Ctrl+Click sees nothing.

Nothing fires unless the project actually has `Dskripchenko\LaravelApi` on its
classpath — `@input` and `@output` are ordinary words elsewhere.

## Building

```bash
./gradlew buildPlugin     # → build/distributions/
./gradlew test
./gradlew runIde          # a sandbox IDE with the plugin loaded
```

The build compiles against the locally installed PhpStorm
(`/Applications/PhpStorm.app`) by default — it saves a gigabyte of download and
is the IDE the author runs. Point it elsewhere with `-PphpstormPath=…`, or pin a
downloaded version with `-PphpstormVersion=2025.2`, which is what CI does.

## License

MIT © Denis Skripchenko
