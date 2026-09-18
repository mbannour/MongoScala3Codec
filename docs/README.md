# MongoScala3Codec documentation

**[../README.md](../README.md) is the authoritative documentation.** It covers the product, the
API, the supported-type contract, the BSON representations, the compatibility matrices and the
limitations, and it is the one kept in step with the test suite.

The pages here are deep dives. Where one disagrees with the main README, the README is right.

---

## Start here

| Page | For |
|---|---|
| [../README.md](../README.md) | everything: install, derive, supported types, compatibility |
| [QUICKSTART.md](QUICKSTART.md) | a longer worked example with a running database |

## Deep dives

| Page | For |
|---|---|
| [BSON_TYPE_MAPPING.md](BSON_TYPE_MAPPING.md) | the stored representation of every supported type |
| [SEALED_TRAIT_SUPPORT.md](SEALED_TRAIT_SUPPORT.md) | sealed hierarchies and discriminators in depth |
| [ENUM_SUPPORT.md](ENUM_SUPPORT.md) | Scala 3 enums, string and ordinal modes |
| [MONGODB_INTEROP.md](MONGODB_INTEROP.md) | using derived registries with the official driver |
| [FEATURES.md](FEATURES.md) | feature catalogue with examples |
| [HOW_IT_WORKS.md](HOW_IT_WORKS.md) | how the macros build a codec |
| [FAQ.md](FAQ.md) | common questions and compile-error troubleshooting |
| [MIGRATION.md](MIGRATION.md) | moving from another MongoDB library |
| [BENCHMARKS.md](BENCHMARKS.md) | what the JMH benchmarks measure and how to run them |

## Project

| Page | For |
|---|---|
| [../ROADMAP.md](../ROADMAP.md) | 1.0 scope and the 1.1+ direction |
| [../CHANGELOG.md](../CHANGELOG.md) | release notes |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | contributing and local development |

---

## Two things to know before reading further

**Configuration has to reach the builder.** `RegistryBuilder` does not summon a `given
CodecConfig`. A page that writes

```scala
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

RegistryBuilder.from(base).withConfig(summon[CodecConfig]).register[User].build
```

is correct — the config is passed explicitly. A page that declares the `given` and then **omits**
`withConfig` is not: the setting is silently ignored and `None` is still written as `null`. The
direct form avoids the question:

```scala
RegistryBuilder.from(base).ignoreNone.register[User].build
```

**The deep dives predate the 1.0 contract review** and have not all been re-verified line by line
against the test suite. The README has. If a page here claims a type is supported and the README's
table does not list it, trust the README.
