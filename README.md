# MongoScala3Codec: A Macro-Based BSON Codec Generator for Scala

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.1-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)

**MongoScala3Codec** is a lightweight and efficient library that simplifies BSON serialization and deserialization for Scala case classes. Leveraging Scala 3’s powerful macro system and the new `given`/`using` syntax, it automatically generates BSON codecs at compile time.

This approach ensures:

* **Strong Type Safety** – Compile-time validation of BSON serialization.
* **High Performance** – Optimized code generation for efficient BSON handling.
* **Minimal Boilerplate** – Eliminates manual codec definitions.
# MongoFieldResolver: Extract MongoDB Field Paths with Compile-Time Safety

Included in the library is the `MongoFieldResolver` (formerly `FieldPathMapper`), a utility that enables compile-time safe extraction of MongoDB field names, including support for nested structures.

---

##  Why Use `MongoFieldResolver`?

* **Safe**: Detects typos at compile time by relying on actual field names from your case classes.
* **Smart**: Understands deeply nested structures and custom field renaming using annotations (e.g., `@BsonProperty`).
* **Fast**: Leveraging Scala 3 inline macros and a built-in cache for high performance.
* **Flexible**: Supports both static and dynamic access patterns:

  * Static/type-safe: `mongoField[Person]("address.city")`
  * Dynamic/dictionary-style: `MongoFieldMapper.asMap[Person]("address.city")`

---
##  Recommended Pattern

Use `MongoFieldMapper.asMap[T]` to generate a cached field map once and safely reuse it. This keeps your code clean and avoids hardcoded MongoDB field strings:

```scala
val dbField = PersonFields("address.city")
```

If you pass a field that doesn't exist, an exception is thrown with a helpful message — ensuring correctness during

---

## Documentation

Visit the GitHub Wiki for complete guides, tutorials, and references:

* [Getting Started](https://github.com/mbannour/MongoScala3Codec/wiki/Getting%E2%80%90started)
* [Architecture Overview](https://github.com/mbannour/MongoScala3Codec/wiki/Architecture%E2%80%90Overview)
* [API Reference](https://github.com/mbannour/MongoScala3Codec/wiki/API‐Reference)
* [How-To Guides](https://github.com/mbannour/MongoScala3Codec/wiki/How‐To-Guides)
* [Design Decisions](https://github.com/mbannour/MongoScala3Codec/wiki/Design‐Decisions)

---

## Compatibility

* **Scala 3**: This library is compatible only with Scala 3, leveraging its advanced macro capabilities for compile-time codec generation.

## Installation

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.4"
```

## Features

* **Automatic Codec Generation**
  Generate a BSON codec for any Scala case class with minimal boilerplate.

* **Flexible `None` Handling**
  Choose whether to encode `None` as BSON `null` or omit the field entirely.

* **Scala Enumeration Support**
  Use `ScalaEnumerationCodecProvider` to handle `Enumeration.Value` fields smoothly.

* **Support for @BsonProperty Annotations**
  Rename fields easily when mapping to MongoDB.

* **Compile-Time Safety**
  The macros validate types at compile time, preventing misconfiguration.

* **Scala 3 Macros**
  Leverage `inline` and macro features for safe, boilerplate-free code generation.

## Quick Example

```scala
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.ObjectId
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import io.github.mbannour.mongo.codecs.CodecProviderMacro

case class Address(street: String, city: String, zip: Int)
case class Person(
  _id: ObjectId,
  @BsonProperty("n") name: String,
  age: Int,
  address: Option[Address]
)

object Person {
  val registry: CodecRegistry = CodecRegistries.fromRegistries(
    CodecRegistries.fromProviders(
      CodecProviderMacro.createCodecProviderEncodeNone[Address],
      CodecProviderMacro.createCodecProviderEncodeNone[Person]
    ),
    MongoClient.DEFAULT_CODEC_REGISTRY
  )
}

val client = MongoClient()
val db = client.getDatabase("test_db").withCodecRegistry(Person.registry)
val collection = db.getCollection[Person]("people")

val person = Person(ObjectId.get(), "Alice", 30, Some(Address("Main St", "City", 12345)))
collection.insertOne(person).toFuture()
```

## Contributing

Contributions are welcome! If you'd like to contribute, please fork the repository and submit a pull request. For major changes, please open an issue first to discuss what you would like to change.
## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

Main Developer: Mohamed Ali Bannour
Email: [med.ali.bennour@gmail.com](mailto:med.ali.bennour@gmail.com)
