# MongoScala3Codec

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.7--M3-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)
![Build Status](https://github.com/mbannour/MongoScala3Codec/workflows/Test%20Scala%20Library/badge.svg)

**MongoScala3Codec – Compile‑time BSON codecs for Scala 3.** Auto-generates type-safe BSON codecs at compile time with zero runtime overhead, best-in-class sealed trait support, and production-ready error handling.

---

## ⚡ Why MongoScala3Codec?

### **The Core Problem: No Scala 3 Support**

**This library was created to enable native MongoDB usage in Scala 3.** The official `mongo-scala-driver` **only supports Scala 2.11, 2.12, and 2.13** because it relies heavily on **Scala 2 macros** for automatic codec generation. Since Scala 3 completely redesigned the macro system, the official driver **requires a major rewrite** to support Scala 3.

**Your options without MongoScala3Codec:**
- ⬇️ **Downgrade to Scala 2.13** (lose Scala 3 features)
- 🔧 **Use Java driver directly** (lose type safety, write manual codecs)
- ❌ **Wait indefinitely** for official Scala 3 support

### **The Problem with MongoDB + Scala**
Beyond Scala 3 support, most MongoDB Scala libraries force you to choose between:
- ❌ **Manual codec writing** (tedious, error-prone, 100+ lines per type)
- ❌ **Runtime reflection** (slow, unsafe, compatibility issues)
- ❌ **Limited sealed trait support** (no discriminator strategies, rigid `_t` field naming)

### **MongoScala3Codec Solves Everything**

✅ **Zero Boilerplate** - One line registers any case class
✅ **Compile-Time Safe** - Catch errors before deployment, not in production
✅ **BSON-Native** - Preserves ObjectId, Binary, Decimal128, Dates
✅ **Sealed Traits** - Industry-leading polymorphic support with discriminators
✅ **Production-Ready** - Comprehensive error messages, 280+ tests, stress-tested

### **Unique Advantages**

| Feature | MongoScala3Codec | mongo-scala-driver | ReactiveMongo | 
|---------|------------------|---------------------|---------------|
| **Scala 3 Support** | ✅ **Native** | ❌ Scala 2 only¹ | ❌ Scala 2 only² |
| **Macro System** | ✅ Scala 3 macros | ❌ Scala 2 macros³ | ⚠️ Scala 2 macros |
| **Compile-Time Codecs** | ✅ Zero overhead | ✅ Scala 2 only | ⚠️ Mixed⁴ |
| **Sealed Trait Support** | ✅ **Best** | ✅ Basic⁵ | ✅ Basic |
| **Discriminator Strategies** | ✅ 3 strategies⁶ | ❌ Fixed `_t` only | ❌ Limited |
| **Type-Safe Field Paths** | ✅ **MongoPath**⁷ | ❌ | ❌ |
| **None Handling Options** | ✅ Ignore/Encode | ✅ Ignore/Encode | ✅ |
| **Production Error Messages** | ✅ **Detailed**⁸ | ⚠️ Basic | ⚠️ Ba*sic |

**Footnotes:**
1. mongo-scala-driver supports Scala 2.11, 2.12, 2.13 only - [Scaladex](https://index.scala-lang.org/mongodb/mongo-java-driver)
2. ReactiveMongo v0.20.13 supports Scala 2.11, 2.12, 2.13 only - [Scaladex](https://index.scala-lang.org/reactivemongo/reactivemongo)
3. mongo-scala-driver "heavily uses macros which were dropped in Scala 3" - [Stack Overflow](https://stackoverflow.com/q/69230300)
4. ReactiveMongo uses both compile-time macros and runtime reflection components
5. mongo-scala-driver supports sealed traits via `Macros.createCodecProvider` with fixed `_t` discriminator
6. MongoScala3Codec: SimpleName, FullyQualifiedName, Custom discriminator strategies
7. Compile-time safe field paths: `MongoPath.of[User](_.address.?.city)` respects `@BsonProperty`
8. Enhanced macro errors with ❌/✅ examples, runtime errors with causes and suggestions

**Bottom line:** MongoScala3Codec is the **only library** that enables native MongoDB usage in Scala 3 with compile-time safety, flexible sealed trait encoding, and BSON-native types.

---

## ⚡ Key Features

- **Strong Type Safety**: Compile-time validation of all BSON serialization
- **High Performance**: Optimized code generation with specialized primitive fast paths
- **Minimal Boilerplate**: No manual codec writing - everything auto-generated
- **Best-in-Class Sealed Traits**: Discriminator-based encoding with 3 strategies
- **Type-Safe Field Paths**: `MongoPath.of[User](_.address.?.city)` - unique in Scala
- **Flexible Configuration**: `ignoreNone` vs `encodeNone`, custom discriminators
- **Pure Scala 3**: Opaque types, extension methods, modern macro system

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

**👉 [Complete Documentation Index](docs/README.md)** - Navigation guide for all docs

### Quick Links

| Getting Started | Advanced | Reference |
|----------------|----------|-----------|
| [5-Min Quickstart](docs/QUICKSTART.md) | [Sealed Traits Guide](docs/SEALED_TRAITS.md) | [BSON Type Mapping](docs/BSON_TYPE_MAPPING.md) |
| [Feature Overview](docs/FEATURES.md) | [Enum Support](docs/ENUM_SUPPORT.md) | [MongoDB Interop](docs/MONGODB_INTEROP.md) |
| [FAQ & Troubleshooting](docs/FAQ.md) | [How It Works](docs/HOW_IT_WORKS.md) | [Migration Guide](docs/MIGRATION.md) |

**💡 New to the library?** Start with [QUICKSTART.md](docs/QUICKSTART.md) (5 minutes)

---

## Features

- ✅ Automatic BSON codec generation for Scala 3 case classes
- ✅ **Polymorphic sealed trait support** - discriminator-based encoding with `registerSealed[T]`
- ✅ **Support for concrete case classes from sealed trait hierarchies** - each case class registered independently
- ✅ **Support for default parameter values** - missing fields use defaults automatically
- ✅ Support for options and nested case classes
- ✅ **Sealed trait fields** with full polymorphism (case classes, case objects, collections)
- ✅ **Three discriminator strategies** - SimpleName, FullyQualifiedName, Custom
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

### F) Sealed Traits

Register sealed traits for polymorphic field support:

```scala
sealed trait PaymentStatus
case class Pending() extends PaymentStatus
case class Processing(transactionId: String) extends PaymentStatus
case class Completed(amount: Double) extends PaymentStatus

case class Payment(orderId: String, status: PaymentStatus)

// Single sealed trait
val reg = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .registerSealed[PaymentStatus]
  .register[Payment]
  .build

// Multiple sealed traits (batch)
sealed trait Priority
case class Low() extends Priority
case class High() extends Priority

val reg2 = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .registerSealedAll[(PaymentStatus, Priority)]  // Batch registration
  .register[Payment]
  .build
```

**Tip:** Use `registerSealedAll[(A, B, C)]` when registering multiple sealed traits for cleaner syntax.

See **[Sealed Traits Guide](docs/SEALED_TRAITS.md)** for complete documentation.

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
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.7-M3"
```

For use with MongoDB Scala Driver:

```scala
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.7-M3",
  ("org.mongodb.scala" %% "mongo-scala-driver" % "5.2.1").cross(CrossVersion.for3Use2_13)
)
```

**Requirements:**
- Scala 3.3.7 or higher
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

## Support

- 📖 [Documentation](docs/)
- 🐛 [Report Issues](https://github.com/mbannour/MongoScala3Codec/issues)
- 💬 [Discussions](https://github.com/mbannour/MongoScala3Codec/discussions)
