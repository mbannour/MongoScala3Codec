# MongoScala3Codec

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.5-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)

MongoScala3Codec is a macro-based library for BSON serialization and deserialization of Scala 3 case classes. It generates BSON codecs at compile time, ensuring:

- **Strong Type Safety**: Compile-time validation of BSON serialization.
- **High Performance**: Optimized code generation for efficient BSON handling.
- **Minimal Boilerplate**: No need to write manual codec definitions.
- **Sealed Trait Support**: Automatic codec generation for sealed trait hierarchies.
- **Flexible Configuration**: Type-safe configuration for codec behavior.

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
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.5"
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

✅ **Collections of concrete sealed trait types**
```scala
sealed trait Status
case class Active(since: Long) extends Status
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
case class Completed(timestamp: Long, transactionId: String) extends PaymentStatus

sealed trait Currency
case class USD(amount: Double) extends Currency
case class EUR(amount: Double) extends Currency

case class Transaction(_id: ObjectId, status: Completed, currency: USD)
```

### Limitations

⚠️ **Polymorphic fields not yet supported**  
Fields typed as the sealed trait itself (e.g., `method: PaymentMethod`) require additional codec infrastructure and are not currently supported. Use concrete types in your case class fields.

⚠️ **Case objects as sealed trait members**  
Case objects in sealed hierarchies are not fully supported yet. Use case classes instead.

### Custom Discriminator Fields

```scala
val registry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(CodecConfig(discriminatorField = "_type"))
  .derive[Car]
  .derive[Motorcycle]
  .build
```

---

## CodecConfig - Type-Safe Configuration

The new `CodecConfig` provides type-safe configuration for codec behavior:

```scala
import io.github.mbannour.mongo.codecs.{CodecConfig, NoneHandling}

// Configure None handling
val config = CodecConfig(
  noneHandling = NoneHandling.Ignore,        // Omit None fields from BSON
  discriminatorField = "_type"                // Custom discriminator for sealed traits
)

// Or use defaults
val defaultConfig = CodecConfig()  // NoneHandling.Encode, "_t" discriminator
```

### None Handling Strategies

**NoneHandling.Encode** (default)
```scala
val config = CodecConfig(noneHandling = NoneHandling.Encode)
// None values are encoded as BSON null
// { "name": "Alice", "email": null }
```

**NoneHandling.Ignore**
```scala
val config = CodecConfig(noneHandling = NoneHandling.Ignore)
// None values are omitted from the document
// { "name": "Alice" }
```

---

## Built-in Type Support

### Primitive Types
- ✅ String, Int, Long, Double, **Float**, Boolean
- ✅ Byte, Short, Char

### Special Types
- ✅ **UUID** (automatically serialized as String)
- ✅ ObjectId
- ✅ Option[T]

### Collections
- ✅ List[T], Seq[T]
- ✅ Set[T]
- ✅ Vector[T]
- ✅ Map[String, T]

### Custom Types
- ✅ Nested case classes
- ✅ Scala 3 enums (via EnumValueCodecProvider)
- ✅ Value classes (with custom codec)

---

## CodecTestKit - Testing Utilities

New testing utilities make it easy to test your codecs:

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit

// Round-trip testing
val person = Person(new ObjectId(), "Alice", 30, None)
CodecTestKit.assertCodecSymmetry(person)

// BSON structure validation
val expectedBson = BsonDocument(/* ... */)
CodecTestKit.assertBsonStructure(person, expectedBson)

// Convert to/from BSON
val bsonDoc = CodecTestKit.toBsonDocument(person)
val decoded = CodecTestKit.fromBsonDocument[Person](bsonDoc)
```

---

## MongoFieldResolver: Compile-Time Safe Field Paths

`MongoFieldResolver` enables compile-time safe extraction of MongoDB field names, including nested structures and custom field renaming.

```scala
import io.github.mbannour.fields.MongoFieldMapper

val dbField = MongoFieldMapper.asMap[Person]("address.city")
// Returns the actual MongoDB field name, respecting @BsonProperty annotations
```

If you pass a field that doesn't exist, an exception is thrown with a helpful message.

---

## Using RegistryBuilder (Recommended API)

`RegistryBuilder` offers a fluent, immutable API to assemble a `CodecRegistry` while deriving case class codecs via macros.

### Basic Example
```scala
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig, NoneHandling}
import org.mongodb.scala.MongoClient

case class Address(street: String, city: String)
case class Person(name: String, address: Option[Address])

val registry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(CodecConfig(noneHandling = NoneHandling.Ignore))
  .derive[Address]
  .derive[Person]
  .build

