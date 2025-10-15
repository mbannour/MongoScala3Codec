# MongoScala3Codec

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.6-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)

MongoScala3Codec is a macro-based library for BSON serialization and deserialization of Scala 3 case classes. It generates BSON codecs at compile time, ensuring:

- **Strong Type Safety**: Compile-time validation of BSON serialization.
- **High Performance**: Optimized code generation for efficient BSON handling.
- **Minimal Boilerplate**: No need to write manual codec definitions.
- **Sealed Trait Support**: Automatic codec generation for sealed trait hierarchies.
- **Flexible Configuration**: Type-safe configuration for codec behavior.
- **Pure Scala 3**: Built with opaque types and extension methods for idiomatic Scala 3 code.

> **Note:**
> - Scala 3 case classes are fully supported with automatic codec generation.
> - **Sealed traits are supported** for concrete case class implementations (see [Sealed Trait Support](#sealed-trait-support)).
> - For Scala 3 enums, use `EnumValueCodecProvider` to register a codec for your enum type.
> - **Not all Scala 3 enum types are supported.** Only plain enums (no parameters, no ADT/sealed traits, no custom fields) are fully supported.

---

## Features

- ✅ Automatic BSON codec generation for Scala 3 case classes
- ✅ **Support for default parameter values** - missing fields use defaults automatically
- ✅ Support for options and nested case classes
- ✅ **Sealed trait hierarchies** with concrete case class implementations
- ✅ Custom field name annotations (e.g., `@BsonProperty`)
- ✅ Compile-time safe MongoDB field path extraction via `MongoFieldResolver`
- ✅ Scala 3 enum support via `EnumValueCodecProvider`
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

### 2. Register Codecs (New Simplified API)

```scala
import org.bson.codecs.configuration.CodecRegistry
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig, NoneHandling}
import org.mongodb.scala.MongoClient

// Create registry with fluent builder API
val codecRegistry: CodecRegistry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(CodecConfig(noneHandling = NoneHandling.Ignore)) // Configure behavior
  .derive[Address]      // Automatically derive codec for Address
  .derive[Person]       // Automatically derive codec for Person
  .derive[Task]         // Automatically derive codec for Task
  .build

given CodecRegistry = codecRegistry // 👈 DO NOT FORGET THIS LINE!
```

**Or use the traditional approach:**

```scala
import io.github.mbannour.mongo.codecs.{CodecProviderMacro, EnumValueCodecProvider, CodecConfig, NoneHandling}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}

given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)
given baseRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY

val personProvider = CodecProviderMacro.createCodecProvider[Person]
val addressProvider = CodecProviderMacro.createCodecProvider[Address]
val taskProvider = CodecProviderMacro.createCodecProvider[Task]

val codecRegistry: CodecRegistry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(personProvider, addressProvider, taskProvider),
  MongoClient.DEFAULT_CODEC_REGISTRY
)

given CodecRegistry = codecRegistry
```

### 3. Use with MongoDB

```scala
val mongoClient = MongoClient()
val database = mongoClient.getDatabase("test_db").withCodecRegistry(codecRegistry)
val peopleCollection = database.getCollection[Person]("people")
val taskCollection = database.getCollection[Task]("tasks")
```

### 4. Insert and Query Documents

```scala
val person = Person(new ObjectId(), "Alice", 30, Some(Address("Main St", "City", 12345)))
peopleCollection.insertOne(person).toFuture()

val task = Task(new ObjectId(), "Complete report", Priority.High)
taskCollection.insertOne(task).toFuture()

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

given registry: CodecRegistry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .derive[Car]
  .derive[Motorcycle]
  .build

// Each type can be stored independently
val carCollection: MongoCollection[Car] = database.getCollection("vehicles")
val car = Car(new ObjectId(), "Toyota", 4)
carCollection.insertOne(car).toFuture()
```

given registry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .register[Car]
  .register[Motorcycle]
case class Inactive(reason: String) extends Status

case class User(_id: ObjectId, name: String, statuses: List[Active])

given registry: CodecRegistry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .derive[Active]
  .derive[Inactive]
  .derive[User]
  .build
```

✅ **Multiple sealed hierarchies in one case class**
```scala
sealed trait PaymentStatus
case class Pending(timestamp: Long) extends PaymentStatus
given registry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .register[Active]
  .register[Inactive]
  .register[User]

case class Transaction(_id: ObjectId, status: Completed, currency: USD)
```

### Limitations

⚠️ **Polymorphic sealed trait fields NOT supported**  
Fields typed as the sealed trait itself (e.g., `status: PaymentStatus`) are **NOT currently supported**. The codec infrastructure does not yet handle polymorphic field types.

**Current workaround**: Use concrete types in your case class field definitions:
```scala
// ❌ NOT supported
case class Transaction(_id: ObjectId, status: PaymentStatus)  // Polymorphic field

// ✅ Supported - use concrete types
case class Transaction(_id: ObjectId, status: Completed)      // Concrete type
```

⚠️ **Case objects in sealed hierarchies NOT fully supported**  
Case objects as sealed trait members are not fully supported yet. Use case classes with no parameters instead.

⚠️ **Sealed traits as collection elements**  
Collections containing sealed trait references (e.g., `List[PaymentStatus]`) are not yet supported. Use collections of concrete types.
