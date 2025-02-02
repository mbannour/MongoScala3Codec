

# MongoScala3Codec: A Macro-Based BSON Codec Generator for Scala

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.1-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)

`MongoScala3Codec` is a lightweight and efficient library that simplifies BSON serialization and deserialization for Scala case classes. Powered by Scala 3 macros, it generates BSON codecs at compile time, ensuring type safety and high performance. This library is an essential tool for seamless integration with MongoDB in Scala applications.

## Compatibility

- **Scala 3**: This library is compatible only with Scala 3, leveraging its advanced macro capabilities for compile-time codec generation.
## Installation

To include MongoScala3Codec in your Scala project, add the following dependency:

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.1-M5"
```

## Features

- **Macro-based Codec Generation**: Automatically generate BSON codecs for case classes at compile-time.
- **Support for Optional Fields**: Handle `Option` types with customizable behavior (`encodeNone` or `ignoreNone`).
- **Unified Codec Management**: Consolidates and manages codecs with `CodecRegistryManager`
- **Nested Case Class Support**: Serialize and deserialize deeply nested case class structures.
- **Seamless Integration**: Compatible with MongoDB Scala Driver.


## Getting Started

Below is a complete example that demonstrates how to:

- Define your case classes.
- Generate BSON codecs using the macro-based codec generator.
- Set up a consolidated `CodecRegistry` for MongoDB.
- Insert and retrieve a document from a MongoDB collection.

### Example Code

```scala
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase, *}
import io.github.mbannour.mongo.codecs.{CaseClassCodec, CodecProviderMacro}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.types.ObjectId
import org.mongodb.scala.bson.annotations.BsonProperty
import org.mongodb.scala.model.Filters

// Define your case classes
case class Address(street: String, city: String, zipCode: Int)

case class Person(
                   _id: ObjectId,
                   @BsonProperty("n") name: String,
                   middleName: Option[String],
                   age: Int,
                   height: Double,
                   married: Boolean,
                   address: Option[Address],
                   nicknames: Seq[String]
                 )

@main def hello(): Unit = {

  // Get the default MongoDB codec registry
  val baseRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY

  // Create a codec provider for Address using macro-based generation
  val addressProvider: CodecProvider =
    CodecProviderMacro.createCodecProvider[Address](encodeNone = true, baseRegistry)

  // Create a codec registry that includes the Address codec
  val addressRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(addressProvider),
      baseRegistry
    )

  // Create a codec provider for Person that includes the Address registry
  val personProvider: CodecProvider =
    CodecProviderMacro.createCodecProvider[Person](encodeNone = true, addressRegistry)

  // Combine the generated codecs with the default registry
  val combinedRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(personProvider),
      baseRegistry
    )

  // Create a MongoDB database instance with the combined codec registry
  val database: MongoDatabase = MongoClient()
    .getDatabase("test_db")
    .withCodecRegistry(combinedRegistry)

  // Get the collection for Person documents
  val collection: MongoCollection[Person] = database.getCollection("people")

  // Create a Person instance
  val person = Person(
    _id = new ObjectId(),
    name = "Alice",
    middleName = Some("Marie"),
    age = 30,
    height = 5.6,
    married = true,
    address = Some(Address("123 Main St", "Wonderland", 12345)),
    nicknames = Seq("Ally", "Lissie")
  )

  // Insert the Person into the collection
  collection.insertOne(person).toFuture().foreach { _ =>
    println("Person inserted successfully!")
  }

  // Retrieve the Person by _id and print the result
  collection.find(Filters.equal("_id", person._id)).first().toFuture().foreach { retrievedPerson =>
    println(s"Retrieved Person: $retrievedPerson")
  }
}
```
## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

Main Developer: Mohamed Ali Bannour  
Email: med.ali.bennour@gmail.com
