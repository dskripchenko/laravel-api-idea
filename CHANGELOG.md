# Changelog

All notable changes to this plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Everything since 0.1.1, in one place

Five releases, published one after another so the history stays readable. The
short version:

- **0.2.0** — every check became an inspection. Severity, suppression and an
  on/off switch now come from the IDE, which is also why this plugin has no
  settings screen of its own.
- **0.3.0** — Find Usages on a response template, an arrow from a declaration to
  the docblocks naming it, and a searchable list of every endpoint the route map
  declares.
- **0.4.0** — `api:lint` runs from the IDE, its findings clickable. The command
  carries twenty-eight rules against the ten implemented here, and asking the
  application beats writing the other eighteen twice.
- **0.5.0** — a quick fix that writes the controller method an action points at,
  copying only what the neighbours agree on.
- **0.6.0** — the markup is compared against the validation rules. A field the
  endpoint validates and the docblock does not mention is reported, with a fix
  that writes the tag from the rule.

The thread running through all five: the package fails quietly, and so does its
documentation. Every release here turns one silent disagreement into something
visible while typing.

## [0.6.2]

### Fixed

- **The compatibility check on the plugin page stopped reporting warnings.**
  Every upload since 0.3.0 carried them: six usages of internal API, six of
  experimental, four of deprecated — none of which appeared anywhere in the
  source.

  They came from the compiler. Implementing a platform interface in Kotlin makes
  it emit a delegating override for every default method that interface has —
  `getIcon`, `getAnchor`, `manage`, `isApplicable`, `isDoNotActivateOnStart` — and
  the verifier reads a generated bridge exactly as it reads a hand-written call.
  Compiling with `-jvm-default=no-compatibility` leaves the class with the two
  methods it actually declares.

- **The two genuine ones are gone too.** Navigation from a double-click in the
  endpoint list and in the lint panel resolved its target through
  `ReadAction.compute`, deprecated in 2026.1, whose Kotlin replacement
  `runReadAction` is deprecated as well. Both now take the non-blocking path the
  list itself already used — which is not only current but better mannered:
  resolving a method touches the index, and that is not work to do between two
  mouse events.

### Changed

- **The verifier now fails the build on any of this**, rather than reporting it
  on a page nobody reads until a release is already out. Internal and
  experimental API break without a deprecation cycle; deprecated API is included
  because the only two the plugin ever used were both accidental.

## [0.6.1]

### Changed

- **The plugin page now describes what the plugin does.** Its text had not moved
  since 0.1.1 and named none of the five releases after it: no find-usages for a
  response template, no endpoint list, no `api:lint` run from the IDE, no quick
  fix that writes a missing action method, no comparison against validation
  rules.

  Nothing in the code changed here. The description is read far more often than
  it is written, and a stale one costs more than an unreleased feature: someone
  deciding whether to install this was reading a list from which half of it was
  missing.

## [0.6.0]

### Added

- **A field the endpoint validates and the markup does not mention** is now
  reported, with a quick fix that writes the tag from the rule.

  This is a different failure from a dangling reference, and a harder one to
  see: both sides are impeccable on their own. The rules are correct, the
  docblock parses, the generated specification validates — and it describes a
  different set of fields from the one the endpoint accepts.

  Measured before it was built. Of 52 endpoints across two real applications
  with both readable rules and a docblock, nine disagreed: a public integration
  API whose email delivery could not be called from its own documentation, a
  bulk endpoint documenting `items` while requiring `ids`, six fields of a
  dashboard layout described nowhere.

  The tag is written from what the rules actually say — `email` →
  `string(email)`, `nullable` → `?$`, `in:link,b64` → `[link,b64]`,
  `items.*.variables` → `$items[].variables` — and no description is invented.
  An empty description is honest; an invented one reads as considered.

### Deliberately absent

- **Generating the markup wholesale from the code**, which is what this release
  was originally going to be. The measurement said otherwise: rules are readable
  almost everywhere (one method in fifty-three is not), so generation saves
  typing where typing was never the problem, while the disagreement between
  rules and documentation is what nobody was looking at.

- **The reverse direction** — documented and not validated. Checked on the same
  code: `password_confirmation` comes from the `confirmed` rule, and some fields
  are validated dynamically against a column. Both are correct documentation,
  and an inspection that argues with correct documentation is one people switch
  off.

- **`@output` from the response.** Half of the responses cannot be read at all,
  their shapes vary more than rules do, and the cost of being wrong is the same:
  a specification that is confidently mistaken is worse than one that is silent.

- **Demanding a tag for `ids.*`.** An element of a scalar array has no form in
  this markup — the generator drops such a tag without a word. Asking the author
  to write a line that vanishes would be worse than asking nothing.

