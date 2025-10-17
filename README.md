# MongoScala3Codec

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.6-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)
![Build Status](https://github.com/mbannour/MongoScala3Codec/workflows/Test%20Scala%20Library/badge.svg)

MongoScala3Codec is a macro-based library for BSON serialization and deserialization of Scala 3 case classes. It generates BSON codecs at compile time, ensuring:

- **Strong Type Safety**: Compile-time validation of BSON serialization.
- **High Performance**: Optimized code generation for efficient BSON handling.
- **Minimal Boilerplate**: No need to write manual codec definitions.
- **Sealed Trait Support**: Automatic codec generation for sealed trait hierarchies.
- **Flexible Configuration**: Type-safe configuration for codec behavior.
- **Pure Scala 3**: Built with opaque types and extension methods for idiomatic Scala 3 code.

---

## 🚀 Minimal Example - Get Started in 30 Seconds

Here's everything you need - just copy, paste, and run:

```scala
// 1. Imports - only 3 lines needed!
import org.bson.types.ObjectId
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig, NoneHandling}
import org.mongodb.scala.MongoClient

// 2. Define your domain models
// Nested case class example
case class Address(street: String, city: String, zipCode: Int)
case class Person(_id: ObjectId, name: String, address: Address, email: Option[String])

// Sealed trait (ADT) example
sealed trait Notification
case class EmailNotif(_id: ObjectId, recipient: String, subject: String) extends Notification
case class SmsNotif(_id: ObjectId, phone: String, message: String) extends Notification

// 3. Generate codecs - automatic, zero boilerplate!
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Address]      // Register nested type
  .register[Person]       // Register parent type
  .register[EmailNotif]   // Register sealed trait members
  .register[SmsNotif]
  .build

// 4. Use with MongoDB - fully type-safe!
val mongoClient = MongoClient("mongodb://localhost:27017")
val database = mongoClient.getDatabase("myapp").withCodecRegistry(registry)

// Type-safe collections
val people = database.getCollection[Person]("people")
val notifications = database.getCollection[EmailNotif]("notifications")

// Save and retrieve - it just works!
val person = Person(new ObjectId(), "Alice", Address("123 Main", "NYC", 10001), Some("alice@example.com"))
people.insertOne(person).toFuture()

val found = people.find().first().toFuture()
// found is type Person with nested Address - fully decoded!
```

**That's it!** No manual codec writing, no reflection, no runtime overhead. 

👉 **See [5-Minute Quickstart](docs/QUICKSTART.md)** for more examples and explanations.

---

## 📚 Documentation

- **[5-Minute Quickstart](docs/QUICKSTART.md)** - Get started immediately
- **[BSON Type Mapping](docs/BSON_TYPE_MAPPING.md)** - Complete type reference (35+ types)
- **[Feature Overview](docs/FEATURES.md)** - Complete feature guide with examples
- **[ADT Patterns](docs/ADT_PATTERNS.md)** - Sealed traits and validation patterns
- **[MongoDB Interop](docs/MONGODB_INTEROP.md)** - Driver integration guide
- **[How It Works](docs/HOW_IT_WORKS.md)** - Scala 3 derivation internals explained
- **[Migration Guide](docs/MIGRATION.md)** - Migrate from other libraries
- **[FAQ & Troubleshooting](docs/FAQ.md)** - Common issues and solutions

---

## Features

- ✅ Automatic BSON codec generation for Scala 3 case classes
- ✅ **Support for default parameter values** - missing fields use defaults automatically
- ✅ Support for options and nested case classes
- ✅ **Sealed trait hierarchies** with concrete case class implementations
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

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.6"
```

---

## Quick Start

### 1. Define Your Case Classes and Enums

```scala
import org.bson.types.ObjectId
import org.mongodb.scala.bson.annotations.BsonProperty

case class Address(street: String, city: String, zipCode: Int)

case class Person(
  _id: ObjectId,
  @BsonProperty("n") name: String,
  age: Int,
  address: Option[Address]
)

enum Priority:
  case Low, Medium, High

case class Task(_id: ObjectId, title: String, priority: Priority)
```

### 2. Register Codecs

```scala
import org.bson.codecs.configuration.CodecRegistry
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig, NoneHandling}
import org.mongodb.scala.MongoClient