given CodecRegistry = registry
```

### Adding Custom Codecs
```scala
import java.util.UUID
import org.bson.codecs.Codec

// Custom codec for legacy types
class LegacyIdCodec extends Codec[LegacyId]:
  // ...existing implementation...

val registry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .addCodec(new LegacyIdCodec)
  .derive[Person]
  .build
```

### Configuration Options
```scala
val registry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(CodecConfig(
    noneHandling = NoneHandling.Ignore,
    discriminatorField = "_type"
  ))
  .derive[Address]
  .derive[Person]
  .build

// Or using deprecated methods (still supported)
val registry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNonePolicy
  .withDiscriminatorField("_type")
  .derive[Person]
  .build
```

### Best Practices
1. **Derive nested types first**: Derive supporting types before parent types
2. **Choose None handling early**: Decide between encoding nulls vs omitting fields
3. **Use `given` for registry**: Make it available to macros at compile time
4. **Custom codecs before derive**: Add manual codecs before deriving types that use them

---

## Scala 3 Enum Support

### Simple Enums (Recommended)

```scala
enum Priority:
  case Low, Medium, High

// String-based encoding
import io.github.mbannour.mongo.codecs.EnumValueCodecProvider
val provider = EnumValueCodecProvider.forStringEnum[Priority]

// Ordinal-based encoding
val provider = EnumValueCodecProvider.forOrdinalEnum[Priority]

// Custom encoding
val provider = EnumValueCodecProvider[Priority, Int](
  toValue = _.ordinal,
  fromValue = Priority.fromOrdinal
)
```

### Enum Support Summary

| Enum Type                        | Supported? | Helper to Use                 |
|----------------------------------|:----------:|-------------------------------|
| Plain enum (no params)           |    ✅ Yes  | forStringEnum, forOrdinalEnum |
| Enum with methods/companion      |    ✅ Yes  | as above                      |
| Enum with parameters (ADT style) |    ❌ No   | Use sealed traits + case classes |
| Enum with custom value per case  |    ❌ No   | Use custom codec              |

---

## Advanced Examples

### Complex Case Class with All Features

```scala
import org.bson.types.ObjectId
import org.mongodb.scala.bson.annotations.BsonProperty

// Value class
case class EmployeeId(value: ObjectId) extends AnyVal

// Custom codec for value class
given Codec[EmployeeId] with
  def getEncoderClass = classOf[EmployeeId]
  def encode(w: BsonWriter, v: EmployeeId, ctx: EncoderContext): Unit = 
    w.writeObjectId(v.value)
  def decode(r: BsonReader, ctx: DecoderContext): EmployeeId = 
    EmployeeId(r.readObjectId())

// Nested type with annotation
case class Address(
  street: String, 
  city: String, 
  zipCode: Int, 
  employeeId: EmployeeId
)

// Complex parent with all field types
case class Person(
  _id: ObjectId,
  @BsonProperty("n") name: String,
  employeeId: Map[String, EmployeeId],
  middleName: Option[String],
  age: Int,
  height: Double,
  weight: Float,                    // Float support
  uuid: java.util.UUID,             // UUID support
  married: Boolean,
  address: Option[Address],
  nicknames: List[String],
  tags: Set[String],
  metadata: Map[String, String]
)

// Build registry
val registry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .addCodec(summon[Codec[EmployeeId]])
  .withConfig(CodecConfig(noneHandling = NoneHandling.Ignore))
  .derive[Address]
  .derive[Person]
  .build

given CodecRegistry = registry
```

---

## Default Values Support

MongoScala3Codec automatically handles case class default parameter values when decoding BSON documents. If a field is missing from the database, the codec will use the default value defined in the case class.

### Example

```scala
case class UserProfile(
  _id: ObjectId,
  name: String,
  score: Int = 100,              // Default value
  active: Boolean = true,        // Default value
  level: String = "beginner"     // Default value
)

given registry: CodecRegistry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .derive[UserProfile]
  .build

val collection: MongoCollection[UserProfile] = database.getCollection("users")

// Insert document with all fields
val fullUser = UserProfile(new ObjectId(), "Alice", 200, false, "expert")
collection.insertOne(fullUser).toFuture()

// Insert document with only required fields (score, active, level will use defaults)
import org.bson.Document
val partialDoc = new Document()
  .append("_id", new ObjectId())
  .append("name", "Bob")
  // score, active, level fields are missing

val docCollection = database.getCollection("users") // raw Document collection
docCollection.insertOne(partialDoc).toFuture()

