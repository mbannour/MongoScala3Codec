# MongoScala3Codec

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.4-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)

MongoScala3Codec is a macro-based library for BSON serialization and deserialization of Scala 3 case classes. It generates BSON codecs at compile time, ensuring:

- **Strong Type Safety**: Compile-time validation of BSON serialization.
- **High Performance**: Optimized code generation for efficient BSON handling.
- **Minimal Boilerplate**: No need to write manual codec definitions.

> **Note:**
> - Only Scala 3 case classes are supported. Sealed traits (ADTs) are **NOT** supported.
> - For Scala 3 enums, use `EnumValueCodecProvider` to register a codec for your enum type.
> - **Not all Scala 3 enum types are supported.** See the summary table below for details on which enum types are supported and which require workarounds.
- Only plain enums (no parameters, no ADT/sealed traits, no custom fields) are fully supported. See the table below for a summary of supported and unsupported enum types.

---

## Features

- Automatic BSON codec generation for Scala 3 case classes
- Support for default values, options, and nested case classes
- Custom field name annotations (e.g., `@BsonProperty`)
- Compile-time safe MongoDB field path extraction via `MongoFieldResolver`
- Scala 3 enum support via `EnumValueCodecProvider`

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

### 2. Register Codecs

```scala
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import io.github.mbannour.mongo.codecs.CodecProviderMacro
import io.github.mbannour.mongo.codecs.EnumValueCodecProvider
import org.mongodb.scala.MongoClient

val personProvider = CodecProviderMacro.createCodecProviderEncodeNone[Person]
val addressProvider = CodecProviderMacro.createCodecProviderEncodeNone[Address]
val taskProvider = CodecProviderMacro.createCodecProviderEncodeNone[Task]
val priorityEnumProvider = EnumValueCodecProvider[Priority, String](
  toValue = _.toString,
  fromValue = str => Priority.valueOf(str)
)

val codecRegistry: CodecRegistry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(personProvider, addressProvider, taskProvider, priorityEnumProvider),
  MongoClient.DEFAULT_CODEC_REGISTRY
)

given CodecRegistry = codecRegistry // 👈 DO NOT FORGET THIS LINE!
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
peopleCollection.insertOne(person)

val task = Task(new ObjectId(), "Complete report", Priority.High)
taskCollection.insertOne(task)

val foundPerson = peopleCollection.find().first().head()
val foundTask = taskCollection.find().first().head()
```

---

## MongoFieldResolver: Compile-Time Safe Field Paths

`MongoFieldResolver` enables compile-time safe extraction of MongoDB field names, including nested structures and custom field renaming.

```scala
import io.github.mbannour.fields.MongoFieldMapper
val dbField = MongoFieldMapper.asMap[Person]("address.city")
```
If you pass a field that doesn't exist, an exception is thrown with a helpful message.

---

## Documentation

