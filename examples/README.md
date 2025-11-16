# MongoScala3Codec Examples

This directory contains comprehensive, runnable examples demonstrating all the features of MongoScala3Codec.

## Prerequisites

- **Scala 3.7.1** (or compatible 3.3+)
- **MongoDB** running on `localhost:27017`
- **sbt** build tool

## Quick Start

### Start MongoDB

```bash
# Using Docker (recommended)
docker run -d -p 27017:27017 --name mongo-examples mongo:latest

# Or use your local MongoDB installation
mongod
```

### Run an Example

```bash
# From the project root directory
sbt "examples/runMain io.github.mbannour.examples.BasicCaseClassExample"

# Or enter sbt shell and run
sbt
> examples/runMain io.github.mbannour.examples.BasicCaseClassExample
```

## Available Examples

### 1. Basic Case Class Example 📝
**File:** `BasicCaseClassExample.scala`

**What it demonstrates:**
- Simple case class serialization with primitive types
- ObjectId as `_id` field
- Basic CRUD operations (Create, Read, Update, Delete)
- Working with typed MongoDB collections

**Run:**
```bash
sbt "examples/runMain io.github.mbannour.examples.BasicCaseClassExample"
```

**Key concepts:**
```scala
case class User(_id: ObjectId, name: String, age: Int, email: String, isActive: Boolean)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build
```

---

### 2. Nested Structures Example 🏗️
**File:** `NestedStructuresExample.scala`

**What it demonstrates:**
- Nested case classes
- Registration order (nested types first)
- Querying nested fields with dot notation
- Updating nested fields

**Run:**
```bash
sbt "examples/runMain io.github.mbannour.examples.NestedStructuresExample"
```

**Key concepts:**
```scala
case class Address(street: String, city: String, state: String, zipCode: String)
case class Company(_id: ObjectId, name: String, headquarters: Address, ...)

// Register nested types FIRST
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Address]      // Nested type first
  .register[Company]      // Then parent type
  .build

// Query nested fields
collection.find(Filters.equal("headquarters.city", "San Francisco"))
```

---

### 3. Enum Support Example 🔢
**File:** `EnumSupportExample.scala`

**What it demonstrates:**
- String-based enum serialization
- Ordinal-based enum serialization
- Enums with custom fields (code pattern)
- Multiple enums in one case class
- Querying by enum values

**Run:**
```bash
sbt "examples/runMain io.github.mbannour.examples.EnumSupportExample"
```

**Key concepts:**
```scala
// Simple string-based enum
enum Priority:
  case Low, Medium, High, Critical

// Enum with custom integer field
enum Status(val code: Int):
  case Pending extends Status(0)
  case InProgress extends Status(1)
  case Completed extends Status(2)

// Create codec providers
val priorityProvider = EnumValueCodecProvider.forStringEnum[Priority]

given Codec[Int] = new IntegerCodec().asInstanceOf[Codec[Int]]
val statusProvider = EnumValueCodecProvider[Status, Int](
  toValue = _.code,
  fromValue = code => Status.values.find(_.code == code).getOrElse(...)
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProviders(priorityProvider, statusProvider)
  .register[Task]
  .build
```

---

### 4. Optional Fields and Collections Example 📦
**File:** `OptionalFieldsAndCollectionsExample.scala`

**What it demonstrates:**
- `Option[T]` fields with `NoneHandling.Ignore` (omit from BSON)
- `Option[T]` fields with `NoneHandling.Encode` (store as null)
- Collections: `List`, `Set`, `Vector`, `Map`
- Nested collections
- Querying by field existence
- Querying collection elements

**Run:**
```bash
sbt "examples/runMain io.github.mbannour.examples.OptionalFieldsAndCollectionsExample"
```

**Key concepts:**
```scala
case class BlogPost(
  _id: ObjectId,
  title: String,
  tags: List[String],           // Collection
  metadata: Map[String, String], // Map
  publishedAt: Option[Instant],  // Optional field
  excerpt: Option[String]
)

// Configure to ignore None values
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[BlogPost]
  .build

// Query by collection element
collection.find(Filters.equal("tags", "scala"))

// Query by field existence
collection.find(Filters.exists("publishedAt", true))
```

