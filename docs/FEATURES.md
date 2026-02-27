# Feature Overview

MongoScala3Codec provides comprehensive BSON codec generation for Scala 3 applications. This guide covers all features with practical examples.

## Table of Contents

- [Automatic Codec Generation](#automatic-codec-generation)
- [RegistryBuilder Enhancements (New in 0.0.7)](#registrybuilder-enhancements-new-in-007)
- [Sealed Trait Support (New in 0.0.8)](#sealed-trait-support-new-in-008)
- [Case Classes](#case-classes)
- [Optional Fields](#optional-fields)
- [Collections](#collections)
- [Nested Structures](#nested-structures)
- [Custom Field Names](#custom-field-names)
- [Scala 3 Enums](#scala-3-enums)
- [Opaque Types](#opaque-types)
- [Default Values](#default-values)
- [Type-Safe Configuration](#type-safe-configuration)
- [Type-Safe Field Path Resolution](#type-safe-field-path-resolution)
- [Testing Utilities](#testing-utilities)
- [Limitations](#limitations)

---

## Automatic Codec Generation

MongoScala3Codec uses Scala 3 macros to automatically generate BSON codecs at compile-time.

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.mongodb.scala.MongoClient

case class User(_id: ObjectId, name: String, age: Int)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]  // ← Codec generated at compile-time
  .build
```

**Benefits:**
- ✅ Zero runtime reflection
- ✅ Compile-time type safety
- ✅ Optimized performance
- ✅ No boilerplate code

---

## RegistryBuilder Enhancements (New in 0.0.7)

Version 0.0.7 introduces significant enhancements to `RegistryBuilder` with improved performance, convenience methods, and state inspection capabilities.

### Convenience Methods

#### Single Type Registration with `just[T]`
Register one type and build immediately:
```scala
given CodecRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .just[User]
```

#### Batch Registration with `registerAll`
More efficient than chaining multiple `register` calls:
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .registerAll[(User, Address, Order, Product)]
  .build
```

**Performance:** `registerAll` builds the temporary registry only once for all types, making it significantly faster than chained `register` calls.

**Safety:** duplicate registrations are rejected at compile time. If the same type appears twice (for example once via `withCodec[T]` and again via `register[T]`, or twice inside `registerAll[(A, A)]`), compilation fails with a `Duplicate codec detected for ...` error instead of silently overriding a codec. This prevents hard-to-debug serialization drift and keeps one clear codec source of truth per model type.

#### Conditional Registration with `registerIf`
Register types based on runtime conditions:
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[CommonType]
  .registerIf[DebugInfo](isDevelopment)
  .registerIf[AdminTools](isAdmin)
  .build
```

#### Batch and Build with `withTypes`
Register multiple types and build in one call:
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .withTypes[(User, Order, Product)]
```

### Builder Composition

Merge multiple builders using the `++` operator:
```scala
val commonTypes = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Address]
  .register[Person]

val specificTypes = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Department]
  .register[Employee]

val registry = (commonTypes ++ specificTypes).build
```

### State Inspection

Query builder state for debugging and conditional logic:

```scala
val builder = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .register[User]
  .register[Order]

// Get current configuration
val config = builder.currentConfig
println(s"None handling: ${config.noneHandling}")

// Count registered items
println(s"Providers: ${builder.providerCount}")  // 2
println(s"Codecs: ${builder.codecCount}")        // 0
println(s"Is empty: ${builder.isEmpty}")         // false

// Check codec availability
if builder.hasCodecFor[User] then
  println("User codec is available")

// Get codec if available
builder.tryGetCodec[User] match
  case Some(codec) => println("Found User codec")
  case None => println("No codec for User")

// Get summary
println(builder.summary)
// Output: "RegistryBuilder(providers=2, codecs=0, ignore None fields, cached=true)"
```

### Performance Optimizations

**Efficient Caching:** The builder maintains a cached temporary registry used for codec derivation:
- Chaining `register[A].register[B]...` is now O(N) total instead of O(N²)
- The cache is preserved across register calls
- Only rebuilt when base/codecs change

**Batch Registration:** `registerAll[(A, B, C)]` is N× faster than chaining individual `register` calls:
```scala
// Slower (rebuilds registry N times)
val registry = builder
  .register[Type1]
  .register[Type2]
  .register[Type3]
  .build

// Faster (builds registry once)
val registry = builder
  .registerAll[(Type1, Type2, Type3)]
  .build
```

### Cleaner Configuration API

The configuration API has been simplified:

```scala
// Old style (still works)
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[User]
  .build

// New style (recommended)
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone  // or .encodeNone
  .register[User]
  .build
```

---

## Sealed Trait Support (New in 0.0.8)

MongoScala3Codec now supports sealed traits and classes with automatic polymorphic codec generation.

### Basic Usage

```scala
sealed trait Animal
case class Dog(name: String, breed: String) extends Animal
case class Cat(name: String, lives: Int) extends Animal
case class Bird(name: String, canFly: Boolean) extends Animal

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Animal]  // Registers Animal + all subtypes
  .build
```

**Key Features:**
- ✅ **Automatic discriminator field** - No manual type field needed
- ✅ **Single registration** - One call registers entire hierarchy
- ✅ **Polymorphic collections** - `List[Animal]` works seamlessly
- ✅ **Nested structures** - Use as fields in other case classes
- ✅ **Configurable** - Custom discriminator field names

### Encoding Example

```scala
val dog: Animal = Dog("Rex", "Labrador")
// Encoded as: {"_type": "Dog", "name": "Rex", "breed": "Labrador"}

val cat: Animal = Cat("Whiskers", 9)
// Encoded as: {"_type": "Cat", "name": "Whiskers", "lives": 9}
```

### Polymorphic Collections

```scala
case class Zoo(name: String, animals: List[Animal])

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Animal]
  .register[Zoo]
  .build

val zoo = Zoo("City Zoo", List(
  Dog("Max", "Beagle"),
  Cat("Luna", 3),
  Bird("Polly", true)
))
// Round-trips perfectly with discriminators
```

### Custom Discriminator Field

```scala
val config = CodecConfig(discriminatorField = "_class")

val registry = baseRegistry
  .withConfig(config)
  .registerSealed[Animal]
  .build
// Uses "_class" instead of "_type"
```

### Supported Types

- ✅ `sealed trait`
- ✅ `sealed class`
- ✅ `sealed abstract class`
- ✅ Multi-level hierarchies
- ⚠️ Case objects not supported (use case classes or Scala 3 enums)

### Batch Registration with `registerSealedAll`

Register multiple sealed traits efficiently:

```scala
sealed trait Animal
case class Dog(name: String) extends Animal

sealed trait Vehicle
case class Car(make: String) extends Vehicle

sealed trait Status
case class Active(since: Long) extends Status

// Register all at once - more efficient than separate calls
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealedAll[(Animal, Vehicle, Status)]
  .build
```

**Performance:** `registerSealedAll` builds the temporary registry only once, making it significantly faster than chaining `registerSealed` calls.

**See [Sealed Trait Support Guide](SEALED_TRAIT_SUPPORT.md) for comprehensive examples.**

---

## Case Classes

Full support for Scala 3 case classes with all primitive types.

### Supported Types

```scala
case class AllTypes(
  // Primitives
  byteVal: Byte,
  shortVal: Short,
  intVal: Int,
  longVal: Long,
  floatVal: Float,
  doubleVal: Double,
  boolVal: Boolean,
  charVal: Char,
  stringVal: String,
  
  // MongoDB types
  objectId: ObjectId,
  decimal: BigDecimal,
  
  // Java types
  uuid: java.util.UUID,
  instant: java.time.Instant,
  localDate: java.time.LocalDate
)
```

### Example

```scala
case class Product(
  _id: ObjectId,
  name: String,
  price: Double,
  inStock: Boolean
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Product]
  .build

val collection = database
  .getCollection[Product]("products")
  .withCodecRegistry(registry)

val product = Product(new ObjectId(), "Laptop", 999.99, true)
collection.insertOne(product).toFuture()
```

**Stored in MongoDB:**
```json
{
  "_id": ObjectId("..."),
  "name": "Laptop",
  "price": 999.99,
  "inStock": true
}
```

## Optional Fields

Handle `Option[T]` with two strategies: encode as `null` or omit from document.

### Omit None Values (Recommended)

```scala
case class User(_id: ObjectId, name: String, email: Option[String])

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone  // Cleaner API in 0.0.7+
  .register[User]
  .build

val user = User(new ObjectId(), "Alice", None)
// Stored as: {"_id": ObjectId("..."), "name": "Alice"}
```

### Encode None as null

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .encodeNone  // Cleaner API in 0.0.7+
  .register[User]
  .build

val user = User(new ObjectId(), "Alice", None)
// Stored as: {"_id": ObjectId("..."), "name": "Alice", "email": null}
```

### Nested Options

```scala
case class Address(street: String, city: String, country: Option[String])
case class User(_id: ObjectId, name: String, address: Option[Address])

// Fully supported - options can be nested at any level
```

---

## Collections

Full support for Scala collections: `List`, `Seq`, `Vector`, `Set`, `Map`.

### List and Seq

```scala
case class Playlist(
  _id: ObjectId,
  name: String,
  songs: List[String],
  ratings: Seq[Int]
)

val playlist = Playlist(
  new ObjectId(),
  "My Favorites",
  List("Song A", "Song B", "Song C"),
  Seq(5, 4, 5)
)
```

**Stored in MongoDB:**
```json
{
  "_id": ObjectId("..."),
  "name": "My Favorites",
  "songs": ["Song A", "Song B", "Song C"],
  "ratings": [5, 4, 5]
}
```

### Set

```scala
case class Article(
  _id: ObjectId,
  title: String,
  tags: Set[String]
)

val article = Article(
  new ObjectId(),
  "Scala 3 Features",
  Set("scala", "programming", "functional")
)
```

### Map

```scala
case class UserPreferences(
  _id: ObjectId,
  userId: String,
  settings: Map[String, String],
  scores: Map[String, Int]
)

val prefs = UserPreferences(
  new ObjectId(),
  "user123",
  Map("theme" -> "dark", "language" -> "en"),
  Map("level1" -> 100, "level2" -> 250)
)
```

**Stored in MongoDB:**
```json
{
  "_id": ObjectId("..."),
  "userId": "user123",
  "settings": {"theme": "dark", "language": "en"},
  "scores": {"level1": 100, "level2": 250}
}
```

---

## Nested Structures

Support for arbitrary nesting of case classes.

```scala
case class Coordinates(lat: Double, lon: Double)
case class Location(name: String, coords: Coordinates)
case class Address(street: String, city: String, location: Location)
case class Company(_id: ObjectId, name: String, headquarters: Address)

val company = Company(
  new ObjectId(),
  "TechCorp",
  Address(
    "123 Tech St",
    "San Francisco",
    Location(
      "Downtown",
      Coordinates(37.7749, -122.4194)
    )
  )
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Coordinates]
  .register[Location]
  .register[Address]
  .register[Company]
  .build
```

---

## Custom Field Names

Use `@BsonProperty` to customize field names in MongoDB.

```scala
import org.mongodb.scala.bson.annotations.BsonProperty

case class User(
  _id: ObjectId,
  @BsonProperty("n") name: String,        // Stored as "n"
  @BsonProperty("e") email: String,       // Stored as "e"
  @BsonProperty("a") age: Int             // Stored as "a"
)
```

**Stored in MongoDB:**
```json
{
  "_id": ObjectId("..."),
  "n": "Alice",
  "e": "alice@example.com",
  "a": 30
}
```

**Use case:** Reduce document size for high-volume collections.

---

## Scala 3 Enums

Support for simple Scala 3 enums (no parameters or custom fields).

### String-Based Enum

```scala
enum Priority:
  case Low, Medium, High

case class Task(_id: ObjectId, title: String, priority: Priority)

import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider[Priority]())
  .register[Task]
  .build
```

**Stored in MongoDB:**
```json
{
  "_id": ObjectId("..."),
  "title": "Complete report",
  "priority": "High"
}
```

### Ordinal-Based Enum

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider[Priority](useOrdinal = true))
  .register[Task]
  .build
```

**Stored in MongoDB:**
```json
{
  "_id": ObjectId("..."),
  "title": "Complete report",
  "priority": 2
}
```

**Note:** Enums with parameters are supported using custom codec providers (see [Enum Support Guide](ENUM_SUPPORT.md) for details).

---

## Opaque Types

Full support for Scala 3 opaque types.

```scala
opaque type UserId = String
object UserId:
  def apply(value: String): UserId = value
  extension (id: UserId) def value: String = id

opaque type Email = String
object Email:
  def apply(value: String): Email = value
  extension (e: Email) def value: String = e

case class User(_id: ObjectId, userId: UserId, email: Email)

// Opaque types are automatically handled - no extra configuration needed
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build
```

---

## Default Values

Automatic handling of default parameter values.

```scala
case class Settings(
  _id: ObjectId,
  theme: String = "light",
  fontSize: Int = 14,
  notifications: Boolean = true,
  language: String = "en"
)

// If fields are missing in MongoDB, defaults are used
val settings = Settings(_id = new ObjectId())
collection.insertOne(settings).toFuture()

// When reading from DB, missing fields use defaults
val retrieved = collection.find().first().toFuture()
// retrieved.theme will be "light" if not present in DB
```

---

## Type-Safe Configuration

Use `CodecConfig` for type-safe codec configuration.

```scala
import io.github.mbannour.mongo.codecs.{CodecConfig, NoneHandling}

// Define configuration
given CodecConfig = CodecConfig(
  noneHandling = NoneHandling.Ignore,
  discriminatorField = "_type"
)

// Apply to registry
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[User]
  .build

// Or use fluent API
val registry2 = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .encodeNone  // or .ignoreNone
  .register[User]
  .build
```

---

## Type-Safe Field Path Resolution

Compile-time safe MongoDB field path extraction with `MongoFieldResolver`.

```scala
import io.github.mbannour.fields.MongoFieldResolver

case class Address(street: String, city: String, zipCode: Int)
case class Person(_id: ObjectId, name: String, age: Int, address: Option[Address])

// Generate field paths at compile-time
object PersonFields extends MongoFieldResolver[Person]

// Type-safe field references
PersonFields.name           // "name"
PersonFields.age            // "age"
PersonFields.address.city   // "address.city"
PersonFields.address.zipCode // "address.zipCode"

// Use in queries
import org.mongodb.scala.model.Filters

collection.find(
  Filters.and(
    Filters.eq(PersonFields.name, "Alice"),
    Filters.eq(PersonFields.address.city, "Springfield")
  )
).toFuture()

// Compile-time error for invalid fields
// PersonFields.invalid  // ← Compilation error!
```

---

## Testing Utilities

`CodecTestKit` provides utilities for testing codecs without MongoDB.

Add testkit as a test-only dependency:

```scala
libraryDependencies +=
  "io.github.mbannour" %% "mongoscala3codec-testkit" % "0.0.10" % Test
```

### Round-Trip Testing

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit

case class User(_id: ObjectId, name: String, age: Int)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build

given codec: Codec[User] = registry.get(classOf[User])

val user = User(new ObjectId(), "Alice", 30)

// Assert encode/decode symmetry
CodecTestKit.assertCodecSymmetry(user)  // ✅ Pass if codec works correctly
```

### BSON Structure Inspection

```scala
val bsonDoc = CodecTestKit.toBsonDocument(user)
println(bsonDoc.toJson())
// Output: {"_id": {"$oid": "..."}, "name": "Alice", "age": 30}

assert(bsonDoc.containsKey("name"))
assert(bsonDoc.getString("name").getValue == "Alice")
```

### Round-Trip with Assertion

```scala
val roundTripped = CodecTestKit.roundTrip(user)
assert(roundTripped == user)
```
---
### Summary

The library is designed for **concrete case class codecs**, not polymorphic type resolution. This design choice:
- ✅ Provides compile-time type safety
- ✅ Eliminates runtime reflection overhead
- ✅ Generates optimal code
- ❌ Requires explicit typing of fields

For more details, see [FAQ & Troubleshooting](FAQ.md).

---

## Performance Characteristics

- **Codec Generation:** Compile-time (zero runtime cost)
- **Encoding/Decoding:** Comparable to hand-written codecs
- **Memory:** Minimal overhead, no reflection
- **Binary Size:** Generated code is optimized

---

## Next Steps

- 🔧 [How It Works](HOW_IT_WORKS.md) - Understand the internals
- 🚀 [Migration Guide](MIGRATION.md) - Migrate from other libraries
- ❓ [FAQ & Troubleshooting](FAQ.md) - Common issues and solutions