- See the [GitHub Wiki](https://github.com/mbannour/MongoScala3Codec/wiki) for guides, tutorials, and references.
- For a full working example, see the integration tests in `integration/src/test/scala/io/github/mbannour/mongo/codecs/CodecProviderIntegrationSpec.scala`.
- **Important Limitations:**
  - Only Scala 3 case classes are supported. Sealed traits (ADTs) are **NOT** supported.
  - For Scala 3 enums, use `EnumValueCodecProvider` to register a codec for your enum type.
  - **Not all Scala 3 enum types are supported.** See the summary table below for details on which enum types are supported and which require workarounds.
    - Only plain enums (no parameters, no ADT/sealed traits, no custom fields) are fully supported. See the table below for a summary of supported and unsupported enum types.

---

### 🚀 Adding an enum codec (one line)

```scala
import io.github.mbannour.mongo.codecs.EnumValueCodecProvider
import org.bson.codecs.StringCodec

given Codec[String] = new StringCodec()           // 1) supply a String codec

val enumProvider = EnumValueCodecProvider.forStringEnum[Priority] // 2) done!
```

For custom representations:

```scala
import org.bson.codecs.IntegerCodec
given Codec[Int] = new IntegerCodec()
val provider = EnumValueCodecProvider.forOrdinalEnum[Priority]
```

And if you need total control:

```scala
EnumValueCodecProvider[Priority, Boolean](
  _.ordinal == 0,                     // toValue
  bool => if bool then Priority.Low else Priority.High // fromValue
)
```

You are now covering all practical cases for serializing Scala 3 enums as BSON with MongoDB, as long as the enums:

- Are “plain” enums (not parameterized, not ADTs/sealed traits, not enums with additional fields).

### Summary Table
| Enum Type                        | Supported? | Helper to Use                 |
|----------------------------------|:----------:|-------------------------------|
| Plain enum (no params)           |    Yes     | forStringEnum, forOrdinalEnum |
| Enum with methods/companion      |    Yes     | as above                      |
| Enum with parameters (ADT style) |     No     | Use your own codec            |
| Enum with custom value per case  |     No     | Use your own codec            |

---

## How to Transform MongoDB Sealed Trait/ADT to Scala 3 Enum

MongoDB does not natively support Scala 3 sealed traits or ADT-style enums. If you want to represent ADTs or sealed traits in MongoDB, you should:

1. **Refactor your ADT/sealed trait to a plain Scala 3 enum if possible.**
2. **Use a custom codec** (see below) if you need to serialize/deserialize ADT-style enums or sealed traits.
3. **For plain enums**, use the built-in helpers:
  - `EnumValueCodecProvider.forStringEnum[YourEnum]` (stores as string)
  - `EnumValueCodecProvider.forOrdinalEnum[YourEnum]` (stores as ordinal)

**Example: Transforming a sealed trait to an enum**

```scala
// Original ADT
sealed trait Priority
case object Low extends Priority
case object Medium extends Priority
case object High extends Priority

// Scala 3 enum equivalent
enum Priority:
  case Low, Medium, High
```

**Register the enum codec:**
```scala
import io.github.mbannour.mongo.codecs.EnumValueCodecProvider
import org.bson.codecs.StringCodec
given Codec[String] = new StringCodec() // 👈 required for string-based enum codecs
val enumProvider = EnumValueCodecProvider.forStringEnum[Priority]
```

**Note:**
- If your ADT has parameters or custom fields, you must write your own codec using `CodecProviderMacro` or a manual implementation.
- See the summary table above for supported enum types.

---

## How to Define and Use a CodecRegistry with MongoScala3Codec

Follow these steps to make sure your codecs work for all your case classes, enums, and nested types!

### 1️ Define Your Data Model
```scala
import org.bson.types.ObjectId

final case class Address(street: String, city: String, zipCode: Int)
final case class Person(_id: ObjectId, name: String, age: Int, address: Option[Address])
enum Priority:
  case Low, Medium, High
final case class Task(_id: ObjectId, title: String, priority: Priority)
```

### 2 Import Required Dependencies
```scala
import org.mongodb.scala.MongoClient
import org.bson.codecs.StringCodec
import io.github.mbannour.mongo.codecs.{CodecProviderMacro, EnumValueCodecProvider}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
```

### 3 Create Codec Providers for All Your Types
List every type you want to store, including all case classes and enums (and nested types!).

```scala
object Codecs:
  given org.bson.codecs.Codec[String] = new StringCodec()
  private val allProviders = Seq(
    CodecProviderMacro.createCodecProviderEncodeNone[Address],
    CodecProviderMacro.createCodecProviderEncodeNone[Person],
    CodecProviderMacro.createCodecProviderEncodeNone[Task],
    EnumValueCodecProvider.forStringEnum[Priority]
  )
```

### 4️ Build the Combined Registry
```scala
val registry: CodecRegistry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(allProviders*),
  MongoClient.DEFAULT_CODEC_REGISTRY
)
```

### 5️ Expose the Registry as an Implicit (given)
This step is ESSENTIAL:
It makes your custom registry discoverable by macros and the MongoDB driver at runtime.
If you skip it, nested (de)serialization will fail!

```scala
given CodecRegistry = registry  //  DO NOT FORGET THIS LINE!
```

### 6 Use Your Registry Everywhere in Your App

```scala
val client = MongoClient().withCodecRegistry(Codecs.registry)
val db = client.getDatabase("mydb").withCodecRegistry(Codecs.registry)
val people = db.getCollection[Person]("people")
```

### 7 Insert and Query Data with Confidence

```scala
val person = Person(new ObjectId(), "Alice", 30, Some(Address("Main St", "City", 12345)))
people.insertOne(person).toFuture() // No more "No codec found" errors!
```

---

### ⚠️ Troubleshooting / FAQ
**Q:** I get `No codec found for type: my.model.Address` or a similar error.

**A:** Check that you:
- Listed every type as a provider (including nested case classes/enums)
- Included those providers in your registry
- Added `given CodecRegistry = registry` at the end of your Codecs object
- Passed your custom registry everywhere (client, db, collection)

---

### ✅ Summary Checklist
- Add codec providers for every type (including nested)
- Build the combined registry
- Expose as `given CodecRegistry = registry`
- Use your registry everywhere

By following these steps, your codecs will “just work” for any Scala 3 case class, enum, or nested structure!


## Contributing

Contributions are welcome! Please fork the repository and submit a pull request. For major changes, open an issue first to discuss your ideas.

---

## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

---

**Main Developer:** Mohamed Ali Bannour  
Email: [med.ali.bennour@gmail.com](mailto:med.ali.bennour@gmail.com)

---