## [0.5.0]

### Added

- **A quick fix that writes the controller method an action points at**, on the
  error that already reports the map leading nowhere. Alt+Enter on the action
  key creates the method, with the name taken from the map rather than typed a
  second time — a second, slightly different spelling produces exactly the 404
  the fix exists to end.

  The docblock keeps what the neighbours agree on and invents nothing else.
  `@security` is copied, because authentication belongs to the controller rather
  than to one action, and only when every sibling declares the same scheme;
  where they differ, the choice is a decision and the line is left out.
  `@response` is never borrowed: a template names the body of *that* answer, and
  copying one would document a response this method does not return — while
  looking entirely deliberate.

## [0.4.0]

### Added

- **`api:lint` runs from the IDE**, in a Lint tab beside the endpoint list, or
  from Tools → Run api:lint.

  Not for the convenience of not opening a terminal. The command carries
  twenty-eight rules against the ten implemented here, and writing the other
  eighteen in Kotlin would create a second truth that drifts from the first —
  the very thing this plugin looks for in other people's code. So the fast
  checks run as you type, and the whole set is asked of the application.

  Findings are clickable: their address is `version · controller.action`, which
  the route map turns into a method, so a double click opens the docblock the
  finding is about.

  It needs `artisan` and a `php`. Where they are missing the reason is said in
  words — availability is a file question, deliberately, so that a menu being
  built does not depend on the index being ready.

  `php` is looked for in the login shell's environment rather than the
  launcher's. On macOS an IDE started from Finder inherits neither the shell's
  PATH nor its version manager, so the first version of this told a developer
  whose terminal runs `php artisan` all day that there was no PHP on the
  machine.

## [0.3.0]

### Added

- **Find Usages on a response template.** Standing on `'DraftPrintResult'` in
  `getOpenApiTemplates()`, the question is always the same — is anyone still
  using this, can it go. Until now the answer came from a text search, which
  also finds the name in prose and inside `$userResponse`.

  The search reads the docblocks the way the generator does, so only real
  references count, and goes through the word index rather than over every file
  in the project.

  Invoking it needs two pieces, and the second is easy to forget: before any
  handler is consulted the platform asks what the caret is *on*, and a key in an
  array literal is neither a named element nor a reference target. Without a
  usage target ⌥F7 answers "Cannot search for usages from this location" with a
  working search sitting behind it.

  A handler rather than references, for the reason recorded in the README:
  `PhpDocTagImpl` calls itself a reference host and then ignores contributed
  references, so nothing inside a docblock can be a reference to anything.

- **A gutter arrow from a template declaration to the docblocks naming it**,
  closing the loop the other direction already had. Its absence is the useful
  half: a declaration with no arrow is a schema nobody refers to.

- **An endpoint list**, in a tool window on the right. The route map is spread
  across the Api classes of every version and panel, and an action key is a
  string, so "where is `print-form.batch` handled" has been a question for grep.
  Type to filter, double-click to open the method. Api classes under `vendor/`
  are left out: the package itself ships an `example/` directory routing
  controllers named A, B, C and D, and a list that opens with `a.a → a()` is
  teaching material presented as the application's own surface.

  The window is always registered, and that is a correction rather than a
  preference: availability first asked the PHP index, and the platform decides
  availability while the project is still opening — before indexing finishes.
  The index answered "no package here", the window was never registered, and no
  amount of looking would find it. A feature that hides itself is worse than a
  tab in a project that does not need one, so the panel says plainly when there
  is nothing to show.

## [0.2.0]

### Changed

- **Every check is an inspection now, not an annotator.** An annotator hides its
  severity in the code, cannot be suppressed, cannot be switched off and takes
  no part in "Analyze → Inspect Code". `LocalInspectionTool` gives all four away
  — which is also why this plugin will never grow a settings screen of its own.

  Six inspections, each with its own name, description and severity:
  markup the generator cannot read, unknown type, undeclared response template,
  undeclared security scheme, a docblock that disagrees with itself, and a route
  map pointing at nothing.

  Whether `@security` is policed in a project that declares no schemes is now a
  checkbox. Until this release the answer was decided in the code, for everyone.

  Colouring stays an annotator: a colour is not a complaint.

### Added

- **Checks that need the whole docblock**, previously only in `api:lint`: a
  field declared twice, a nested field whose parent is never declared, a parent
  declared as the wrong kind of container, two answers for one status code. In
  each case the generator keeps the last thing it saw and says nothing.

  Deliberately not brought over: `@default` for a variable with no `@input`.
  Middleware contributes inputs of its own, and telling a stray default from a
  legitimate one needs the route map and the middleware chain resolved — which
  the command-line linter does with the application booted, and an inspection
  would only guess at.

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
