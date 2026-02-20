# FAQ and Troubleshooting

Frequently asked questions and solutions to common issues with MongoScala3Codec.

## Table of Contents

- [General Questions](#general-questions)
- [Compilation Errors](#compilation-errors)
- [Runtime Issues](#runtime-issues)
- [Configuration Questions](#configuration-questions)
- [Performance Questions](#performance-questions)
- [Integration Questions](#integration-questions)

---

## General Questions

### Q: What Scala versions are supported?

**A:** MongoScala3Codec supports Scala 3.3.x, 3.4.x, 3.5.x, 3.6.x, and 3.7.x. It does not support Scala 2.x.

```scala
// build.sbt
scalaVersion := "3.7.1"  // or any 3.3+
```

### Q: Does this work with Scala 2?

**A:** No. MongoScala3Codec is built exclusively for Scala 3 using its metaprogramming features. For Scala 2, consider using:
- `org.mongodb.scala.bson.codecs.Macros`
- ReactiveMongo
- Manual codec implementations

### Q: What's the difference between this and the official MongoDB Scala driver macros?

**A:** 

| Feature | Official Macros (Scala 2) | MongoScala3Codec |
|---------|---------------------------|------------------|
| Scala Version | Scala 2.x | Scala 3.3+ |
| Reflection | Runtime | Compile-time |
| Sealed Traits | Limited | Full support |
| Configuration | None | Type-safe (CodecConfig) |
| None Handling | Encode as null | Configurable (Encode/Ignore) |
| Error Messages | Generic | Detailed compile-time |
| Field Path Resolution | No | Yes (MongoFieldResolver) |

### Q: Can I use this in production?

**A:** Yes! The library:
- ✅ Has comprehensive test coverage (unit + integration)
- ✅ Uses compile-time code generation (no runtime reflection)
- ✅ Has been tested across multiple Scala 3 versions
- ✅ Follows MongoDB codec best practices
- ✅ Includes testing utilities for validation

### Q: Is this compatible with MongoDB versions X?

**A:** MongoScala3Codec generates standard BSON codecs compatible with all MongoDB versions supported by the official Java/Scala driver. Tested with MongoDB 4.x, 5.x, and 6.x.

---

## Compilation Errors

### Error: "Cannot generate codec for type X"

**Problem:**
```
[error] Cannot generate codec for type Foo
[error] No implicit Codec[Bar] found in scope
```

**Causes & Solutions:**

#### Cause 1: Nested type not registered
```scala
case class Address(city: String)
case class Person(name: String, address: Address)

// ❌ Missing Address codec
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Person]
  .build
```

**Solution:** Register nested types first:
```scala
// ✅ Register Address first
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Address]
  .register[Person]
  .build
```

#### Cause 2: Unsupported type
```scala
case class Data(value: CustomType)  // CustomType has no codec
```

**Solution:** Provide a custom codec:
```scala
given customCodec: Codec[CustomType] = new Codec[CustomType] {
  def encode(w: BsonWriter, v: CustomType, ctx: EncoderContext): Unit = ???
  def decode(r: BsonReader, ctx: DecoderContext): CustomType = ???
  def getEncoderClass: Class[CustomType] = classOf[CustomType]
}

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withCodec(customCodec)
  .register[Data]
  .build
```

---

### Error: "Not a case class"

**Problem:**
```
[error] Type Person is not a case class
```

**Cause:** Trying to register a non-case class:
```scala
class Person(val name: String)  // Not a case class

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Person]  // ❌ Error
  .build
```

**Solution:** Use case classes:
```scala
case class Person(name: String)  // ✅ Case class

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Person]
  .build
```

---

### Working with Sealed Traits and Classes

**✅ Sealed traits and classes are fully supported as of version 0.0.8!**

**Solution:** Use `registerSealed[T]` for automatic polymorphic codec generation:

```scala
sealed trait Status
case class Active(since: Long) extends Status
case class Inactive(reason: String) extends Status

case class User(name: String, status: Status)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Status]  // Registers Status + all subtypes
  .register[User]
  .build

// Works perfectly with automatic discriminator!
val user = User("Alice", Active(System.currentTimeMillis()))
// Encodes as: {"name": "Alice", "status": {"_type": "Active", "since": ...}}
```

**Key Features:**
- Automatic discriminator field (default: `_type`, configurable)
- Single registration call for entire hierarchy
- Works with collections: `List[Status]`, `Vector[Animal]`, etc.
- Supports sealed trait, sealed class, and sealed abstract class

**For simple enumerations without parameters, use Scala 3 enums:**
```scala
enum SimpleStatus:
  case Active, Inactive

case class User(status: SimpleStatus)

import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProviders(EnumValueCodecProvider.forStringEnum[SimpleStatus])
  .register[User]
  .build
```

**See [Sealed Trait Support Guide](SEALED_TRAIT_SUPPORT.md) for comprehensive examples.**

import org.bson.codecs.{Codec, IntegerCodec}

given Codec[Int] = new IntegerCodec().asInstanceOf[Codec[Int]]

val statusProvider = EnumValueCodecProvider[Status, Int](
  toValue = _.code,
  fromValue = code => Status.values.find(_.code == code).getOrElse(
    throw new IllegalArgumentException(s"Invalid status code: $code")
  )
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProviders(statusProvider)
  .register[User]
  .build
```

---

### Error: "Diverging implicit expansion"

**Problem:**
```
[error] Diverging implicit expansion for type Codec[MyType]
```

**Cause:** Circular dependency in type definitions:
```scala
case class Person(friend: Person)  // Self-referential
```

**Solution:** This is a limitation of compile-time derivation. Consider:

1. Use `Option` to break the cycle:
```scala
case class Person(name: String, friend: Option[Person])
```

2. Use a reference by ID:
```scala
case class Person(name: String, friendId: Option[ObjectId])
```

---

### Error: "Missing given instance"

**Problem:**
```
[error] No given instance of type io.github.mbannour.mongo.codecs.CodecConfig was found
```

**Cause:** Using a method that requires `CodecConfig` without providing it.

**Solution:** Provide a given instance:
```scala
import io.github.mbannour.mongo.codecs.{CodecConfig, NoneHandling}

given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[User]
  .build
```

---

## Runtime Issues

### Issue: "CodecConfigurationException: Can't find a codec for class X"

**Problem:**
```
org.bson.codecs.configuration.CodecConfigurationException: 
  Can't find a codec for class com.example.User
```

**Causes & Solutions:**

#### Cause 1: Forgot to use the registry
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build

// ❌ Not using the registry
val collection = database.getCollection[User]("users")
```

**Solution:** Apply the registry to the collection:
```scala
// ✅ Apply registry
val collection = database
  .getCollection[User]("users")
  .withCodecRegistry(registry)
```

#### Cause 2: Wrong collection type
```scala
// ❌ Generic Document collection
val collection: MongoCollection[Document] = 
  database.getCollection("users")
```

**Solution:** Use the typed collection:
```scala
// ✅ Typed collection
val collection: MongoCollection[User] = 
  database.getCollection[User]("users")
  .withCodecRegistry(registry)
```

---

### Issue: None values appearing as null in MongoDB

**Problem:**
```scala
case class User(name: String, email: Option[String])
val user = User("Alice", None)
// Stored as: {"name": "Alice", "email": null}
```

**Solution:** Configure `NoneHandling.Ignore`:
```scala
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[User]
  .build

// Now stored as: {"name": "Alice"}
```

---

### Issue: "BsonInvalidOperationException: Missing discriminator field '_type'" after Updates.set

**Problem:**
```scala
sealed trait Animal
case class Dog(name: String, breed: String) extends Animal
case class Bird(name: String, canFly: Boolean) extends Animal

case class User(_id: ObjectId, name: String, pet: Animal)

// Update a sealed-trait field using Updates.set
collection.updateOne(
  Filters.eq("_id", userId),
  Updates.set("pet", Bird("Tweety", true))
).toFuture()

// Later, reading it back throws:
// BsonInvalidOperationException: Missing discriminator field '_type'
```

**Cause:** When `Updates.set` encodes a value, the MongoDB driver calls `registry.get(classOf[Bird])` — the concrete subtype codec — rather than the `Animal` sealed trait codec. In older versions of this library, the concrete subtype codec did not write the `_type` discriminator, so the stored document was not self-describing and could not be decoded as an `Animal`.

**Solution:** This was fixed in the library. Ensure you are using a version that includes the fix, and register the sealed hierarchy with `registerSealed[T]` (not bare `register[T]` for individual subtypes):

```scala
// ✅ Correct - registerSealed produces discriminator-aware codecs for all subtypes
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Animal]   // covers Animal, Dog, Cat, Bird
  .register[User]
  .build

// ❌ Incorrect - bare register[T] codecs do NOT write the discriminator
val badRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Dog]
  .register[Bird]
  .register[User]
  .build
```

After the fix, `Updates.set("pet", Bird("Tweety", true))` stores `{"_type": "Bird", "name": "Tweety", "canFly": true}`, which decodes correctly as `Animal`.

---

### Issue: Default values not being used

**Problem:**
```scala
case class Settings(theme: String = "light")

// Reading from DB where theme is missing
val settings = collection.find().first().toFuture()
// settings.theme is null instead of "light"
```

**Cause:** The field exists in the document with a null value.

**Solution:** Ensure the field is completely missing from the document, or handle null values:
```scala
// Option 1: Use Option with default
case class Settings(theme: Option[String] = Some("light"))

// Option 2: Clean your data - remove null fields
import org.mongodb.scala.model.Updates
collection.updateMany(
  Filters.exists("theme", false),
  Updates.unset("theme")
)
```

---

## Configuration Questions


### Q: How do I configure different settings for different types?

**A:** Use multiple registries or create types in separate registration blocks:

```scala
// Registry 1: Ignore None values
given config1: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)
val registry1 = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(config1)
  .register[User]
  .build

// Registry 2: Encode None as null
given config2: CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)
val registry2 = RegistryBuilder
  .from(registry1)
  .withConfig(config2)
  .register[Product]
  .build
```

---

## Performance Questions

### Q: What's the runtime performance compared to manual codecs?

**A:** MongoScala3Codec generates code that performs comparably to hand-written codecs:
- **Encoding:** Within 5% of manual implementations
- **Decoding:** Within 5-10% of manual implementations
- **Memory:** No additional allocations beyond object creation

The library uses compile-time generation, so there's zero runtime reflection overhead.

### Q: Does it slow down compilation?

**A:** Yes, slightly:
- **Small projects (< 20 types):** +1-2 seconds
- **Medium projects (20-100 types):** +3-5 seconds
- **Large projects (> 100 types):** +5-10 seconds

This is a one-time cost during compilation. Incremental compilation helps minimize the impact.

### Q: How can I reduce compilation time?

**A:** 

1. **Use incremental compilation** (enabled by default in sbt)
2. **Split large codebases** into modules
3. **Register only types you need** (don't register unused types)
4. **Use sbt's incremental compiler settings:**

```scala
// build.sbt
ThisBuild / incOptions := {
  incOptions.value.withRecompileAllFraction(0.1)
}
```

---

## Integration Questions

### Q: Can I use this with Akka Streams?

**A:** Yes! MongoScala3Codec generates standard codecs compatible with reactive streams:

```scala
import org.mongodb.scala._
import akka.stream.scaladsl._

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build

val collection = database
  .getCollection[User]("users")
  .withCodecRegistry(registry)

// Use with Akka Streams
Source
  .fromPublisher(collection.find())
  .map(user => processUser(user))
  .runWith(Sink.seq)
```

### Q: Does it work with Play Framework?

**A:** Yes, integrate it in your Play application:

```scala
// app/models/User.scala
case class User(name: String, email: String)

// app/dao/MongoDAO.scala
import io.github.mbannour.mongo.codecs.RegistryBuilder
import javax.inject._
import org.mongodb.scala._

@Singleton
class MongoDAO @Inject()(config: Configuration) {
  private val mongoClient = MongoClient(config.get[String]("mongodb.uri"))
  
  private val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .register[User]
    .build
  
  private val database = mongoClient
    .getDatabase("myapp")
    .withCodecRegistry(registry)
  
  val users: MongoCollection[User] = 
    database.getCollection[User]("users")
}
```

### Q: Can I use it with ZIO or Cats Effect?

**A:** Yes! The MongoDB Scala driver returns Observables that can be converted:

```scala
// ZIO example
import zio._
import org.mongodb.scala._

def findUser(id: String): Task[User] = ZIO.fromFuture { implicit ec =>
  collection.find(Filters.eq("_id", id)).first().toFuture()
}

// Cats Effect example
import cats.effect._
import scala.concurrent.ExecutionContext

def findUser(id: String)(implicit cs: ContextShift[IO]): IO[User] =
  IO.fromFuture(IO(collection.find(Filters.eq("_id", id)).first().toFuture()))
```

### Q: How do I test code that uses these codecs?

**A:** Use `CodecTestKit` for unit tests without MongoDB:

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit
import org.scalatest.flatspec.AnyFlatSpec

class UserCodecSpec extends AnyFlatSpec {
  val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .register[User]
    .build
  
  given codec: Codec[User] = registry.get(classOf[User])
  
  "User codec" should "round-trip correctly" in {
    val user = User("Alice", "alice@example.com")
    CodecTestKit.assertCodecSymmetry(user)
  }
  
  it should "encode to correct BSON structure" in {
    val user = User("Bob", "bob@example.com")
    val bson = CodecTestKit.toBsonDocument(user)
    
    assert(bson.getString("name").getValue == "Bob")
    assert(bson.getString("email").getValue == "bob@example.com")
  }
}
```

For integration tests with real MongoDB, use Testcontainers (see existing integration tests in the project).

---

## Troubleshooting Tips

### Enable Macro Debugging

See generated code by enabling compiler flags:

```scala
// build.sbt
scalacOptions ++= Seq(
  "-Xprint:typer",     // Print generated code
  "-Xcheck-macros"     // Validate macro expansions
)
```

### Check BSON Structure

Inspect what's actually being written to MongoDB:

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit

val user = User("Alice", 30)
val bson = CodecTestKit.toBsonDocument(user)
println(bson.toJson())  // See exact BSON structure
```

### Verify Registry Contents

Check if your codec is registered:

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build

val codec = registry.get(classOf[User])
println(s"Codec found: ${codec != null}")
```

---

## Getting Help

If you can't find a solution here:

1. **Check the examples:** `/examples` directory in the repository
2. **Read the integration tests:** `/integration/src/test/scala`
3. **Search existing issues:** [GitHub Issues](https://github.com/mbannour/MongoScala3Codec/issues)
4. **Open a new issue:** Provide a minimal reproducible example

---

## Next Steps

- 📖 [Quickstart Guide](QUICKSTART.md) - Get started in 5 minutes
- 🔧 [Feature Overview](FEATURES.md) - Learn about all features
- 🚀 [Migration Guide](MIGRATION.md) - Migrate from other libraries

