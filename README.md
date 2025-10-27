# MongoScala3Codec

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.7--M2-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)
![Build Status](https://github.com/mbannour/MongoScala3Codec/workflows/Test%20Scala%20Library/badge.svg)

MongoScala3Codec is a macro-based library for BSON serialization and deserialization of Scala 3 case classes. It generates BSON codecs at compile time, ensuring:

- **Strong Type Safety**: Compile-time validation of BSON serialization.
- **High Performance**: Optimized code generation for efficient BSON handling.
- **Minimal Boilerplate**: No need to write manual codec definitions.
- **Sealed Trait Support**: Automatic codec generation for sealed trait hierarchies.
- **Case Class Support**: Automatic codec generation for case classes, including concrete implementations from sealed trait hierarchies.
- **Flexible Configuration**: Type-safe configuration for codec behavior.
- **Pure Scala 3**: Built with opaque types and extension methods for idiomatic Scala 3 code.

---

## 🚀 Minimal Example 

Here's everything you need - just copy, paste, and run:

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

val found = people.find().first().toFuture()
```

**That's it!** No manual codec writing, no reflection, no runtime overhead. 

👉 **See [5-Minute Quickstart](docs/QUICKSTART.md)** for more examples and explanations.

---

## 📚 Documentation

- **[5-Minute Quickstart](docs/QUICKSTART.md)** - Get started immediately
- **[BSON Type Mapping](docs/BSON_TYPE_MAPPING.md)** - Complete type reference (35+ types)
- **[Feature Overview](docs/FEATURES.md)** - Complete feature guide with examples
- **[MongoDB Interop](docs/MONGODB_INTEROP.md)** - Driver integration guide
- **[How It Works](docs/HOW_IT_WORKS.md)** - Scala 3 derivation internals explained
- **[Migration Guide](docs/MIGRATION.md)** - Migrate from other libraries
- **[FAQ & Troubleshooting](docs/FAQ.md)** - Common issues and solutions

---

## Features

- ✅ Automatic BSON codec generation for Scala 3 case classes
- ✅ **Support for concrete case classes from sealed trait hierarchies** - each case class registered independently
- ✅ **Support for default parameter values** - missing fields use defaults automatically
- ✅ Support for options and nested case classes
- ✅ **Sealed trait hierarchies** with concrete case class implementations
- ❌ **Polymorphic sealed trait/class fields NOT supported** - see limitations below
- ✅ Custom field name annotations (e.g., `@BsonProperty`)
- ✅ Compile-time safe MongoDB field path extraction via `MongoPath`
- ✅ Scala 3 enum support via `EnumValueCodec`
- ✅ **UUID and Float primitive types** built-in support
- ✅ **Complete primitive type coverage** (Byte, Short, Char)
- ✅ **Type-safe configuration** with `CodecConfig`
- ✅ Flexible None handling (encode as null or omit from document)
- ✅ Collections support (List, Set, Vector, Map)
- ✅ **Testing utilities** with `CodecTestKit`

---

## Core Concepts

### Compile-time Derivation

Case class codecs are generated at compile time for speed and safety - no runtime reflection overhead.

### RegistryBuilder

Fluent, immutable builder for `CodecRegistry`:

- Register single or multiple types
- Add explicit codecs
- Merge builders
- Choose how `Option[None]` is handled:

| Setting | BSON Result |
|---------|-------------|
| `NoneHandling.Encode` | Encodes `None` as `null` |
| `NoneHandling.Ignore` | Omits the field entirely |

### MongoPath

Compile-time safe field paths (respects `@BsonProperty`) - prevents stringly-typed bugs.

### Enum Codecs

Provided via `EnumValueCodecProvider` (by name or ordinal).

### CodecTestKit

Testing helpers for round-trip checks and structure assertions.

---

## Registering Codecs

### A) Single Types

```scala
val reg = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .register[MyType]
  .build
```

### B) Batch Types (Faster)

```scala
val reg = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .registerAll[(Address, Person, Task)]
  .build
```

**Tip:** Prefer `registerAll[(A, B, C)]` over many sequential `register` calls for better performance.

### C) Configure Option Handling

```scala
val reg = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .ignoreNone   // or .encodeNone
  .registerAll[(Address, Person)]
  .build
```

### D) Conditional Registration

```scala
val isProd = sys.env.get("APP_ENV").contains("prod")

val reg = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .register[CommonType]
  .registerIf[ProdOnlyType](isProd)
  .build
```

### E) Merging Builders

```scala
val common = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .register[Address]
  .register[Person]

val extra = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .register[Department]

val reg = (common ++ extra).build
```

---

## Enums

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

### Enum Variants

- `forStringEnum[E]` → stores enum by its name (stable, readable)
- `forOrdinalEnum[E]` → stores enum by its ordinal (compact, renumbering-sensitive)

**Best practice:** Prefer string-based enums (`forStringEnum`) for schema stability and readability.

---

## MongoPath – Compile-time Field Paths

Avoid stringly-typed bugs in filters, updates, projections, and sorts.

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

### Rules

- Use simple access chains, e.g. `_.a.b.c`
- Import `MongoPath.syntax.?` to transparently traverse `Option`
- `@BsonProperty` values on constructor params are respected

---

## Scala 3 Opaque Types

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

val reg = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .register[Profile]
  .build
```

---

## Testing with CodecTestKit

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

### Why Use It?

✅ Catch codec bugs early (no DB needed)  
✅ Validate BSON structure deterministically  
✅ Works with ScalaTest, MUnit, ScalaCheck

---

## Troubleshooting & Limitations

⚠️ **Polymorphic sealed traits as fields** (e.g., `status: PaymentStatus`) are not supported yet. Use concrete types in fields, or wrappers that you register explicitly.

⚠️ **Case objects in sealed hierarchies** are not fully supported. Prefer parameterless case classes.

⚠️ **Collections of sealed traits** (e.g., `List[PaymentStatus]`) are not supported yet. Use collections of concrete members.

⚠️ **Value codecs with CodecTestKit.toBsonDocument** are intended for document-like types; for scalars, encode into a field or provide a helper that returns `BsonValue`.

---

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.7-M2"
```

For use with MongoDB Scala Driver:

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.7-M2",
  ("org.mongodb.scala" %% "mongo-scala-driver" % "5.2.1").cross(CrossVersion.for3Use2_13)
)
```

**Requirements:**
- Scala 3.3.1 or higher
- JDK 11 or higher

---

## Getting Started

See the **[5-Minute Quickstart](docs/QUICKSTART.md)** for a hands-on tutorial, or jump straight to the [Feature Overview](docs/FEATURES.md) for comprehensive examples.

---

## Performance & Benchmarks

MongoScala3Codec includes JMH microbenchmarks for measuring codec performance. The benchmarks cover:
- Flat case classes with primitives
- Nested structures with `Option` fields
- Sealed trait hierarchies (ADTs)
- Large collections (List, Vector, Map)

See **[Benchmarks Documentation](docs/BENCHMARKS.md)** for details on running benchmarks and interpreting results.

---

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

## Changelog

### 0.0.7-M2 (Current)
- **Performance**: Optimized `RegistryBuilder` with efficient caching (O(N) total vs O(N²))
- **New**: Convenience methods `just[T]`, `withTypes[T]`, `registerIf[T]`
- **New**: State inspection methods (`currentConfig`, `codecCount`, `hasCodecFor`, etc.)
- **New**: JMH benchmarks for performance testing
- **Enhancement**: `MongoPath` for compile-time safe field paths
- **Breaking**: Simplified `CodecConfig` by removing discriminator field
- **Improvement**: Better builder composition with `++` operator

### 0.0.6
- Initial stable release
- Case class codec generation
- Sealed trait support
- Optional field handling
- Custom field names with `@BsonProperty`
- Scala 3 enum support
- Opaque type support
- Testing utilities with `CodecTestKit`

---

## Support

- 📖 [Documentation](docs/)
- 🐛 [Report Issues](https://github.com/mbannour/MongoScala3Codec/issues)
- 💬 [Discussions](https://github.com/mbannour/MongoScala3Codec/discussions)