given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val codecRegistry: CodecRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(config)
  .register[Address]
  .register[Person]
  .register[Task]
  .build

given CodecRegistry = codecRegistry
```

### 3. Use with MongoDB

```scala
val mongoClient = MongoClient()
val database = mongoClient.getDatabase("test_db").withCodecRegistry(codecRegistry)
val peopleCollection = database.getCollection[Person]("people")
val taskCollection = database.getCollection[Task]("tasks")

// Insert documents
val person = Person(new ObjectId(), "Alice", 30, Some(Address("Main St", "City", 12345)))
peopleCollection.insertOne(person).toFuture()

val task = Task(new ObjectId(), "Complete report", Priority.High)
taskCollection.insertOne(task).toFuture()

// Query documents
val foundPerson = peopleCollection.find().first().head()
val foundTask = taskCollection.find().first().head()
```

---

## MongoPath – compile-time MongoDB field paths

`MongoPath` lets you extract MongoDB field paths from type-safe lambdas, at compile time. It respects `@BsonProperty` and supports a transparent hop into `Option` via a tiny helper.

This prevents runtime failures from using wrong/typoed field names in Filters, Updates, Deletes, Projections, Sorts, or any operation that needs a field path—paths are validated at compile time.

### Import and basics

```scala
import io.github.mbannour.fields.MongoPath
// For the transparent Option hop syntax
import io.github.mbannour.fields.MongoPath.syntax.?

import org.bson.types.ObjectId
import org.mongodb.scala.bson.annotations.BsonProperty

case class Address(street: String, @BsonProperty("zip") zipCode: Int)
case class User(_id: ObjectId, name: String, address: Option[Address])

MongoPath.of[User](_._id)              // "_id"
MongoPath.of[User](_.name)             // "name"
MongoPath.of[User](_.address)          // "address"
MongoPath.of[User](_.address.?.zipCode) // "address.zip"  (respects @BsonProperty)
```

### Use in queries

```scala
import org.mongodb.scala.model.Filters

// Find all users by nested zip code (Option hop handled transparently)
val filter = Filters.equal(MongoPath.of[User](_.address.?.zipCode), 12345)
val results = database.getCollection[User]("users").find(filter)
```

### Use in updates and deletes

```scala
import org.mongodb.scala.model.{Filters, Updates}

val users = database.getCollection[User]("users")

// Update: set name using a compile-time-checked path
val id: ObjectId = new ObjectId("...")
val update = Updates.set(MongoPath.of[User](_.name), "Bob")
users.updateOne(Filters.equal(MongoPath.of[User](_._id), id), update)

// Delete: filter by _id using a compile-time-checked path
users.deleteOne(Filters.equal(MongoPath.of[User](_._id), id))
```

Notes:
- Import `MongoPath.syntax.?` to enable the transparent `.?` hop for `Option`.
- The selector must be a simple field access chain (e.g. `_.a.b.c`).
- `@BsonProperty` values are used automatically when present on constructor params.
- Prevents stringly-typed bugs across Filters, Updates, Deletes, Projections, Sorts, Aggregations, etc.

---

## Testing Your Codecs with CodecTestKit

MongoScala3Codec includes **CodecTestKit**, a powerful testing utility that makes it easy to verify your BSON codecs work correctly before deploying to production.

### Quick Example

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit
import org.bson.types.ObjectId

case class User(_id: ObjectId, name: String, age: Int)

// Setup your codec
given config: CodecConfig = CodecConfig()
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build

given codec: Codec[User] = registry.get(classOf[User])

// Test codec symmetry - encode then decode should return the same value
val user = User(new ObjectId(), "Alice", 30)
CodecTestKit.assertCodecSymmetry(user)  // ✅ Passes if codec works correctly

// Inspect the BSON structure
val bsonDoc = CodecTestKit.toBsonDocument(user)
println(bsonDoc.toJson())
// Output: {"_id": {"$oid": "..."}, "name": "Alice", "age": 30}
```

