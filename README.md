

# MongoScala3Codecs: A Macro-Based BSON Codec Generator for Scala

![mongoScala3Codecs version](https://img.shields.io/badge/mongoScala3Codecs-0.0.1-brightgreen)
![mongoScala3Codecs compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)

`MongoScala3Codecs` is a lightweight and efficient library that simplifies BSON serialization and deserialization for Scala case classes. Powered by Scala 3 macros, it generates BSON codecs at compile time, ensuring type safety and high performance. This library is an essential tool for seamless integration with MongoDB in Scala applications.

## Compatibility

- **Scala 3**: This library is compatible only with Scala 3, leveraging its advanced macro capabilities for compile-time codec generation.
## Installation

To include MongoScala3Codecs in your Scala project, add the following dependency:

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codecs" % "0.0.1-M4"
```

## Features

- **Macro-based Codec Generation**: Automatically generate BSON codecs for case classes at compile-time.
- **Support for Optional Fields**: Handle `Option` types with customizable behavior (`encodeNone` or `ignoreNone`).
- **Unified Codec Management**: Consolidates and manages codecs with `CodecRegistryManager`
- **Nested Case Class Support**: Serialize and deserialize deeply nested case class structures.
- **Seamless Integration**: Compatible with MongoDB Scala Driver.


## Getting Started

### Importing the Library

```scala
import io.github.mbannour.mongo.codecs.CaseClassCodec
```

### Generating Codecs

Use the provided methods to generate codecs for your case classes.

#### Example Case Class

```scala
case class Person(name: String, age: Option[Int], address: Option[String])
```

#### Generate Codec Including `None` Values

```scala
val codecWithNone = CaseClassCodec.generateCodecEncodeNone[Person]
```

#### Generate Codec Excluding  `None` Values

```scala
val codecWithoutNone = CaseClassCodec.generateCodecIgnoreNone[Person]
```

## Getting Started

### 1. Define Your Case Classes

Start by defining your case classes. For example:

```scala
import org.mongodb.scala.bson.annotations.BsonProperty
import org.bson.types.ObjectId

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
```

### 2. Register Codecs

Use `CaseClassCodec` to generate codecs for your case classes and register them with the `CodecRegistryManager`:

```scala
import io.github.mbannour.bson.macros.CaseClassCodecGenerator
import io.github.mbannour.bson.macros.CodecRegistryManager
import org.bson.codecs.configuration.CodecRegistries

// Generate codecs
val addressCodec = CaseClassCodec.generateCodecEncodeNone[Address]
val personCodec = CaseClassCodec.generateCodecEncodeNone[Person]

// Register codecs with CodecRegistryManager
CodecRegistryManager.addCodecRegistries(
   List(
      CodecRegistries.fromCodecs(addressCodec, personCodec),
      MongoClient.DEFAULT_CODEC_REGISTRY
   )
)
```
### 3. Get the Consolidated CodecRegistry
Retrieve the unified `CodecRegistry` from `CodecRegistryManager`:

```scala
val consolidatedRegistry = CodecRegistryManager.getCombinedCodecRegistry
```
### 4. Attach Codecs to MongoDB
Attach the consolidated `CodecRegistry` to your MongoDB database or collection:

```scala
import org.mongodb.scala.MongoClient

val mongoClient = MongoClient()
val database = mongoClient.getDatabase("example_db").withCodecRegistry(consolidatedRegistry)
val collection = database.getCollection[Person]("people")
```
### 5. Use the MongoDB Collection
You can now perform standard MongoDB operations on the collection using your custom codecs:
```scala
import org.mongodb.scala.bson.ObjectId

// Insert a document
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

collection.insertOne(person).toFuture().foreach(println)

// Find a document
collection.find().first().toFuture().foreach(retrievedPerson => println(s"Retrieved: $retrievedPerson"))
```

## Importance of `CodecRegistryManager`

`CodecRegistryManager` simplifies the management of BSON codecs by providing:
### 1. Centralized Codec Management
A single place to manage all custom and default codecs.
### 2. Dynamic Addition of Codecs
Easily add new codecs without restructuring existing logic.
```scala
CodecRegistryManager.addCodecRegistry(customCodecRegistry)
CodecRegistryManager.addCodecRegistries(List(codec1, codec2))
```
### 3. Unified CodecRegistry
Consolidates all registered codecs into one `CodecRegistry` for seamless MongoDB integration.
```scala
val consolidatedRegistry = CodecRegistryManager.getCombinedCodecRegistry
```

### 4. Compatibility with MongoDB Defaults

Ensures your custom codecs work alongside MongoDB's built-in codecs

## Example Workflow

Here’s a typical workflow using `CaseClassCodec`:

1. **Define Case Classes**  
   Represent your data model with case classes.

2. **Generate Codecs**  
   Use `CaseClassCodecGenerator` to create BSON codecs.

3. **Register Codecs**  
   Add the generated codecs to `CodecRegistryManager`.

4. **Get Unified Registry**  
   Retrieve the consolidated `CodecRegistry` from `CodecRegistryManager`.

5. **Attach Codecs**  
   Use the unified `CodecRegistry` with your MongoDB database or collection.

6. **Perform MongoDB Operations**  
   Insert, query, and retrieve data seamlessly from your MongoDB collections.


## Example Workflow

Here’s a typical workflow using `CaseClassCodec`:

1. **Define Case Classes**  
   Represent your data model with case classes.

2. **Generate Codecs**  
   Use `CaseClassCodecGenerator` to create BSON codecs for your case classes.

3. **Register Codecs**  
   Add the generated codecs to `CodecRegistryManager` for centralized management.

4. **Get Unified Registry**  
   Retrieve the consolidated `CodecRegistry` using `CodecRegistryManager.getCombinedCodecRegistry`.

5. **Attach Codecs**  
   Use the unified `CodecRegistry` with your MongoDB database or collection.

6. **Perform MongoDB Operations**  
   Seamlessly insert, query, and retrieve data from your MongoDB collections using the registered codecs.


## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

Main Developer: Mohamed Ali Bannour  
Email: med.ali.bennour@gmail.com
