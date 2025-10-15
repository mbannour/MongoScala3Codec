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

> **Note:**
> - Scala 3 case classes are fully supported with automatic codec generation.
> - For Scala 3 enums, use `EnumValueCodec` to register a codec for your enum type.
> - **Not all Scala 3 enum types are supported.** Only plain enums (no parameters, no ADT/sealed traits, no custom fields) are fully supported.

---

## 📚 Documentation

- **[5-Minute Quickstart](docs/QUICKSTART.md)** - Get started immediately
- **[Feature Overview](docs/FEATURES.md)** - Complete feature guide with examples
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
- ✅ Compile-time safe MongoDB field path extraction via `MongoFieldResolver`
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

### Type Safety at Compile Time

Opaque types prevent type confusion at compile time:

```scala
val userId = UserId("user_123")
val email = Email("test@example.com")
val age = Age(30)

// ✅ These compile
userId.value   // "user_123"
email.value    // "test@example.com"
age.value      // 30

// ❌ These DO NOT compile - type safety enforced
val wrong1: UserId = email      // Compile error: type mismatch
val wrong2: Email = userId      // Compile error: type mismatch
val wrong3: Age = userId        // Compile error: type mismatch
```

### BSON Storage - Zero Overhead

Opaque types are stored as their underlying primitive types in MongoDB:

```scala
// In MongoDB, the document looks like:
{
  "_id": ObjectId("..."),
  "userId": "user_12345",        // Stored as String
  "email": "john@example.com",   // Stored as String
  "age": 28,                     // Stored as Int
  "displayName": "John Doe"
}

// No wrapper objects, no performance penalty
// Type safety exists only at compile time
```

### Querying with Opaque Types

Opaque types are transparent in MongoDB queries:

```scala
import org.mongodb.scala.model.Filters

val collection = database.getCollection[UserProfile]("users")

// Query using the underlying type value
val youngUsers = collection
  .find(Filters.lt("age", 25))
  .toFuture()

// Range queries work naturally
val midAgeUsers = collection
  .find(Filters.and(
    Filters.gte("age", 25),
    Filters.lte("age", 35)
  ))
  .toFuture()

// String field queries
val specificUser = collection
  .find(Filters.eq("userId", "user_12345"))
  .first()
  .head()
```

### Advanced: Opaque Types with Validation

Combine opaque types with smart constructors for validated domain types:

```scala
object ValidatedTypes:
  opaque type Email = String
  object Email:
    def apply(value: String): Either[String, Email] =
      if value.contains("@") then Right(value)
      else Left("Invalid email format")
    
    def unsafe(value: String): Email = value
    
    extension (email: Email)
      def value: String = email
  
  opaque type PositiveInt = Int
  object PositiveInt:
    def apply(value: Int): Either[String, PositiveInt] =
      if value > 0 then Right(value)
      else Left("Must be positive")
    
    def unsafe(value: Int): PositiveInt = value
    
    extension (n: PositiveInt)
      def value: Int = n

import ValidatedTypes.*

case class Product(
  _id: ObjectId,
  name: String,
  price: PositiveInt,
  contactEmail: Email
)

// Use validated construction
val emailResult = Email("invalid")  // Left("Invalid email format")
val validEmail = Email("contact@example.com")  // Right(Email)

validEmail match
  case Right(email) =>
    val product = Product(
      new ObjectId(),
      "Widget",
      PositiveInt.unsafe(100),
      email
    )
    // Save to MongoDB with type-safe validated values
  case Left(error) =>
    println(s"Validation failed: $error")
```

### Opaque Types in Collections

Opaque types work seamlessly in collections:

```scala
object Types:
  opaque type TagId = String
  object TagId:
    def apply(value: String): TagId = value
    extension (id: TagId) def value: String = id

import Types.*

case class Article(
  _id: ObjectId,
  title: String,
  tags: List[TagId],
  authorIds: Set[UserId]
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Article]
  .build

val article = Article(
  _id = new ObjectId(),
  title = "Scala 3 Guide",
  tags = List(TagId("scala"), TagId("functional")),
  authorIds = Set(UserId("author_1"), UserId("author_2"))
)

// Collections of opaque types stored as primitive arrays
// In MongoDB: {"tags": ["scala", "functional"], "authorIds": ["author_1", "author_2"]}
```

### Combining Opaque Types with RegistryBuilder

The library's `RegistryBuilder` is itself an opaque type, providing type-safe builder patterns:

```scala
// Two levels of type safety:
// 1. RegistryBuilder opaque type (type-safe API)
// 2. Domain opaque types (UserId, Email, etc.)

val builder: RegistryBuilder = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)

val configured: RegistryBuilder = builder.ignoreNone
val withCodec: RegistryBuilder = configured.register[UserProfile]
val registry: CodecRegistry = withCodec.build

// All operations are type-safe with fluent chaining
val registryFluent = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone
  .discriminator("_type")
  .register[UserProfile]
  .build
```

### Best Practices

✅ **Use opaque types for domain concepts** - UserId, Email, Price, etc.  
✅ **Add validation in smart constructors** - Keep invalid states unrepresentable  
✅ **Provide extension methods for access** - Use `.value` convention  
✅ **Group related opaque types** - Use objects to namespace types  
✅ **No runtime overhead** - Perfect for high-performance applications  

❌ **Don't overuse** - Simple strings/ints don't always need wrapping  
❌ **Don't expose underlying value** - Keep implementation details hidden  

### Performance Benefits

```scala
// Traditional approach with case classes
case class UserId(value: String)  // ❌ Allocates object wrapper
case class Email(value: String)   // ❌ Allocates object wrapper

// Opaque type approach
opaque type UserId = String        // ✅ Zero allocation
opaque type Email = String         // ✅ Zero allocation

// With 1 million users, opaque types save:
// - No object allocations (saves heap memory)
// - No indirection (faster field access)
// - Same type safety as case classes
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

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
