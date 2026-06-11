# 5-Minute Quickstart Guide

Get started with MongoScala3Codec in just a few minutes. This guide will have you persisting and retrieving Scala 3 case classes to MongoDB with automatic codec generation.

## Why Use This Library?

**MongoScala3Codec is the type-safe ergonomics layer on top of the official driver.** Since `mongo-scala-driver` 5.7 the official driver derives BSON codecs natively on Scala 3 — this library adds the type safety and ergonomics it still lacks:
- ✅ **Zero boilerplate** — `RegistryBuilder` registers any case class in one line (vs manual `fromProviders(classOf[…])`)
- ✅ **Compile-time safe** — Catch errors at compile time, not production
- ✅ **BSON native** — Full support for ObjectId, Binary, Decimal128, etc.
- ✅ **Type-safe query DSL** — `field[T](_.f) === value` instead of `Filters.eq("f", value)`
- ✅ **Scala 3 enum support** — String/ordinal/custom field encoding (the official driver fails on `enum` fields at runtime)
- ✅ **Configurable polymorphism** — discriminator field name + SimpleName/FQN/Custom strategy

## Prerequisites

- Scala 3.3+ project
- MongoDB instance (local or cloud)
- SBT or Mill build tool

## Step 1: Add Dependencies

Add to your `build.sbt`:

```scala
// Required: core codec library + MongoDB driver
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.11",
  "org.mongodb.scala" %% "mongo-scala-driver" % "5.7.0"  // native Scala 3 since 5.7
)

// Optional but recommended: type-safe query/update DSL
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec-dsl" % "0.0.11"

// Optional: DSL assertion helpers for your test suite
libraryDependencies += "io.github.mbannour" %% "mongoscala3codec-dsl-testkit" % "0.0.11" % Test
```

| Artifact | Purpose |
|----------|---------|
| `mongoscala3codec` | Compile-time BSON codecs — always required |
| `mongoscala3codec-dsl` | `field[T]`, `Filter`, `Update`, `Sort`, `Projection` |
| `mongoscala3codec-dsl-testkit` | `DslTestKit` assertions for testing query/update logic |

## Step 2: Define Your Domain Models

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

## Step 3: Register Codecs

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


## Step 4: Connect to MongoDB

```scala
import org.mongodb.scala._

val mongoClient = MongoClient("mongodb://localhost:27017")
val database = mongoClient.getDatabase("myapp").withCodecRegistry(codecRegistry)

// Type-safe collections!
val userCollection: MongoCollection[User] = database.getCollection[User]("users")
val customerCollection: MongoCollection[Customer] = database.getCollection[Customer]("customers")
```

## Step 5: Insert and Query Data

```scala
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

Await.result(userCollection.insertOne(newUser).toFuture(), 10.seconds)
println(s"Inserted user with ID: ${newUser._id}")

// INSERT: Nested case class with collections
val customer = Customer(
  _id = new ObjectId(),
  name = "Bob Smith",
  address = Address("123 Main St", "Springfield", 12345),
  tags = List("premium", "verified", "active")
)
Await.result(customerCollection.insertOne(customer).toFuture(), 10.seconds)
```

## Step 6: Type-Safe Queries with the DSL

Instead of writing field names as strings (which break silently on refactoring), use the DSL:

```scala
import io.github.mbannour.mongo.dsl.*
```

**Before — stringly typed, no compile-time safety:**

```scala
import org.mongodb.scala.model.{Filters, Updates}

// Typo in "email" won't be caught until runtime
userCollection.find(Filters.eq("email", "alice@example.com")).first().toFuture()

// Wrong type for age won't be caught until runtime
userCollection.updateOne(Filters.eq("_id", id), Updates.inc("age", "oops")).toFuture()
```

**After — type-safe, refactor-proof:**

```scala
// field path and value type are checked at compile time
userCollection.find(field[User](_.email) === "alice@example.com").first().toFuture()

// field[User](_.age).inc("oops") would not compile — age is Int, not String
userCollection.updateOne(field[User](_._id) === id, field[User](_.age).inc(1)).toFuture()
```

**Combining filters, updates, sort, and projection:**

