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

Planned, in order: navigation from `{Template}` and `@Model` to their
definitions, gutter icons between the route map and controller methods,
inspections for dangling references, completion.

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