// Retrieve and decode - missing fields will use default values
val user = collection.find(Filters.equal("_id", partialDoc.get("_id"))).first().head()
// user.score == 100 (default)
// user.active == true (default)
// user.level == "beginner" (default)
```

### How It Works

1. **Compile-time detection**: The macro detects which parameters have default values
2. **Missing field handling**: When a field is missing from BSON, the default value is used
3. **Explicit null vs missing**: `null` values in BSON are different from missing fields
   - Missing field → uses default value
   - `null` field → decodes as `null` (or `None` for Option types)

### Compatibility with None Handling

Default values work seamlessly with both None handling strategies:

```scala
case class Settings(
  name: String,
  email: Option[String] = None,    // Optional with default
  theme: String = "light",         // Required with default
  notifications: Boolean = true     // Required with default
)

// With NoneHandling.Ignore (default)
val config = CodecConfig(noneHandling = NoneHandling.Ignore)
// None values are omitted → defaults apply on decode

// With NoneHandling.Encode
val config = CodecConfig(noneHandling = NoneHandling.Encode)
// None values stored as null → null decoded as None
```

---

## Migration Guide

### From 0.0.4 to 0.0.5

**Old API (still works):**
```scala
val provider = CodecProviderMacro.createCodecProviderEncodeNone[Person]
```

**New API (recommended):**
```scala
given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)
given registry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY
val provider = CodecProviderMacro.createCodecProvider[Person]
```

**Or use RegistryBuilder:**
```scala
val registry = RegistryBuilder.Builder
  .base(MongoClient.DEFAULT_CODEC_REGISTRY)
  .encodeNonePolicy  // or .withConfig(CodecConfig(...))
  .derive[Person]
  .build
```

---

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| "No codec found for type X" | Missing codec registration | Add `.derive[X]` or custom codec before dependent types |
| None fields appear as null | Using NoneHandling.Encode | Use `CodecConfig(noneHandling = NoneHandling.Ignore)` |
| Missing fields in MongoDB | Using NoneHandling.Ignore | Use NoneHandling.Encode or provide default values |
| Compile error "not a case class" | Trying to derive codec for trait/class | Only case classes supported; use concrete types |
| UUID serialization error | No UUID support in old version | Upgrade to 0.0.5+ (built-in UUID support) |
| Float precision issues | Float stored as Double in BSON | Expected behavior; BSON uses Double type |

---

## Performance

Based on integration tests with 1000 concurrent operations:
- **Insert**: ~500-700ms for 1000 documents
- **Retrieve**: ~200-300ms for 1000 documents
- **Round-trip**: Full encode/decode cycle maintains data integrity

---

## Documentation

- 📚 [GitHub Wiki](https://github.com/mbannour/MongoScala3Codec/wiki) - Comprehensive guides and tutorials
- 🧪 [Integration Tests](integration/src/test/scala/io/github/mbannour/mongo/codecs/CodecProviderIntegrationSpec.scala) - 24 test cases covering all features
- 📖 [API Documentation](https://javadoc.io/doc/io.github.mbannour/mongoscala3codec_3) - ScalaDoc
- 📝 [IMPROVEMENTS.md](IMPROVEMENTS.md) - Detailed changelog of recent improvements

---

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Add tests for new features
4. Submit a pull request

For major changes, open an issue first to discuss your ideas.

---

## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

---

## Credits

**Main Developer:** Mohamed Ali Bannour  
Email: [med.ali.bennour@gmail.com](mailto:med.ali.bennour@gmail.com)

**Special Thanks:** To all contributors and users who have helped improve this library.

---

## Changelog

### Version 0.0.5 (Current)
- ✅ Added `CodecConfig` for type-safe configuration
- ✅ Added `NoneHandling` enum for flexible None value handling
- ✅ **Default parameter values support** - missing fields automatically use case class defaults
- ✅ **Complete primitive type support** - Byte, Short, Char fully supported
- ✅ **Fixed Option[Primitive] handling** - None values correctly decoded for primitive types
- ✅ Built-in UUID and Float primitive support
- ✅ Sealed trait support for concrete case class implementations
- ✅ Added `CodecTestKit` for testing utilities
- ✅ Added `BsonCodec` type class for functional programming patterns
- ✅ Improved `RegistryBuilder` with configuration support
- ✅ Enhanced compile-time error messages
- ✅ **29 integration tests** covering all features (up from 24)
- ✅ Comprehensive documentation and examples

### Version 0.0.4
- Initial public release
- Basic case class codec generation
- Enum support
- MongoFieldResolver