### Why Use CodecTestKit?

✅ **Catch codec bugs early** - Test your codecs before they reach production  
✅ **Fast unit tests** - No need for MongoDB instance, tests run in milliseconds  
✅ **Verify BSON structure** - Ensure your data is stored exactly as expected  
✅ **Test edge cases** - Unicode, empty strings, boundary values, None handling  
✅ **Property-based testing** - Integrate with ScalaCheck for comprehensive testing

### Example: Testing with munit

```scala
import munit.FunSuite
import io.github.mbannour.mongo.codecs.CodecTestKit

class UserCodecTest extends FunSuite:
  
  case class User(_id: ObjectId, name: String, email: Option[String])
  
  given config: CodecConfig = CodecConfig()
  val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .register[User]
    .build
  
  given codec: Codec[User] = registry.get(classOf[User])
  
  test("User codec should handle round-trip encoding"):
    val user = User(new ObjectId(), "Bob", Some("bob@example.com"))
    CodecTestKit.assertCodecSymmetry(user)
  
  test("User codec should handle None values"):
    val userWithoutEmail = User(new ObjectId(), "Alice", None)
    val bson = CodecTestKit.toBsonDocument(userWithoutEmail)
    
    // Verify the BSON structure
    assert(bson.containsKey("name"))
    assert(bson.getString("name").getValue == "Alice")
```

### Example: Testing None Handling

```scala
// Test with NoneHandling.Ignore - None fields are omitted
given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(config)
  .register[User]
  .build

given codec: Codec[User] = registry.get(classOf[User])

val user = User(new ObjectId(), "Charlie", None)
val bson = CodecTestKit.toBsonDocument(user)

// With NoneHandling.Ignore, email field is omitted
assert(!bson.containsKey("email"))  // ✅ Field is not in BSON
```

### Example: Property-Based Testing with ScalaCheck

```scala
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties

object UserCodecProps extends Properties("UserCodec"):
  
  given config: CodecConfig = CodecConfig()
  val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .register[User]
    .build
  
  given codec: Codec[User] = registry.get(classOf[User])
  
  property("codec maintains symmetry for all users") = forAll { (name: String, age: Int) =>
    val user = User(new ObjectId(), name, age)
    val result = CodecTestKit.roundTrip(user)
    result == user
  }
```

### Available CodecTestKit Methods

| Method | Description |
|--------|-------------|
| `roundTrip[T](value: T): T` | Encode to BSON and decode back |
| `toBsonDocument[T](value: T): BsonDocument` | Convert case class to BSON for inspection |
| `fromBsonDocument[T](doc: BsonDocument): T` | Decode BSON to case class |
| `assertCodecSymmetry[T](value: T): Unit` | Assert encode→decode returns original value |
| `assertBsonStructure[T](value: T, expected: BsonDocument): Unit` | Verify exact BSON structure |
| `testRegistry(codecs: Codec[?]*): CodecRegistry` | Create minimal test registry |

---

## Sealed Trait Support

MongoScala3Codec supports sealed trait hierarchies where each case class implementation can be stored and retrieved from MongoDB.

### Supported Patterns

✅ **Concrete case classes from sealed hierarchies**
```scala
sealed trait Vehicle
case class Car(_id: ObjectId, brand: String, doors: Int) extends Vehicle
case class Motorcycle(_id: ObjectId, brand: String, cc: Int) extends Vehicle

given config: CodecConfig = CodecConfig()
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(config)
  .register[Car]
  .register[Motorcycle]
  .build

// Each type can be stored independently
val carCollection: MongoCollection[Car] = database.getCollection("vehicles")
val car = Car(new ObjectId(), "Toyota", 4)
carCollection.insertOne(car).toFuture()
```

