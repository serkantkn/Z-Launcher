# ZetaHubKit

Write a hub for [Zeta](https://github.com/serkantkn/Zeta): your app describes the hub - pages, lists,
buttons, the bar, a live tile - and Zeta draws it on phones and tablets, in Aura and in Mosaic, like
one of its own hubs. Touches come back to your app; your code never runs inside Zeta.

- **Guide:** [write a hub for Zeta](https://serkantkn.github.io/Zeta/developers.html) ·
  [Türkçe](https://serkantkn.github.io/Zeta/gelistiriciler.html)
- **Design notes:** [PLATFORM.md](../PLATFORM.md) (Turkish)

| Directory | Artifact | What it is |
|---|---|---|
| `protocol/` | `io.github.serkantkn:zetahubkit-protocol` | The binder interfaces, the node tree, the names in it and the rules (`HubSpecValidator`). Zeta uses it too. |
| `kit/` | `io.github.serkantkn:zetahubkit` | `ZetaHubService`, the description language, `ZetaTiles`. What a hub app depends on. |
| `sample/` | - | A to-do hub written with it. |

```kotlin
dependencies {
    implementation("io.github.serkantkn:zetahubkit:0.1.0")
}
```

Until the first release reaches Maven Central: `./gradlew :hubkit:publishToMavenLocal` from the
repository's root, and `mavenLocal()` in your project.

Everything in this directory is under the Apache License 2.0 ([LICENSE](LICENSE)); the rest of the
repository is Zeta, under its own licence.
