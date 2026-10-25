# Local dependencies

Place the Lootr Forge 1.20.1 jar here with this exact filename:

```
libs/lootr-forge-1.20-0.7.35.90.jar
```

Download Lootr from its official project page: <https://www.curseforge.com/minecraft/mc-mods/lootr>

The jar is intentionally not committed to this repository because it is a
third-party binary. `build.gradle` resolves it through `flatDir` and remaps it
with `fg.deobf` for the development environment.