---

---

## Example Output

When you run an example, you'll see output like:

```
============================================================
Example 1: Basic Case Class with Primitive Types
============================================================

1. Inserting a new user...
✓ Inserted user: Alice Johnson (ID: 507f1f77bcf86cd799439011)

2. Finding the user...
✓ Found user: Alice Johnson, Age: 30, Active: true

3. Inserting multiple users...
✓ Inserted 3 more users

✓ Total users in database: 4
   - Alice Johnson (30 years old)
   - Bob Smith (25 years old)
   - Carol White (35 years old)
   - David Brown (28 years old)

...

============================================================
Example completed successfully!
============================================================
```

## Common Patterns

### Pattern 1: Registration Order
Always register nested types before parent types:

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Address]      // Nested type
  .register[ContactInfo]  // Type using Address
  .register[Company]      // Top-level type
  .build
```

### Pattern 2: Enum Codec Providers
**IMPORTANT:** Enums must be defined at package level (not inside objects) for reflection to work properly.

```scala
// ✅ CORRECT: Enum at package level
package io.github.mbannour.examples

enum MyEnum:
  case Value1, Value2

object MyExample:
  val enumProvider = EnumValueCodecProvider.forStringEnum[MyEnum]
  // ...

// ❌ WRONG: Enum inside object (reflection will fail)
object MyExample:
  enum MyEnum:
    case Value1, Value2
  // This will cause ClassNotFoundException at runtime!
```

Create providers before building the registry:

```scala
val enumProvider = EnumValueCodecProvider.forStringEnum[MyEnum]

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProviders(enumProvider)
  .register[MyClass]
  .build
```

### Pattern 3: Optional Field Handling
Configure `NoneHandling` before registration:

```scala
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[MyClass]
  .build
```

## Troubleshooting

### Error: "Can't find a codec for class X"

**Solution:** Make sure you registered the codec and applied the registry:

```scala
// Register the codec
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[MyClass]
  .build

// Apply to collection
val collection = database
  .getCollection[MyClass]("my_collection")
  .withCodecRegistry(registry)  // Don't forget this!
```

### Error: MongoDB Connection Refused

**Solution:** Make sure MongoDB is running:

```bash
# Check if MongoDB is running
docker ps | grep mongo

# Or check local MongoDB
pgrep mongod

# Start MongoDB if needed
docker run -d -p 27017:27017 --name mongo-examples mongo:latest
```

## Building Your Own Examples

To create your own example:

1. Create a file in `examples/src/main/scala/io/github/mbannour/examples/`
2. Define your domain models as case classes
3. Create a codec registry
4. Perform MongoDB operations
5. Clean up (drop database, close client)

**Template:**

```scala
package io.github.mbannour.examples

import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}
import org.bson.types.ObjectId

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

object MyExample:

  case class MyModel(_id: ObjectId, name: String)

  def main(args: Array[String]): Unit =
    val mongoClient = MongoClient("mongodb://localhost:27017")
    val database = mongoClient.getDatabase("examples_db")

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[MyModel]
      .build

    val collection = database
      .getCollection[MyModel]("my_collection")
      .withCodecRegistry(registry)

    try
      // Your code here
      val model = MyModel(new ObjectId(), "Example")
      Await.result(collection.insertOne(model).toFuture(), 5.seconds)
      println(s"Inserted: ${model.name}")

    catch
      case e: Exception =>
        e.printStackTrace()
    finally
      Await.result(database.drop().toFuture(), 5.seconds)
      mongoClient.close()

end MyExample
```

Then run with:
```bash
sbt "examples/runMain io.github.mbannour.examples.MyExample"
```

## Next Steps

- Read the [main documentation](../docs/QUICKSTART.md)
- Explore the [feature guide](../docs/FEATURES.md)
- Check the [FAQ](../docs/FAQ.md) for common questions
- Review the [BSON type mapping](../docs/BSON_TYPE_MAPPING.md) reference

## Need Help?

- Check the [FAQ](../docs/FAQ.md)
- Review the [troubleshooting guide](../docs/FAQ.md#troubleshooting)
- Open an issue on [GitHub](https://github.com/mbannour/MongoScala3Codec/issues)
