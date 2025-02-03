

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

enum PersonState:
  case Created, Approved,  Sleep, InProgress, Closed

case class Person(
                   _id: ObjectId,
                   state: PersonState,
                   name: String,
                   middleName: Option[String],
                   age: Int,
                   height: Double,
                   married: Boolean,
                   address: Option[Address],
                   nicknames: Seq[String]
                 )

// Example case class for Event.
case class Event(_id: ObjectId, description: String, eventTime: ZonedDateTime)

object Main extends App {

  // Base registry from the default MongoDB Scala Driver.
  val baseRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY

  // Create a codec provider for Address.
  val addressProvider: CodecProvider =
    CodecProviderMacro.createCodecProvider[Address](encodeNone = true, baseRegistry)

  // Create a codec registry for Person that includes Address and a custom codec for lead execution status.
  // Make sure that `PersonStateBsonCodec` is defined in your project.
  val personRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(addressProvider),
      CodecRegistries.fromCodecs(PersonStateBsonCodec), 
      baseRegistry
    )

  // Create a codec provider for Person.
  val personProvider: CodecProvider =
    CodecProviderMacro.createCodecProvider[Person](encodeNone = true, personRegistry)

  // Build a codec registry for Event including the custom ZonedDateTimeCodec.
  val eventRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      baseRegistry,
      CodecRegistries.fromCodecs(new ZonedDateTimeCodec())
    )

  // Create a codec provider for Event.
  val eventProvider: CodecProvider =
    CodecProviderMacro.createCodecProvider[Event](encodeNone = false, eventRegistry)

  // Combine the codec registries for Person and Event.
  val combinedRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(personProvider),
      CodecRegistries.fromProviders(eventProvider),
      baseRegistry
    )

  // --- Database and Collection Setup ---

  // Connect to the MongoDB database with the combined codec registry.
  val database: MongoDatabase = MongoClient()
    .getDatabase("test_db")
    .withCodecRegistry(combinedRegistry)

  // Obtain collections for Person and Event.
  val collection: MongoCollection[Person] = database.getCollection("people")
  val eventCollection: MongoCollection[Event] = database.getCollection("event")

  // --- Sample Documents and Operations ---

  // Create a sample Person document.
  val person = Person(
    _id = new ObjectId(),
    state = Created,
    name = "Alice",
    middleName = None,
    age = 30,
    height = 5.6,
    married = true,
    address = None,
    nicknames = Seq("Ally", "Lissie")
  )

  // Insert a sample Event document.
  eventCollection.insertOne(Event(new ObjectId(), "today Event", ZonedDateTime.now())).toFuture()

  // Insert the Person document.
  collection.insertOne(person).toFuture()

  // Retrieve and print all Person documents.
  collection.find().toFuture().foreach(println)

}
```
## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

Main Developer: Mohamed Ali Bannour  
Email: med.ali.bennour@gmail.com
