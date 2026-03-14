# MongoScala3Codec

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.11-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.3.7%2B-blue)
![Build Status](https://github.com/mbannour/MongoScala3Codec/workflows/Test%20Scala%20Library/badge.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**MongoScala3Codec – Compile‑time BSON codecs for Scala 3.** Auto-generates type-safe BSON codecs at compile time with zero runtime overhead and production-ready error handling.

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
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.11",
  ("org.mongodb.scala" %% "mongo-scala-driver" % "5.6.0").cross(CrossVersion.for3Use2_13)
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

### **The Core Problem: No Scala 3 Support**

**This library was created to enable native MongoDB usage in Scala 3.** The official `mongo-scala-driver` **only supports Scala 2.11, 2.12, and 2.13** because it relies heavily on **Scala 2 macros** for automatic codec generation. Since Scala 3 completely redesigned the macro system, the official driver **requires a major rewrite** to support Scala 3.

**Your options without MongoScala3Codec:**
- ⬇️ **Downgrade to Scala 2.13** (lose Scala 3 features)
- ❌ **Wait indefinitely** for official Scala 3 support
- ✍️ **Write manual codecs** for every type (100+ lines per case class)

### **MongoScala3Codec Solves This**

| Feature | MongoScala3Codec | mongo-scala-driver | ReactiveMongo |
|---------|------------------|---------------------|---------------|
| **Scala 3 Support** | ✅ **Native** | ❌ Scala 2 only¹ | ❌ Scala 2 only² |
| **Macro System** | ✅ Scala 3 macros | ❌ Scala 2 macros³ | ⚠️ Scala 2 macros |
| **Compile-Time Codecs** | ✅ Zero overhead | ✅ Scala 2 only | ⚠️ Mixed⁴ |
| **Type-Safe Field Paths** | ✅ **MongoPath**⁵ | ❌ | ❌ |
| **None Handling Options** | ✅ Ignore/Encode | ✅ Ignore/Encode | ✅ |
| **Production Error Messages** | ✅ **Detailed**⁶ | ⚠️ Basic | ⚠️ Basic |

**Footnotes:**
1. mongo-scala-driver supports Scala 2.11, 2.12, 2.13 only - [Scaladex](https://index.scala-lang.org/mongodb/mongo-java-driver)
2. ReactiveMongo v0.20.13 supports Scala 2.11, 2.12, 2.13 only - [Scaladex](https://index.scala-lang.org/reactivemongo/reactivemongo)
3. mongo-scala-driver "heavily uses macros which were dropped in Scala 3" - [Stack Overflow](https://stackoverflow.com/q/69230300)
4. ReactiveMongo uses both compile-time macros and runtime reflection components
5. Compile-time safe field paths: `MongoPath.of[User](_.address.?.city)` respects `@BsonProperty`
6. Enhanced macro errors with ❌/✅ examples, runtime errors with causes and suggestions

**Bottom line:** MongoScala3Codec is the **only library** that enables native MongoDB usage in Scala 3 with compile-time safety and BSON-native types.

---

## 📦 Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.11"
```

For use with MongoDB Scala Driver:

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.11",
  ("org.mongodb.scala" %% "mongo-scala-driver" % "5.6.0").cross(CrossVersion.for3Use2_13)
)
```

**Requirements:**
- Scala 3.3.1 or higher
- JDK 11 or higher

---

## ✨ Features

### Core Capabilities

- ✅ **Zero Boilerplate** - One line registers any case class
- ✅ **Compile-Time Safe** - Catch errors before deployment, not in production
- ✅ **Zero Runtime Overhead** - Codecs generated at compile time, no reflection
- ✅ **Type-Safe Field Paths** - `MongoPath.of[User](_.address.?.city)` - unique in Scala
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