✅ **Collections of concrete sealed trait implementations**
```scala
sealed trait Status
case class Active(since: Long) extends Status
case class Inactive(reason: String) extends Status

case class User(_id: ObjectId, name: String, statuses: List[Active])

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Active]
  .register[Inactive]
  .register[User]
  .build
```

### Limitations

⚠️ **Polymorphic sealed trait fields NOT supported**  
Fields typed as the sealed trait itself (e.g., `status: PaymentStatus`) are **NOT currently supported**. The codec infrastructure does not yet handle polymorphic field types.

**Current workaround**: Use concrete types in your case class field definitions:
```scala

case class Transaction(_id: ObjectId, status: PaymentStatus)  // Polymorphic field

case class Transaction(_id: ObjectId, status: Completed)      // Concrete type
```

⚠️ **Case objects in sealed hierarchies NOT fully supported**  
Case objects as sealed trait members are not fully supported yet. Use case classes with no parameters instead.

⚠️ **Sealed traits as collection elements**  
Collections containing sealed trait references (e.g., `List[PaymentStatus]`) are not yet supported. Use collections of concrete types.

---

## Scala 3 Opaque Types

MongoScala3Codec provides **seamless support for Scala 3 opaque types**, enabling type-safe domain modeling with zero runtime overhead. Opaque types work transparently with the codec generation system.

### Why Use Opaque Types?

Opaque types provide:
- ✅ **Compile-time type safety** - Prevent mixing of semantically different values
- ✅ **Zero runtime overhead** - Erased to underlying primitive types
- ✅ **Type-safe domain modeling** - Make invalid states unrepresentable
- ✅ **No wrapper allocation** - Unlike case classes, no object allocation overhead
- ✅ **Transparent BSON storage** - Stored as primitive types in MongoDB

### Basic Example

```scala
import org.bson.types.ObjectId
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig}

// Define opaque types for domain concepts
object DomainTypes:
  opaque type UserId = String
  object UserId:
    def apply(value: String): UserId = value
    extension (userId: UserId)
      def value: String = userId
  
  opaque type Email = String
  object Email:
    def apply(value: String): Email = value
    extension (email: Email)
      def value: String = email
  
  opaque type Age = Int
  object Age:
    def apply(value: Int): Age = value
    extension (age: Age)
      def value: Int = age

import DomainTypes.*

// Use opaque types in your case classes
case class UserProfile(
  _id: ObjectId,
  userId: UserId,
  email: Email,
  age: Age,
  displayName: String
)

// Register codec - works automatically with opaque types
given config: CodecConfig = CodecConfig()

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[UserProfile]
  .build

val collection = database
  .getCollection[UserProfile]("users")
  .withCodecRegistry(registry)

// Create and save user with type-safe opaque types
val user = UserProfile(
  _id = new ObjectId(),
  userId = UserId("user_12345"),
  email = Email("john@example.com"),
  age = Age(28),
  displayName = "John Doe"
)

collection.insertOne(user).toFuture()

// Query and retrieve - types are preserved
val retrieved = collection
  .find(Filters.equal("_id", user._id))
  .first()
  .head()

println(retrieved.userId.value)  // "user_12345"
println(retrieved.email.value)   // "john@example.com"
println(retrieved.age.value)     // 28
```
### Testing Opaque Types

Opaque types work seamlessly with `CodecTestKit`:

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit

given config: CodecConfig = CodecConfig()
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[UserProfile]
  .build

given codec: Codec[UserProfile] = registry.get(classOf[UserProfile])

val user = UserProfile(
  new ObjectId(),
  UserId("test_user"),
  Email("test@example.com"),
  Age(25),
  "Test User"
)

// Test codec symmetry with opaque types
CodecTestKit.assertCodecSymmetry(user)  // ✅ Passes

// Inspect BSON structure
val bson = CodecTestKit.toBsonDocument(user)
println(bson.toJson())
// {"_id": {...}, "userId": "test_user", "email": "test@example.com", "age": 25, ...}
```
---