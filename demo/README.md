# Demo project — for screenshots

A project laid out so that every feature of the plugin lands in a frame. It does
not run: `stubs/laravel-api.php` declares just enough of the package for the
plugin to switch on.

```bash
./gradlew runIde          # a sandbox IDE with the plugin loaded
```

Then open this `demo` directory as a project. Not the plugin's own repository —
a sandbox opened on the plugin sources shows Kotlin, which is the wrong thing to
photograph.

## What to shoot, and where

**1. Markup that the generator understands** — `OrderController::store()`.
Types, formats, variables, template references and status codes are coloured.
The point of the shot is that a line looks like a language rather than prose.

**2. The three silent mistakes** — `OrderController::show()`, one frame:

- `datetime` is warned about, with the consequence named — the generator will
  call the field `string`;
- `string missingDollarSign …` is an error: the tag does not parse, and the
  field never reaches the spec;
- `{TemplateNobodyDeclared}` and `SchemeNobodyDeclared` are errors: the spec
  would carry references to things it never defines.

**3. The 404 nobody notices** — `V1::getMethods()`. `'export'` is red: the
method it points at was renamed away. The two entries above it carry gutter
arrows to the methods they route; this one has nothing to point at. Both facts
are in the same frame, which is the whole argument for the plugin.

**4. The way back** — `OrderController`, gutter. `store()` and `show()` have
arrows to the map entries that route them; `legacyExport()` has none.

**5. Completion** — type `@input ` on a new line and invoke it; then
`@response 200 {`, which offers the templates declared in `V1`.

**6. The quick fix** — Alt+Enter on `{TemplateNobodyDeclared}` offers to declare
it, and applying it writes the name into `V1::getOpenApiTemplates()` spelled
exactly as in the docblock.

## For the listing

Marketplace scales screenshots down; shoot a narrowed editor rather than a
full-screen IDE, and prefer the light theme unless the point of the frame is a
colour.
