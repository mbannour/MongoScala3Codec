

# MongoScala3Codec: A Macro-Based BSON Codec Generator for Scala

![mongoScala3Codec version](https://img.shields.io/badge/mongoScala3Codecs-0.0.1-brightgreen)
![mongoScala3Codec compatibility](https://img.shields.io/badge/Scala-3.0%2B-blue)

**MongoScala3Codec** is a lightweight and efficient library that simplifies BSON serialization and deserialization for Scala case classes. Leveraging Scala 3’s powerful macro system and the new `given`/`using` syntax, it automatically generates BSON codecs at compile time.

This approach ensures:
- **Strong Type Safety** – Compile-time validation of BSON serialization.
- **High Performance** – Optimized code generation for efficient BSON handling.
- **Minimal Boilerplate** – Eliminates manual codec definitions.

MongoScala3Codec is an essential tool for seamless integration with MongoDB in modern Scala applications.
## Compatibility

- **Scala 3**: This library is compatible only with Scala 3, leveraging its advanced macro capabilities for compile-time codec generation.
## Installation

To include MongoScala3Codec in your Scala project, add the following dependency:

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.1-M6"
```

## Features

- **Automatic Codec Generation**  
  Generate a BSON codec for any Scala case class with minimal boilerplate.

- **Flexible `None` Handling**
    - **Encode `None`** → The field will be defined in the MongoDB document with a `null` value.
    - **Ignore `None`** → The field will not be included in the MongoDB document at all.

- **Compile-Time Safety**  
  The macros ensure that only valid case classes are used, providing compile-time errors if a non-case class is passed.

- **Scala 3 Macros**  
  Leverage the new `inline` and macro features of Scala 3 for concise and safe code generation.

## How It Works

The macros generate a `CodecProvider` through the following steps:

### 1. Type Validation
At **compile time**, the macro ensures that the type `T` is a **case class**. If not, it aborts with an error to prevent incorrect usage.

### 2. Codec Generation
The macro calls the `generateCodec[T]` function from `CaseClassCodecGenerator`, which **constructs a BSON codec** based on whether you want to **encode or ignore `None` values**.

### 3. Provider Creation
It wraps the generated codec in a `CodecProvider` instance, which:
- Checks at **runtime** if a requested class is assignable from `T`.
- **Returns the appropriate codec** for BSON serialization and deserialization.

### Example Code

```scala

import io.github.mbannour.mongo.codecs.CodecProviderMacro
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.{BsonReader, BsonWriter}
import org.bson.types.ObjectId
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}
import scala.concurrent.Await
import scala.concurrent.duration._


final case class EmployeeId(value: ObjectId) extends AnyVal

object EmployeeIdCodec extends Codec[EmployeeId] {
  override def encode(writer: BsonWriter, value: EmployeeId, encoderContext: EncoderContext): Unit =
    writer.writeObjectId(value.value)

  override def decode(reader: BsonReader, decoderContext: DecoderContext): EmployeeId =
    EmployeeId(reader.readObjectId())

  override def getEncoderClass: Class[EmployeeId] = classOf[EmployeeId]
}

case class Address(street: String, city: String, zipCode: Int, employeeId: EmployeeId)


object DefaultCodecRegistries {

  private val addressProvider = CodecProviderMacro.createCodecProviderEncodeNone[Address]

  // Compose all codec providers into a single CodecRegistry
  val defaultRegistry: CodecRegistry = CodecRegistries.fromRegistries(
    CodecRegistries.fromCodecs(EmployeeIdCodecProvider),
    CodecRegistries.fromProviders(EmployeeIdCodecProvider, addressProvider),
    MongoClient.DEFAULT_CODEC_REGISTRY
  )


  given CodecRegistry = defaultRegistry
}


object MyApp extends App {
  
  import DefaultCodecRegistries.given
  
  val database: MongoDatabase = MongoClient()
    .getDatabase("example_db")
    .withCodecRegistry(DefaultCodecRegistries.defaultRegistry)
  
  val collection: MongoCollection[Address] = database.getCollection("addresses")
  
  val address = Address(
    street = "456 Oak St",
    city = "Metropolis",
    zipCode = 98765,
    employeeId = EmployeeId(new ObjectId())
  )
  
  val insertFuture = collection.insertOne(address).toFuture()
  Await.result(insertFuture, 10.seconds)
  println(s"Inserted address: $address")
  
  val findFuture = collection.find().first().toFuture()
  val retrievedAddress = Await.result(findFuture, 10.seconds)
  println(s"Retrieved address: $retrievedAddress")
  
}

```

## Contributing

Contributions are welcome! If you'd like to contribute, please fork the repository and submit a pull request. For major changes, please open an issue first to discuss what you would like to change.

## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

Main Developer: Mohamed Ali Bannour  
Email: med.ali.bennour@gmail.com
