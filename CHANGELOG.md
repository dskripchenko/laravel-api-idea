# Changelog

All notable changes to this plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1]

Two defects found by using the plugin on a real project rather than by running
its tests. Both are the same shape — a green test over a feature that did not
work — which is the shape this plugin exists to catch in other people's code.

### Fixed

- **Ctrl+Click did nothing.** The goto handler was registered under
  `codeInsight.gotoDeclarationHandler`, an extension point that does not exist;
  the platform never called it. The tests passed because they invoked the
  handler directly, skipping the dispatch. They now go through
  `GotoDeclarationAction`, the same path the IDE uses, and were confirmed to
  fail against the old registration.

- **One gutter arrow per token instead of per action.** `'create'` is three PSI
  leaves — quote, text, quote — and a marker was created on each. The platform
  merged them into a single icon whose popup offered the same method three
  times, two of the entries labelled `'`. The tests checked that an arrow
  existed and never that there was only one.

## [0.1.0]

The first release. It covers the two surfaces of
[`dskripchenko/laravel-api`](https://github.com/dskripchenko/laravel-api) the IDE
knew nothing about: the docblock markup its OpenAPI generator reads, and the
route map its `getMethods()` declares.

### Added

- **Highlighting** of `@input`, `@output`, `@header`, `@response`, `@security`,
  `@default` and `@example`. Types, formats, variables, template references and
  status codes are coloured, so a line the generator has stopped understanding
  stops looking like the ones it still does.
- **Markup that does not parse is reported** where it is written. The generator
  drops such a line without a word, and the field simply never reaches the spec.
- **Unknown types** are flagged with the consequence named: the generator will
  call the field `string`.
- **Navigation** from `{TemplateName}`, `@Model`, `@security Scheme` and
  `@input [method]` to their declarations.
- **Dangling references are errors**: a template or a security scheme nothing
  declares becomes a `$ref` into nothing in a spec that still validates.
- **A quick fix that declares the missing template**, creating
  `getOpenApiTemplates()` when the class has none, with the name taken verbatim
  from the docblock.
- **Gutter arrows both ways** between the route map and the controllers.
- **An action pointing at a missing, non-public or static method is an error** —
  the defect worth building this for. At runtime it answers 404, the same 404 as
  a mistyped URL.
- **Completion** of types, formats, template names, security schemes and status
  codes — and of nothing at all once the caret has passed the variable into the
  description.

### Notes

- Nothing fires unless the project has `Dskripchenko\LaravelApi` on its
  classpath: `@input` and `@output` are ordinary words elsewhere.
- `@security` is not policed while a project declares no schemes at all. Such an
  application has evidently not taken the feature up, and painting every tag red
  would teach people to look past the plugin rather than at it.
