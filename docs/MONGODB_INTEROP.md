# MongoDB Driver Interop and Configuration Guide

Complete guide to using MongoScala3Codec with MongoDB Scala and Java drivers.

## Table of Contents

- [Driver Compatibility](#driver-compatibility)
- [Registry Configuration](#registry-configuration)
- [Collection Setup](#collection-setup)
- [Query Operations](#query-operations)
- [Index Operations](#index-operations)
- [Aggregation Pipeline](#aggregation-pipeline)
- [Transactions](#transactions)
- [Reactive Streams](#reactive-streams)

---

## Driver Compatibility

### Supported Versions

| Driver | Version | Status |
|--------|---------|--------|
| MongoDB Scala Driver | 4.x | ✅ Full Support |
| MongoDB Scala Driver | 5.x | ✅ Full Support |
| MongoDB Java Driver | 4.x | ✅ Via Scala Driver |
| MongoDB Java Driver | 5.x | ✅ Via Scala Driver |

### Dependencies

```scala
// build.sbt
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.7",
  "org.mongodb.scala" %% "mongo-scala-driver" % "5.2.1" cross CrossVersion.for3Use2_13
)
```

---

## Registry Configuration

### Basic Configuration

```scala
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig, NoneHandling}
import org.mongodb.scala.MongoClient
import org.bson.codecs.configuration.CodecRegistry

// Define your models
case class User(_id: ObjectId, name: String, email: Option[String])
case class Address(street: String, city: String, zipCode: Int)

// Configure codec behavior
given CodecConfig = CodecConfig(
  noneHandling = NoneHandling.Ignore,
  discriminatorField = "_type"
)

// Build registry
val codecRegistry: CodecRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[Address]
  .register[User]
  .build
```

### Combining Multiple Registries

```scala
import org.bson.codecs.configuration.CodecRegistries

// Custom codecs
val customRegistry = CodecRegistries.fromCodecs(
  new MyCustomCodec(),
  new AnotherCustomCodec()
)

// MongoScala3Codec generated codecs
val generatedRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .register[Product]
  .build

// Combine registries (order matters - first match wins)
val combinedRegistry = CodecRegistries.fromRegistries(
  generatedRegistry,    // Check generated codecs first
  customRegistry,       // Then custom codecs
  MongoClient.DEFAULT_CODEC_REGISTRY  // Finally defaults
)
```

### Registry with Providers

```scala
import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

enum Status:
  case Active, Inactive, Pending

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider[Status]())
  .register[User]
  .build
```

---

## Collection Setup

### Type-Safe Collections

```scala
import org.mongodb.scala._

val mongoClient = MongoClient("mongodb://localhost:27017")

// Apply registry at database level
val database = mongoClient
  .getDatabase("myapp")
  .withCodecRegistry(codecRegistry)

// Type-safe collection - automatic codec lookup
val userCollection: MongoCollection[User] = 
  database.getCollection[User]("users")

val productCollection: MongoCollection[Product] = 
  database.getCollection[Product]("products")
```

### Collection-Specific Registry

```scala
// Apply registry only to specific collection
val usersWithSpecialCodec = database
  .getCollection[User]("users")
  .withCodecRegistry(specialUserRegistry)

// Other collections use database registry
val products = database.getCollection[Product]("products")
```

### Generic Document Collections

```scala
import org.mongodb.scala.bson.Document

// For dynamic/untyped queries
val documentCollection: MongoCollection[Document] = 
  database.getCollection("users")

// Query as documents, then manually parse
val docs = documentCollection.find().toFuture()
```

---

## Query Operations

### Basic CRUD

```scala
import org.mongodb.scala.model.Filters
import scala.concurrent.ExecutionContext.Implicits.global

// INSERT
val newUser = User(new ObjectId(), "Alice", Some("alice@example.com"))
val insertResult = userCollection.insertOne(newUser).toFuture()

// INSERT MANY
val users = Seq(
  User(new ObjectId(), "Bob", Some("bob@example.com")),
  User(new ObjectId(), "Carol", None)
)
val insertManyResult = userCollection.insertMany(users).toFuture()

// FIND ONE
val alice = userCollection
  .find(Filters.eq("name", "Alice"))
  .first()
  .toFuture()

// FIND ALL
val allUsers = userCollection
  .find()
  .toFuture()

// FIND WITH FILTER
val activeUsers = userCollection
  .find(Filters.eq("status", "active"))
  .toFuture()

// UPDATE
import org.mongodb.scala.model.Updates
val updateResult = userCollection
  .updateOne(
    Filters.eq("name", "Alice"),
    Updates.set("email", "alice.new@example.com")
  )
  .toFuture()

// DELETE
val deleteResult = userCollection
  .deleteOne(Filters.eq("name", "Alice"))
  .toFuture()
```

### Complex Queries

```scala
import org.mongodb.scala.model.{Filters, Sorts, Projections}

// Compound filters
val complexQuery = userCollection
  .find(
    Filters.and(
      Filters.gte("age", 18),
      Filters.lte("age", 65),
      Filters.eq("active", true)
    )
  )
  .sort(Sorts.descending("createdAt"))
  .limit(10)
  .toFuture()

// Text search
val textSearch = userCollection
  .find(Filters.text("scala"))
  .toFuture()

// Regex search
val regexSearch = userCollection
  .find(Filters.regex("name", "^A.*"))
  .toFuture()

// In query
val inQuery = userCollection
  .find(Filters.in("status", "active", "pending"))
  .toFuture()
```

### Nested Field Queries

```scala
case class Address(street: String, city: String, zipCode: Int)
case class Person(_id: ObjectId, name: String, address: Address)

val personCollection = database.getCollection[Person]("people")

// Query nested fields using dot notation
val springfieldResidents = personCollection
  .find(Filters.eq("address.city", "Springfield"))
  .toFuture()

// Query nested fields with type-safe helper
import io.github.mbannour.fields.MongoFieldResolver

object PersonFields extends MongoFieldResolver[Person]

val typeSafeQuery = personCollection
  .find(Filters.eq(PersonFields.address.city, "Springfield"))
  .toFuture()
```

---

## Index Operations

### Creating Indexes

```scala
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.IndexOptions

// Single field index
val singleIndex = userCollection
  .createIndex(Indexes.ascending("email"))
  .toFuture()

// Compound index
val compoundIndex = userCollection
  .createIndex(
    Indexes.compoundIndex(
      Indexes.ascending("name"),
      Indexes.descending("createdAt")
    )
  )
  .toFuture()

// Unique index
val uniqueEmailIndex = userCollection
  .createIndex(
    Indexes.ascending("email"),
    IndexOptions().unique(true)
  )
  .toFuture()

// Text index
val textIndex = userCollection
  .createIndex(Indexes.text("name"))
  .toFuture()

// TTL index (auto-expire documents)
val ttlIndex = userCollection
  .createIndex(
    Indexes.ascending("createdAt"),
    IndexOptions().expireAfter(30, java.util.concurrent.TimeUnit.DAYS)
  )
  .toFuture()
```

### Listing Indexes

```scala
val indexes = userCollection
  .listIndexes()
  .toFuture()
  .map(_.foreach(println))
```

---

## Aggregation Pipeline

### Basic Aggregation

```scala
import org.mongodb.scala.model.Aggregates
import org.mongodb.scala.model.Accumulators
import org.mongodb.scala.bson.Document

// Group by and count
val pipeline = Seq(
  Aggregates.group("$status", Accumulators.sum("count", 1))
)

val results = userCollection
  .aggregate[Document](pipeline)
  .toFuture()

// Match and project
val matchProject = Seq(
  Aggregates.`match`(Filters.gte("age", 18)),
  Aggregates.project(Projections.include("name", "email"))
)

val filtered = userCollection
  .aggregate[Document](matchProject)
  .toFuture()
```

### Type-Safe Aggregation Results

```scala
case class UserSummary(name: String, email: Option[String])

// Register result type
val summaryRegistry = RegistryBuilder
  .from(codecRegistry)
  .register[UserSummary]
  .build

val summaryCollection = database
  .getCollection[User]("users")
  .withCodecRegistry(summaryRegistry)

// Aggregate with typed results
val summaries = summaryCollection
  .aggregate[UserSummary](matchProject)
  .toFuture()
```

---

## Transactions

### Multi-Document Transactions

```scala
import org.mongodb.scala.ClientSession

// Start session
val session: ClientSession = mongoClient.startSession().toFuture().await

try
  // Start transaction
  session.startTransaction()
  
  // Perform operations within transaction
  val user = User(new ObjectId(), "Alice", Some("alice@example.com"))
  userCollection.insertOne(session, user).toFuture().await
  
  val order = Order(new ObjectId(), user._id, List("item1", "item2"))
  orderCollection.insertOne(session, order).toFuture().await
  
  // Commit transaction
  session.commitTransaction().toFuture().await
  println("Transaction committed successfully")
  
catch
  case e: Exception =>
    // Abort transaction on error
    session.abortTransaction().toFuture().await
    println(s"Transaction aborted: ${e.getMessage}")
    throw e
finally
  session.close()
```

### Transaction with Error Handling

```scala
import scala.util.{Try, Success, Failure}

def executeTransaction[T](
  mongoClient: MongoClient
)(operation: ClientSession => Future[T]): Future[T] =
  val sessionFuture = mongoClient.startSession().toFuture()
  
  sessionFuture.flatMap { session =>
    session.startTransaction()
    
    operation(session).transformWith {
      case Success(result) =>
        session.commitTransaction().toFuture().map(_ => result)
      case Failure(error) =>
        session.abortTransaction().toFuture().flatMap(_ => Future.failed(error))
    }.andThen { case _ => session.close() }
  }

// Usage
val result = executeTransaction(mongoClient) { session =>
  for
    _ <- userCollection.insertOne(session, user).toFuture()
    _ <- orderCollection.insertOne(session, order).toFuture()
  yield "Success"
}
```

---

## Reactive Streams

### Observable to Future

```scala
import org.mongodb.scala._
import scala.concurrent.Future

// Built-in conversion
val future: Future[Seq[User]] = userCollection
  .find()
  .toFuture()

// First result
val firstUser: Future[User] = userCollection
  .find()
  .first()
  .toFuture()

// Head result (fails if empty)
val headUser: Future[User] = userCollection
  .find()
  .head()
```

### Working with Observables

```scala
import org.mongodb.scala.Observer

// Custom observer
val customObserver = new Observer[User] {
  override def onNext(user: User): Unit = println(s"Got user: ${user.name}")
  override def onError(error: Throwable): Unit = println(s"Error: $error")
  override def onComplete(): Unit = println("Done!")
}

userCollection.find().subscribe(customObserver)

// Foreach shorthand
userCollection.find().foreach(user => println(user.name))
```

### Integration with Akka Streams

```scala
import akka.stream.scaladsl._
import org.mongodb.scala._

// Convert Observable to Source
val source: Source[User, NotUsed] = 
  Source.fromPublisher(userCollection.find())

// Process with Akka Streams
source
  .filter(_.email.isDefined)
  .map(user => s"${user.name}: ${user.email.get}")
  .runWith(Sink.foreach(println))
```

### Integration with FS2 (Cats Effect)

```scala
import fs2._
import cats.effect._
import org.mongodb.scala._

def streamUsers(collection: MongoCollection[User]): Stream[IO, User] =
  Stream.eval(IO.fromFuture(IO(collection.find().toFuture())))
    .flatMap(users => Stream.emits(users))

// Usage
streamUsers(userCollection)
  .filter(_.email.isDefined)
  .compile
  .toList
  .unsafeRunSync()
```

---

## Configuration Best Practices

### Application-Wide Configuration

```scala
// Application.scala
object MongoConfig:
  given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)
  
  lazy val codecRegistry: CodecRegistry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .withConfig(summon[CodecConfig])
    .register[User]
    .register[Product]
    .register[Order]
    // ... register all domain types
    .build
  
  lazy val mongoClient: MongoClient = 
    MongoClient(sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017"))
  
  lazy val database: MongoDatabase = 
    mongoClient.getDatabase("myapp").withCodecRegistry(codecRegistry)

// Usage in services
class UserService:
  private val collection = MongoConfig.database.getCollection[User]("users")
  
  def findByName(name: String): Future[Option[User]] =
    collection.find(Filters.eq("name", name)).first().toFutureOption()
```

### Environment-Specific Configuration

```scala
trait MongoEnvironment:
  def codecRegistry: CodecRegistry
  def database: MongoDatabase

object DevelopmentMongo extends MongoEnvironment:
  given CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)
  
  val codecRegistry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .withConfig(summon[CodecConfig])
    .register[User]
    .build
  
  val database = MongoClient("mongodb://localhost:27017")
    .getDatabase("myapp_dev")
    .withCodecRegistry(codecRegistry)

object ProductionMongo extends MongoEnvironment:
  given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)
  
  val codecRegistry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .withConfig(summon[CodecConfig])
    .register[User]
    .build
  
  val database = MongoClient(sys.env("MONGO_URI"))
    .getDatabase("myapp_prod")
    .withCodecRegistry(codecRegistry)

// Select environment
val mongo: MongoEnvironment = 
  if sys.env.get("ENV").contains("production") then ProductionMongo
  else DevelopmentMongo
```

---

## Next Steps

- 📖 [BSON Type Mapping](BSON_TYPE_MAPPING.md) - Type compatibility
- 🧪 [Testing Guide](TESTING.md) - Testing with MongoDB

