# MongoScala3Codec

![version](https://img.shields.io/badge/version-0.0.11-brightgreen)
![Scala](https://img.shields.io/badge/Scala-3.3%2B-blue)
![MongoDB driver](https://img.shields.io/badge/mongo--scala--driver-5.7%2B-13aa52)
![Build Status](https://github.com/mbannour/MongoScala3Codec/workflows/Test%20Scala%20Library/badge.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Type-safe MongoDB for Scala 3 — without leaving the official driver.**

In MongoDB, a mistyped field name or a wrong value type in a query *doesn't fail loudly* — it compiles,
runs, and quietly returns the wrong data. MongoScala3Codec turns those silent runtime bugs into
**compile errors**, while you keep using the official MongoDB Scala driver exactly as you do today.

```scala
// ❌ Official driver — everything is a string, nothing is checked
collection.updateMany(
  Filters.and(
    Filters.eq("departmnet", "Engineering"),   // ← typo compiles & runs → silently matches 0 docs
    Filters.gt("salary", "80000")              // ← String vs number → silently matches 0 docs
  ),
  Updates.mul("salary", 1.05)
)

// ✅ MongoScala3Codec — same driver, same `collection`, now the compiler has your back
import io.github.mbannour.mongo.dsl.*

collection.updateMany(
  Filter.and(
    field[Employee](_.department) === "Engineering",  // ← wrong field name → won't compile
    field[Employee](_.salary) > 80_000.0              // ← wrong type → won't compile
  ),
  field[Employee](_.salary).mul(1.05)
)
```

**No rewrite. No new driver. No effect system to adopt.** The DSL produces plain `Bson` and the codecs
are standard `Codec`s, so it drops straight into your existing project — your `collection`, your queries,
your stack stay the same. You just stop shipping field-name typos to production.

### What you get

- 🛡️ **Queries the compiler checks** — filters, updates, sorts, projections, and full aggregation
  pipelines. Field typos and type mismatches fail at *compile time*, not at 2 a.m.
- ⚡ **Zero-boilerplate codecs, zero runtime cost** — case classes derive straight to BSON at compile
  time (no reflection, no JSON round-trip), preserving `ObjectId`, `Decimal128`, dates, and enums.
- 🔌 **Drops onto the official 5.7+ driver** — add one dependency to an existing app and write typed
  queries in minutes. Effect-agnostic: works with `Future`, ZIO, and more.
- ✅ **Production-ready** — 300+ tests against live MongoDB, comprehensive compile-time error messages.

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec"     % "0.0.11",  // compile-time BSON codecs
  "io.github.mbannour" %% "mongoscala3codec-dsl" % "0.0.11",  // type-safe query/update DSL
  "org.mongodb.scala"  %% "mongo-scala-driver"   % "5.7.0"    // the official driver — unchanged
)
```

👉 **Get running in 5 minutes:** [Quickstart](docs/QUICKSTART.md) · **See every operator:** [DSL Guide](docs/DSL.md)

---

## 📋 Table of Contents

- [Quick Start](#-quick-start)
- [Why MongoScala3Codec?](#-why-mongoscala3codec)
- [Installation](#-installation)
- [Features](#-features)
- [Documentation](#-documentation)
- [Usage Examples](#-usage-examples)
  - [Basic Registration](#basic-registration)
  - [Sealed Traits](#sealed-traits)
  - [Enums](#enums)
  - [Type-Safe Field Paths](#type-safe-field-paths)
  - [Type-Safe Query DSL](#type-safe-query-dsl)
  - [Repository Abstraction](#repository-abstraction)
  - [Testing](#testing)
- [Architecture](#-architecture)
- [Performance](#-performance--benchmarks)
- [Contributing](#-contributing)
- [Support & Community](#-support--community)
- [License](#-license)

---

## 🚀 Quick Start

**1. Add dependency to `build.sbt`:**

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec"     % "0.0.11",  // core codecs
  "io.github.mbannour" %% "mongoscala3codec-dsl" % "0.0.11",  // type-safe DSL (optional)
  "org.mongodb.scala"  %% "mongo-scala-driver"   % "5.7.0"
)
```

**2. Copy, paste, and run:**

```scala
import org.bson.types.ObjectId
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig, NoneHandling}
import org.mongodb.scala.MongoClient

case class Address(street: String, city: String, zipCode: Int)
case class Person(_id: ObjectId, name: String, address: Address, email: Option[String])

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .registerAll[(Address, Person)]
  .build

val mongoClient = MongoClient("mongodb://localhost:27017")
val database = mongoClient.getDatabase("myapp").withCodecRegistry(registry)
val people = database.getCollection[Person]("people")

val person = Person(new ObjectId(), "Alice", Address("123 Main", "NYC", 10001), Some("alice@example.com"))
people.insertOne(person).toFuture()
```

**That's it!** No manual codec writing, no reflection, no runtime overhead.

👉 **New to the library?** Continue with [Quickstart Guide](docs/QUICKSTART.md) for detailed walkthrough.

---

## 💡 Why MongoScala3Codec?

### **The Problem: the official driver stops at codecs**

As of **5.7**, the official `mongo-scala-driver` finally derives BSON codecs natively on Scala 3 (via a new `scala.quoted` macro), closing the gap that originally motivated this library. But it stops there. With the official driver alone you still:

- 🔤 **Write string-based queries** — `Filters.eq("address.city", v)`. Typos compile fine and fail at runtime.
- 🧬 **Get rigid polymorphism** — a hard-coded `_t` discriminator using the simple class name; no configurable field or strategy.
- 🚫 **Hand-write enum codecs** — Scala 3 `enum` fields throw `CodecConfigurationException` at runtime unless you supply your own codec.
- 🧱 **Register types verbosely** — `fromProviders(classOf[A], classOf[B], …)`, with no fluent builder.

**MongoScala3Codec is the type-safe ergonomics layer on top of the official driver.** It emits plain `Bson` and standard `Codec`s, so it composes with the driver rather than replacing it.

### **What MongoScala3Codec adds over the official driver**

| Capability | MongoScala3Codec | mongo-scala-driver 5.7+ | ReactiveMongo |
|---|---|---|---|
| **Scala 3 compile-time codecs** | ✅ | ✅ (since 5.7) | ⚠️ via 2.13 compat |
| **Type-safe query/update DSL** | ✅ `field[T]` DSL | ❌ string-based | ❌ string-based |
| **Type-safe field paths** | ✅ **MongoPath**¹ | ❌ string-based | ❌ string-based |
| **Type-safe aggregation builders** | ✅ `Stage`/`Expr`/`Accumulator` | ❌ string-based | ❌ |
| **Scala 3 enum codecs** | ✅ string/ordinal/custom | ❌ runtime failure² | ⚠️ |
| **Configurable discriminator** | ✅ field name + SimpleName/FQN/Custom | ❌ fixed `_t`, simple name only | ⚠️ |
| **None handling (omit vs null)** | ✅ | ✅ | ✅ |
| **Fluent registry builder** | ✅ `RegistryBuilder` | ❌ manual providers | ⚠️ |
| **Detailed error messages** | ✅ **Detailed**³ | ⚠️ Basic | ⚠️ Basic |

**Footnotes:**
1. Compile-time safe field paths: `MongoPath.of[User](_.address.?.city)` respects `@BsonProperty`
2. Verified against `mongo-scala-driver` 5.7.0: a Scala 3 `enum` field encodes to `CodecConfigurationException: Can't find a codec for …` unless you register a hand-written codec. (Opaque types, by contrast, work in both — they erase to their underlying type.)
3. Enhanced macro errors with ❌/✅ examples, runtime errors with causes and suggestions

**Bottom line:** since 5.7 the official driver covers codecs — MongoScala3Codec adds the **type-safe query/update/aggregation DSL, Scala 3 enum codecs, configurable polymorphism, and ergonomic registration** that the driver still lacks.

---

## 📦 Installation

**Requirements:** Scala 3.3.1 or higher · JDK 11 or higher

### Core codec library

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.11",
  "org.mongodb.scala"  %% "mongo-scala-driver" % "5.7.0"
)
```

### Core + type-safe query/update DSL

Add the DSL artifact to get `field[T]`, `Filter`, `Update`, `Sort`, and `Projection`:

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec"     % "0.0.11",
  "io.github.mbannour" %% "mongoscala3codec-dsl" % "0.0.11",
  "org.mongodb.scala"  %% "mongo-scala-driver"   % "5.7.0"
)
```

### Repository abstraction (optional)

Add the repository artifact for a type-safe, effect-agnostic CRUD layer over a collection:

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec"            % "0.0.11",
  "io.github.mbannour" %% "mongoscala3codec-dsl"        % "0.0.11",
  "io.github.mbannour" %% "mongoscala3codec-repository" % "0.0.11",
  "org.mongodb.scala"  %% "mongo-scala-driver"          % "5.7.0"
)
```

### DSL testing utilities

Add to your `Test` scope to get `DslTestKit` assertion helpers:

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec-dsl-testkit" % "0.0.11" % Test
```

### Summary

| Artifact | When to use |
|----------|-------------|
| `mongoscala3codec` | Core BSON codec generation (always required) |
| `mongoscala3codec-dsl` | Type-safe filters, updates, sorts, projections |
| `mongoscala3codec-repository` | Type-safe, effect-agnostic CRUD over a collection |
| `mongoscala3codec-dsl-testkit` | DSL assertion helpers in tests |

---

## ✨ Features

### Core Capabilities

- ✅ **Zero Boilerplate** - One line registers any case class
- ✅ **Compile-Time Safe** - Catch errors before deployment, not in production
- ✅ **Zero Runtime Overhead** - Codecs generated at compile time, no reflection
- ✅ **Type-Safe Field Paths** - `MongoPath.of[User](_.address.?.city)` - unique in Scala
- ✅ **Type-Safe Query DSL** - Compile-time filters, updates, sorts, projections with `field[T]`
- ✅ **BSON-Native** - Preserves ObjectId, Binary, Decimal128, Dates
- ✅ **Production-Ready** - Comprehensive error messages, 280+ tests, stress-tested

### Type Support

- ✅ **Sealed Traits/Classes** - Polymorphic codecs with automatic discriminators (v0.0.8)
- ✅ **Scala 3 Enums** - Full support with string/ordinal/custom field encoding
- ✅ **Default Parameters** - Missing fields use defaults automatically
- ✅ **Options & Nested Types** - `Option[T]`, nested case classes, collections
- ✅ **Opaque Types** - Zero-cost wrappers work seamlessly
- ✅ **Primitive Types** - Complete coverage (Byte, Short, Char, Int, Long, Float, Double, Boolean, String)
- ✅ **UUID & Binary Types** - Built-in support for common data types
- ✅ **Collections** - List, Set, Vector, Map with proper BSON encoding

### Configuration & Tools

- ✅ **Flexible None Handling** - Encode as null or omit from document
- ✅ **Custom Field Names** - `@BsonProperty` annotations
- ✅ **Batch Registration** - `registerAll[(A, B, C)]` for optimal compile times
- ✅ **Conditional Registration** - `registerIf[T](condition)` for environment-specific codecs
- ✅ **Testing Utilities** - `CodecTestKit` for round-trip validation
- ✅ **Type-Safe Configuration** - Immutable `CodecConfig` with builder pattern

---

## 📚 Documentation

### 📖 Complete Guide

**👉 [Complete Documentation Index](docs/README.md)** - Navigation hub for all documentation

### 🎯 Quick Links by Use Case

| I want to... | Documentation |
|--------------|---------------|
| **Get started quickly** | [Quickstart Guide](docs/QUICKSTART.md) |
| **Understand all features** | [Feature Overview](docs/FEATURES.md) |
| **Build type-safe filters & updates** | [Query/Update DSL](docs/DSL.md) |
| **Use a typed CRUD repository** | [Repository Guide](docs/REPOSITORY.md) |
| **Work with sealed traits** | [Sealed Trait Support](docs/SEALED_TRAIT_SUPPORT.md) |
| **Use Scala 3 enums** | [Enum Support](docs/ENUM_SUPPORT.md) |
| **Understand BSON mapping** | [BSON Type Mapping](docs/BSON_TYPE_MAPPING.md) |
| **Fix compilation errors** | [FAQ & Troubleshooting](docs/FAQ.md) |
| **Integrate with MongoDB** | [MongoDB Interop](docs/MONGODB_INTEROP.md) |
| **Migrate from another library** | [Migration Guide](docs/MIGRATION.md) |
| **Understand internals** | [How It Works](docs/HOW_IT_WORKS.md) |

---

## 💻 Usage Examples

### Basic Registration

#### Single Types

```scala
val reg = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[MyType]
  .build
```

#### Batch Types (Recommended)

```scala
val reg = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerAll[(Address, Person, Task)]
  .build
```

**Tip:** Prefer `registerAll[(A, B, C)]` over sequential `register` calls for better compile-time performance.

#### Configure Option Handling

```scala
val reg = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone   // Omit None fields, or use .encodeNone to encode as null
  .registerAll[(Address, Person)]
  .build
```

| Setting | BSON Result |
|---------|-------------|
| `NoneHandling.Encode` (`.encodeNone`) | Encodes `None` as `null` |
| `NoneHandling.Ignore` (`.ignoreNone`) | Omits the field entirely |

#### Conditional Registration

```scala
val isProd = sys.env.get("APP_ENV").contains("prod")

val reg = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[CommonType]
  .registerIf[ProdOnlyType](isProd)
  .build
```

#### Merging Builders

```scala
val common = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Address]
  .register[Person]

val extra = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Department]

val reg = (common ++ extra).build
```

---

### Sealed Traits

✅ **Sealed traits and classes are fully supported!** Use `registerSealed[T]` for automatic polymorphic codec generation:

```scala
sealed trait Animal
case class Dog(name: String, breed: String) extends Animal
case class Cat(name: String, lives: Int) extends Animal

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Animal]  // Registers Animal + all subtypes
  .build

// Works polymorphically with automatic discriminator field
val animals: List[Animal] = List(
  Dog("Rex", "Labrador"),
  Cat("Whiskers", 9)
)

database.getCollection[Animal]("animals").insertMany(animals).toFuture()
```

**Batch Registration:**
```scala
// Register multiple sealed traits efficiently
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealedAll[(Animal, Vehicle, Status)]
  .build
```

**Features:**
- ✅ Automatic discriminator field (default: `_type`, configurable)
- ✅ Single call registers entire hierarchy
- ✅ Works with collections, nested structures, and Option fields
- ✅ Supports sealed trait, sealed class, and sealed abstract class

**Limitations:**
- ⚠️ Case objects in sealed hierarchies are not supported - use case classes or Scala 3 enums
- ⚠️ Sealed traits with type parameters are not yet supported

👉 **See [Sealed Trait Support Guide](docs/SEALED_TRAIT_SUPPORT.md)** for comprehensive examples.

---

### Enums

Scala 3 enums are supported via `EnumValueCodecProvider`.

```scala
import io.github.mbannour.mongo.codecs.EnumValueCodecProvider
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient

enum Priority:
  case Low, Medium, High

val base = fromRegistries(
  MongoClient.DEFAULT_CODEC_REGISTRY,
  fromProviders(EnumValueCodecProvider.forStringEnum[Priority])
)

val reg = RegistryBuilder
  .from(base)
  .register[Task]
  .build
```

**Enum Variants:**

- `forStringEnum[E]` → stores enum by its name (stable, readable)
- `forOrdinalEnum[E]` → stores enum by its ordinal (compact, renumbering-sensitive)

**Best practice:** Prefer string-based enums (`forStringEnum`) for schema stability and readability.

👉 **See [Enum Support Guide](docs/ENUM_SUPPORT.md)** for advanced patterns.

---

### Type-Safe Query DSL

Build filters, updates, sorts, and projections entirely at compile time — no string field names,
no driver-specific imports:

```scala
import io.github.mbannour.mongo.dsl.*
import io.github.mbannour.fields.MongoPath.syntax.?

case class Address(city: String, @BsonProperty("zip") zipCode: Int)
case class Employee(_id: ObjectId, name: String, salary: Double, active: Boolean, address: Option[Address], skills: List[String])

// Filter: active employees in Paris earning above 80k
val filter = Filter.and(
  field[Employee](_.active) === true,
  field[Employee](_.salary) > 80_000.0,
  field[Employee](_.address.?.city) === "Paris"   // path = "address.city"
)

// Atomic update: 5% raise + add a skill
val update = Update.combine(
  field[Employee](_.salary).mul(1.05),
  field[Employee](_.skills).addToSet("Scala 3")
)

// Sort by salary desc, then name asc
val sort = Sort.combine(field[Employee](_.salary).desc, field[Employee](_.name).asc)

collection.updateMany(filter, update).toFuture()
collection.find(filter).sort(sort).toFuture()
```

Every operator is typed — `field[Employee](_.salary) === "wrong"` is a compilation error.

👉 **See [Query/Update DSL Guide](docs/DSL.md)** for the complete API reference.

---

### Repository Abstraction

For a higher-level CRUD layer, the optional `mongoscala3codec-repository` module wraps a collection
in a type-safe, **effect-agnostic** `MongoRepository[F, Doc, Id]` driven entirely by the DSL:

```scala
import io.github.mbannour.mongo.dsl.*
import io.github.mbannour.mongo.repository.*
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global

// idField is just a DSL field reference — typically _._id
val repo: MongoRepository[Future, Employee, ObjectId] =
  MongoRepository(collection, field[Employee](_._id))

repo.find(
  filter = Filter.and(
    field[Employee](_.department) === "Engineering",
    field[Employee](_.salary) > 80_000.0
  ),
  sort  = Some(field[Employee](_.salary).desc),
  limit = 10
)                                            // Future[Seq[Employee]]

repo.updateById(id, Update.combine(field[Employee](_.salary).mul(1.05)))  // Future[Long]
repo.findById(id)                            // Future[Option[Employee]]
```

The effect type `F` is abstract: a `Future` instance ships in the module, and ZIO / cats-effect
instances are a few lines each in a thin integration module.

👉 **See [Repository Guide](docs/REPOSITORY.md)** for the full API.

---

### Type-Safe Field Paths

Avoid stringly-typed bugs in filters, updates, projections, and sorts with **MongoPath**.

```scala
import io.github.mbannour.fields.MongoPath
import io.github.mbannour.fields.MongoPath.syntax.?    // Option hop
import org.mongodb.scala.model.Filters
import org.mongodb.scala.bson.annotations.BsonProperty
import org.bson.types.ObjectId

case class Address(street: String, @BsonProperty("zip") zipCode: Int)
case class User(_id: ObjectId, name: String, address: Option[Address])

val zipPath = MongoPath.of[User](_.address.?.zipCode)  // "address.zip"
val filter  = Filters.equal(zipPath, 12345)

val idPath   = MongoPath.of[User](_._id)   // "_id"
val namePath = MongoPath.of[User](_.name)  // "name"
```

**Rules:**
- Use simple access chains, e.g. `_.a.b.c`
- Import `MongoPath.syntax.?` to transparently traverse `Option`
- `@BsonProperty` values on constructor params are respected

---

### Opaque Types

Opaque types work out of the box (zero runtime overhead).

```scala
object Domain:
  opaque type UserId = String
  object UserId:
    def apply(v: String): UserId = v
    extension (u: UserId) def value: String = u

import Domain.*
import org.bson.types.ObjectId

case class Profile(_id: ObjectId, userId: UserId, age: Int)

val reg = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Profile]
  .build
```

---

### Testing

Test your codecs without a database using `CodecTestKit`:

```scala
import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder, CodecConfig, NoneHandling}
import org.bson.codecs.Codec
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient

case class User(_id: ObjectId, name: String, email: Option[String])

given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val reg = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build

given Codec[User] = reg.get(classOf[User])

// Round-trip symmetry
CodecTestKit.assertCodecSymmetry(User(new ObjectId(), "Alice", Some("a@x.com")))

// Inspect BSON
val bson = CodecTestKit.toBsonDocument(User(new ObjectId(), "Bob", None))
println(bson.toJson())  // email omitted due to Ignore
```

**Why Use It?**

✅ Catch codec bugs early (no DB needed)
✅ Validate BSON structure deterministically
✅ Works with ScalaTest, MUnit, ScalaCheck

---

## 🏗️ Architecture

MongoScala3Codec leverages Scala 3's inline macros and metaprogramming for compile-time codec generation:

```
┌─────────────────────┐
│  Scala 3 Case Class │
│   case class User   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   Inline Macros     │
│  (Compile-Time)     │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   BSON Codec        │
│ (Zero Overhead)     │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   MongoDB Driver    │
│   BsonDocument      │
└─────────────────────┘
```

**Key Design Principles:**

1. **Compile-Time Generation** - All codec logic generated at compile time, no runtime reflection
2. **Type Safety** - Invalid configurations caught by compiler, not at runtime
3. **Zero Overhead** - Generated code is as efficient as hand-written codecs
4. **Incremental Compilation** - Smart caching reduces recompilation time
5. **Clear Error Messages** - Actionable compile-time and runtime diagnostics

👉 **See [How It Works](docs/HOW_IT_WORKS.md)** for detailed architecture explanation.

---

## 🏆 Performance & Benchmarks

MongoScala3Codec includes JMH microbenchmarks for measuring codec performance. The benchmarks cover:
- Flat case classes with primitives
- Nested structures with `Option` fields
- Sealed trait hierarchies with discriminators
- Large collections (List, Vector, Map)

**Headline Results:**
- **Encode Performance**: ~500,000 ops/sec for simple case classes
- **Decode Performance**: ~400,000 ops/sec for simple case classes
- **Memory**: Zero allocation overhead vs hand-written codecs
- **Compile Time**: Batch registration reduces compile time by 60%

👉 **See [Benchmarks Documentation](docs/BENCHMARKS.md)** for details on running benchmarks and interpreting results.

---

## 🤝 Contributing

Contributions are welcome! We appreciate:

- 🐛 Bug reports and fixes
- 📚 Documentation improvements
- ✨ Feature implementations
- 🧪 Test coverage enhancements
- 💡 Ideas and feedback

**Before contributing:**

1. Check [existing issues](https://github.com/mbannour/MongoScala3Codec/issues) for duplicates
2. Read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines
3. Follow the [Code of Conduct](CODE_OF_CONDUCT.md)

**Development Setup:**

```bash
git clone https://github.com/mbannour/MongoScala3Codec.git
cd MongoScala3Codec
sbt compile
sbt test
```

---

## 🛟 Support & Community

### 📖 Documentation
- [Complete Documentation](docs/)
- [API Docs](https://mbannour.github.io/MongoScala3Codec/api/)
- [Quickstart Guide](docs/QUICKSTART.md)

### 💬 Get Help
- [GitHub Discussions](https://github.com/mbannour/MongoScala3Codec/discussions) - Q&A, ideas, and general discussion
- [Issue Tracker](https://github.com/mbannour/MongoScala3Codec/issues) - Bug reports and feature requests

### 📰 Stay Updated
- [Changelog](CHANGELOG.md) - Release notes and migration guides
- [GitHub Releases](https://github.com/mbannour/MongoScala3Codec/releases) - Version announcements

---

## 📜 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

Built with ❤️ for the Scala 3 community. Special thanks to all [contributors](https://github.com/mbannour/MongoScala3Codec/graphs/contributors) who have helped improve this library.

**Star ⭐ this repo if you find it useful!**

---

<div align="center">

**Made with Scala 3 | Powered by Inline Macros | Zero Runtime Overhead**

[Documentation](docs/) • [Quickstart](docs/QUICKSTART.md) • [GitHub](https://github.com/mbannour/MongoScala3Codec) • [Issues](https://github.com/mbannour/MongoScala3Codec/issues)

</div>
