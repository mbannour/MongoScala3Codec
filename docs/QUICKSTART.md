# 5-Minute Quickstart Guide

Get started with MongoScala3Codec in just a few minutes. This guide will have you persisting and retrieving Scala 3 case classes to MongoDB with automatic codec generation.

## Why Use This Library?

**MongoScala3Codec enables native MongoDB usage in Scala 3.** The official `mongo-scala-driver` only supports Scala 2 (2.11, 2.12, 2.13) because it uses Scala 2 macros. This library provides:
- ✅ **Scala 3 native** - Uses modern Scala 3 macros
- ✅ **Zero boilerplate** - One line registers any case class
- ✅ **Compile-time safe** - Catch errors at compile time, not production
- ✅ **BSON native** - Full support for ObjectId, Binary, Decimal128, etc.
- ✅ **Best sealed trait support** - 3 discriminator strategies

## Prerequisites

- Scala 3.3+ project
- MongoDB instance (local or cloud)
- SBT or Mill build tool

## Step 1: Add Dependency (30 seconds)

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec" % "0.0.7"
libraryDependencies += ("org.mongodb.scala" %% "mongo-scala-driver" % "5.2.1").cross(CrossVersion.for3Use2_13)
```

## Step 2: Define Your Domain Models (1 minute)

```scala
import org.bson.types.ObjectId

// Simple case class
case class User(
  _id: ObjectId,
  name: String,
  email: String,
  age: Int
)

// Nested case classes
case class Address(street: String, city: String, zipCode: Int)

case class Customer(
  _id: ObjectId,
  name: String,
  address: Address,
  tags: List[String]
)

```

## Step 3: Register Codecs (1 minute)

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.mongodb.scala.MongoClient
import org.bson.codecs.configuration.CodecRegistry

// Build codec registry - registers codecs for all your types
// Using the new convenience methods for cleaner, more efficient registration
val codecRegistry: CodecRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .ignoreNone                  // Omit None fields from BSON (vs encoding as null)
  .registerAll[(User, Address, Customer)]  // Batch register multiple types (faster!)
  .build

given CodecRegistry = codecRegistry
```

**New in 0.0.7:** The `registerAll[(Type1, Type2, ...)]` method is more efficient than chaining multiple `register[T]` calls, and `ignoreNone` is a cleaner alternative to `given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)`.


## Step 4: Connect to MongoDB (30 seconds)

```scala
import org.mongodb.scala._

val mongoClient = MongoClient("mongodb://localhost:27017")
val database = mongoClient.getDatabase("myapp").withCodecRegistry(codecRegistry)

// Type-safe collections!
val userCollection: MongoCollection[User] = database.getCollection[User]("users")
val customerCollection: MongoCollection[Customer] = database.getCollection[Customer]("customers")
```

## Step 5: Insert and Query Data (2 minutes)

```scala
import org.mongodb.scala.model.Filters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

// INSERT: Create and save a user
val newUser = User(
  _id = new ObjectId(),
  name = "Alice Johnson",
  email = "alice@example.com",
  age = 28
)

val insertResult = Await.result(
  userCollection.insertOne(newUser).toFuture(),
  10.seconds
)
println(s"Inserted user with ID: ${newUser._id}")

// QUERY: Find the user by email
val foundUser = Await.result(
  userCollection.find(Filters.eq("email", "alice@example.com")).first().toFuture(),
  10.seconds
)
println(s"Found user: $foundUser")

// INSERT: Nested case class with collections
val customer = Customer(
  _id = new ObjectId(),
  name = "Bob Smith",
  address = Address("123 Main St", "Springfield", 12345),
  tags = List("premium", "verified", "active")
)

Await.result(customerCollection.insertOne(customer).toFuture(), 10.seconds)

// QUERY: Find customers by city
val springfieldCustomers = Await.result(
  customerCollection.find(Filters.eq("address.city", "Springfield")).toFuture(),
  10.seconds
)
println(s"Customers in Springfield: ${springfieldCustomers.size}")

```

## Complete Working Example

Here's a complete, runnable example you can paste into a Scala worksheet or main method:

```scala
import org.bson.types.ObjectId
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig, NoneHandling}
import org.mongodb.scala._
import org.mongodb.scala.model.Filters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

// 1. Define models
case class BlogPost(
  _id: ObjectId,
  title: String,
  content: String,
  author: String,
  tags: List[String],
  published: Boolean
)

@main def quickstartExample(): Unit =
  // 2. Setup codec registry
  given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)
  
  val codecRegistry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .register[BlogPost]
    .build
  
  // 3. Connect to MongoDB
  val mongoClient = MongoClient("mongodb://localhost:27017")
  val database = mongoClient.getDatabase("blog_db").withCodecRegistry(codecRegistry)
  val posts = database.getCollection[BlogPost]("posts")
  
  // 4. Create and insert a blog post
  val post = BlogPost(
    _id = new ObjectId(),
    title = "Getting Started with MongoScala3Codec",
    content = "This library makes MongoDB operations type-safe and effortless...",
    author = "Alice",
    tags = List("scala", "mongodb", "tutorial"),
    published = true
  )
  
  Await.result(posts.insertOne(post).toFuture(), 10.seconds)
  println(s"✅ Inserted post: ${post.title}")
  
  // 5. Query the post
  val found = Await.result(
    posts.find(Filters.eq("author", "Alice")).first().toFuture(),
    10.seconds
  )
  println(s"✅ Found post: ${found.title}")
  
  // 6. Update the post
  import org.mongodb.scala.model.Updates
  Await.result(
    posts.updateOne(
      Filters.eq("_id", post._id),
      Updates.push("tags", "beginner-friendly")
    ).toFuture(),
    10.seconds
  )
  println("✅ Updated post tags")
  
  // Cleanup
  mongoClient.close()
```

## What Just Happened?

🎉 **Congratulations!** You've just:

1. ✅ Defined type-safe domain models
2. ✅ Automatically generated BSON codecs at compile-time
3. ✅ Persisted complex nested structures to MongoDB
4. ✅ Queried data with type safety
5. ✅ Handled ADTs (sealed traits) with automatic discriminators

## Next Steps

- 📖 Read the [Feature Overview](FEATURES.md) to learn about all capabilities
- 🎯 Explore [Sealed Traits Guide](SEALED_TRAITS.md) for polymorphic type support
- 🔧 Check out [How It Works](HOW_IT_WORKS.md) to understand the internals
- ❓ Visit the [FAQ](FAQ.md) for common questions and troubleshooting

## Common Issues

### Compilation Error: "Cannot find codec for type X"

**Solution:** Make sure you register the codec before using it:

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[X]  // ← Don't forget this!
  .build
```

### None values appearing as null in MongoDB

**Solution:** Use `NoneHandling.Ignore` to omit None fields:

```scala
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)
```

### Type mismatch with MongoDB Scala Driver

**Solution:** Ensure you're using the correct cross-version:

```scala
"org.mongodb.scala" %% "mongo-scala-driver" % "5.2.1" cross CrossVersion.for3Use2_13
```

---

**Need help?** Open an issue on [GitHub](https://github.com/mbannour/MongoScala3Codec/issues)

