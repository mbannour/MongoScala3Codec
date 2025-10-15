# Feature Overview

MongoScala3Codec provides comprehensive BSON codec generation for Scala 3 applications. This guide covers all features with practical examples.

## Table of Contents

- [Automatic Codec Generation](#automatic-codec-generation)
- [Case Classes](#case-classes)
- [Sealed Traits (ADTs)](#sealed-traits-adts)
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

---

## Sealed Traits (ADTs)

Automatic codec generation for sealed trait hierarchies with discriminator support.

### Basic ADT

```scala
sealed trait Shape
case class Circle(_id: ObjectId, radius: Double) extends Shape
case class Rectangle(_id: ObjectId, width: Double, height: Double) extends Shape
case class Triangle(_id: ObjectId, base: Double, height: Double) extends Shape

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Shape]  // ← Automatically registers all subtypes
  .build
```

**Stored in MongoDB:**
```json
{
  "_id": ObjectId("..."),
  "_t": "Circle",
  "radius": 5.0
}
```

### Custom Discriminator Field

```scala
given CodecConfig = CodecConfig(discriminatorField = "_type")

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[Shape]
  .build
```

**Stored in MongoDB:**
```json
{
  "_id": ObjectId("..."),
  "_type": "Circle",
  "radius": 5.0
}
```

### Complex ADT Example

```scala
sealed trait Event
case class UserEvent(_id: ObjectId, userId: String, action: String, timestamp: Long) extends Event
case class SystemEvent(_id: ObjectId, level: String, message: String, timestamp: Long) extends Event
case class ErrorEvent(_id: ObjectId, error: String, stackTrace: Option[String], timestamp: Long) extends Event

// Query specific event type
collection
  .find(Filters.eq("_t", "UserEvent"))
  .toFuture()
```

---

## Optional Fields

Handle `Option[T]` with two strategies: encode as `null` or omit from document.

### Omit None Values (Recommended)

```scala
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

case class User(_id: ObjectId, name: String, email: Option[String])

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[User]
  .build

val user = User(new ObjectId(), "Alice", None)
// Stored as: {"_id": ObjectId("..."), "name": "Alice"}
```

### Encode None as null

```scala
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
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

**Note:** Only plain enums are supported. Enums with parameters or ADT-style enums should use sealed traits instead.

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