```scala
// Find adult customers in Springfield, sorted by name, showing only name + city
val filter = Filter.and(
  field[Customer](_.address.city) === "Springfield",
  field[User](_.age) >= 18
)
val sort = field[Customer](_.name).asc
val proj = Projection.combine(
  field[Customer](_.name).include,
  field[Customer](_.address.city).include,
  Projection.excludeId
)

val results = Await.result(
  customerCollection.find(filter).sort(sort).projection(proj).toFuture(),
  10.seconds
)

// Atomic update: tag as verified and increment a counter
val update = Update.combine(
  field[Customer](_.tags).addToSet("verified"),
  // field[Customer](_.loginCount).inc(1)   ← add loginCount to model to enable this
)
```

> **Tip:** Import `io.github.mbannour.fields.MongoPath.syntax.?` to navigate `Option` fields:
> ```scala
> import io.github.mbannour.fields.MongoPath.syntax.?
> case class Order(customer: Option[Customer])
> field[Order](_.customer.?.name) === "Alice"   // path = "customer.name"
> ```

See the **[full DSL reference](DSL.md)** for all operators and the complete API.

## Complete Working Example

Here's a complete, runnable example that uses both the codec and the DSL:

```scala
import org.bson.types.ObjectId
import io.github.mbannour.mongo.codecs.RegistryBuilder
import io.github.mbannour.mongo.dsl.*
import org.mongodb.scala.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.*

case class BlogPost(
  _id: ObjectId,
  title: String,
  author: String,
  tags: List[String],
  published: Boolean,
  views: Int
)

@main def quickstartExample(): Unit =
  val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .ignoreNone
    .register[BlogPost]
    .build

  val client = MongoClient("mongodb://localhost:27017")
  val posts  = client.getDatabase("blog_db").withCodecRegistry(registry)
                     .getCollection[BlogPost]("posts")

  // Insert
  val post = BlogPost(new ObjectId(), "Getting Started", "Alice",
                      List("scala", "mongodb"), published = true, views = 0)
  Await.result(posts.insertOne(post).toFuture(), 10.seconds)
  println(s"✅ Inserted: ${post.title}")

  // Query with DSL — compile-time safe field references
  val found = Await.result(
    posts.find(field[BlogPost](_.author) === "Alice").first().toFuture(),
    10.seconds
  )
  println(s"✅ Found: ${found.title}")

  // Atomic update — add a tag and increment view count
  Await.result(
    posts.updateOne(
      field[BlogPost](_._id) === post._id,
      Update.combine(
        field[BlogPost](_.tags).addToSet("beginner-friendly"),
        field[BlogPost](_.views).inc(1)
      )
    ).toFuture(),
    10.seconds
  )
  println("✅ Updated tags and views")

  // Sort published posts by views desc, project title + views only
  val topPosts = Await.result(
    posts.find(field[BlogPost](_.published) === true)
         .sort(field[BlogPost](_.views).desc)
         .projection(Projection.combine(
           field[BlogPost](_.title).include,
           field[BlogPost](_.views).include,
           Projection.excludeId
         ))
         .toFuture(),
    10.seconds
  )
  topPosts.foreach(p => println(s"  ${p.title} — ${p.views} views"))

  client.close()
```

## What Just Happened?

🎉 **Congratulations!** You've just:

1. ✅ Defined type-safe domain models
2. ✅ Automatically generated BSON codecs at compile-time
3. ✅ Persisted complex nested structures to MongoDB
4. ✅ Queried data with type safety
5. ✅ Used Scala 3 case classes with full BSON support

## Next Steps

- 🔍 **[DSL Reference](DSL.md)** — Full API for filters, updates, sort, projection, and `BsonEncoder`
- 🧪 **[DSL Testing](DSL.md#testing-with-dsltestkit)** — Use `DslTestKit` to unit-test your query logic
- 📖 **[Feature Overview](FEATURES.md)** — Sealed traits, enums, opaque types, and more
- 🎯 **[Enum Support](ENUM_SUPPORT.md)** — Scala 3 enum handling
- ❓ **[FAQ](FAQ.md)** — Common questions and troubleshooting

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

**Solution:** Use the native Scala 3 driver artifact (5.7.0+). No `CrossVersion.for3Use2_13` shim is needed anymore:

```scala
"org.mongodb.scala" %% "mongo-scala-driver" % "5.7.0"
```

---

**Need help?** Open an issue on [GitHub](https://github.com/mbannour/MongoScala3Codec/issues)

