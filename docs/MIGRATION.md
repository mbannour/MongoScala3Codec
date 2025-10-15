# Migration Guide

This guide helps you migrate to MongoScala3Codec from manual codec implementations or other MongoDB codec libraries.

## Table of Contents

- [From Manual Codec Implementations](#from-manual-codec-implementations)
- [From Scala 2 MongoDB Libraries](#from-scala-2-mongodb-libraries)
- [From ReactiveMongo](#from-reactivemongo)
- [From mongo-scala-driver Custom Codecs](#from-mongo-scala-driver-custom-codecs)
- [Migration Checklist](#migration-checklist)
- [Common Migration Issues](#common-migration-issues)

---

## From Manual Codec Implementations

### Before: Manual Codec

```scala
// Old manual codec implementation
class PersonCodec extends Codec[Person] {
  override def encode(writer: BsonWriter, person: Person, ctx: EncoderContext): Unit = {
    writer.writeStartDocument()
    writer.writeObjectId("_id", person._id)
    writer.writeString("name", person.name)
    writer.writeInt32("age", person.age)
    
    person.email match {
      case Some(email) => writer.writeString("email", email)
      case None => // omit field
    }
    
    writer.writeEndDocument()
  }
  
  override def decode(reader: BsonReader, ctx: DecoderContext): Person = {
    reader.readStartDocument()
    
    var _id: ObjectId = null
    var name: String = null
    var age: Int = 0
    var email: Option[String] = None
    
    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
      val fieldName = reader.readName()
      fieldName match {
        case "_id" => _id = reader.readObjectId()
        case "name" => name = reader.readString()
        case "age" => age = reader.readInt32()
        case "email" => email = Some(reader.readString())
        case _ => reader.skipValue()
      }
    }
    
    reader.readEndDocument()
    Person(_id, name, age, email)
  }
  
  override def getEncoderClass: Class[Person] = classOf[Person]
}

// Register manually
val registry = CodecRegistries.fromRegistries(
  MongoClient.DEFAULT_CODEC_REGISTRY,
  CodecRegistries.fromCodecs(new PersonCodec())
)
```

### After: MongoScala3Codec

```scala
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig, NoneHandling}

case class Person(_id: ObjectId, name: String, age: Int, email: Option[String])

given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Person]
  .build
```

**Benefits:**
- ✅ ~40 lines of boilerplate eliminated
- ✅ Compile-time type safety
- ✅ Automatic handling of Option types
- ✅ No manual BSON reader/writer code

---

## From Scala 2 MongoDB Libraries

### From mongo-scala-bson with Case Class Codecs

#### Before (Scala 2 with Reflection)

```scala
// Scala 2 approach using reflection
import org.mongodb.scala.bson.codecs.Macros
import org.bson.codecs.configuration.CodecRegistries

case class User(name: String, age: Int, email: Option[String])

val codecRegistry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(
    Macros.createCodecProvider[User]()
  ),
  MongoClient.DEFAULT_CODEC_REGISTRY
)
```

#### After (Scala 3 with Macros)

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder

case class User(name: String, age: Int, email: Option[String])

val codecRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .build
```

**Key Differences:**
- ✅ No runtime reflection (faster)
- ✅ Better compile-time error messages
- ✅ Scala 3 native implementation
- ✅ More configuration options (NoneHandling, discriminators)

---

## From ReactiveMongo

### Before: ReactiveMongo Handlers

```scala
// ReactiveMongo with Play JSON
import reactivemongo.api.bson._

case class Person(name: String, age: Int, city: String)

implicit val personHandler: BSONDocumentHandler[Person] = Macros.handler[Person]

// Usage
val collection = db.collection[BSONCollection]("people")
collection.insert.one(person)
```

### After: MongoScala3Codec

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.mongodb.scala._

case class Person(name: String, age: Int, city: String)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Person]
  .build

val collection = database
  .getCollection[Person]("people")
  .withCodecRegistry(registry)

collection.insertOne(person).toFuture()
```

**Migration Steps:**
1. Replace `BSONDocumentHandler` with codec registration
2. Switch from ReactiveMongo to mongodb-scala-driver
3. Replace `Macros.handler` with `RegistryBuilder.register`
4. Update collection type from `BSONCollection` to `MongoCollection[T]`

---

## From mongo-scala-driver Custom Codecs

### Before: Custom CodecProvider Pattern

```scala
import org.bson.codecs.{Codec, CodecProvider}
import org.bson.codecs.configuration.{CodecRegistry, CodecRegistries}

class MyCodecProvider extends CodecProvider {
  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] = {
    if (clazz == classOf[Address]) {
      new AddressCodec().asInstanceOf[Codec[T]]
    } else if (clazz == classOf[Person]) {
      new PersonCodec(registry).asInstanceOf[Codec[T]]
    } else {
      null
    }
  }
}

val registry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(new MyCodecProvider()),
  MongoClient.DEFAULT_CODEC_REGISTRY
)
```

### After: MongoScala3Codec

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder

case class Address(street: String, city: String)
case class Person(name: String, address: Address)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Address]
  .register[Person]
  .build
```

**Benefits:**
- ✅ Automatic dependency resolution (Address is registered automatically when needed)
- ✅ No manual CodecProvider implementation
- ✅ Type-safe registration

---

## Migration Checklist

### Step 1: Update Dependencies

```scala
// build.sbt
libraryDependencies ++= Seq(
  "io.github.mbannour" %% "mongoscala3codec" % "0.0.6",
  "org.mongodb.scala" %% "mongo-scala-driver" % "5.2.1" cross CrossVersion.for3Use2_13
)

// Remove old dependencies:
// - reactivemongo
// - Custom codec libraries
```

### Step 2: Convert Domain Models to Scala 3 Case Classes

Ensure your models are:
- ✅ Case classes (not regular classes)
- ✅ Immutable (no vars)
- ✅ Using supported types

```scala
// ✅ Good
case class User(name: String, age: Int)

// ❌ Avoid
class User(val name: String, var age: Int)
```

### Step 3: Replace Manual Codecs with Registration

```scala
// Before
val registry = CodecRegistries.fromCodecs(
  new UserCodec(),
  new AddressCodec(),
  new CompanyCodec()
)

// After
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .register[Address]
  .register[Company]
  .build
```

### Step 4: Configure None Handling

```scala
// Choose your strategy
given CodecConfig = CodecConfig(
  noneHandling = NoneHandling.Ignore  // or NoneHandling.Encode
)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[User]
  .build
```

### Step 5: Update Collection Declarations

```scala
// Before
val collection: MongoCollection[Document] = database.getCollection("users")

// After (type-safe)
val collection: MongoCollection[User] = database
  .getCollection[User]("users")
  .withCodecRegistry(registry)
```

### Step 6: Test Your Migration

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit

// Test codec symmetry
val user = User("Alice", 30)
CodecTestKit.assertCodecSymmetry(user)

// Inspect BSON structure
val bson = CodecTestKit.toBsonDocument(user)
println(bson.toJson())
```

---

## Common Migration Issues

### Issue 1: "Cannot find codec for type X"

**Problem:**
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Person]  // Person contains Address field
  .build

// Error: Cannot find codec for Address
```

**Solution:** Register nested types first:
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Address]  // Register nested type first
  .register[Person]
  .build
```

---

### Issue 2: None Values Stored as null

**Problem:**
```scala
case class User(name: String, email: Option[String])
val user = User("Alice", None)
// Stored as: {"name": "Alice", "email": null}
```

**Solution:** Configure NoneHandling:
```scala
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withConfig(summon[CodecConfig])
  .register[User]
  .build

// Now stored as: {"name": "Alice"}
```

---

### Issue 3: Custom Types Not Supported

**Problem:**
```scala
case class Timestamp(value: Long)
case class Event(timestamp: Timestamp, name: String)

// Compile error: No codec for Timestamp
```

**Solution:** Provide a custom codec:
```scala
given timestampCodec: Codec[Timestamp] = new Codec[Timestamp] {
  def encode(w: BsonWriter, v: Timestamp, ctx: EncoderContext): Unit =
    w.writeInt64(v.value)
  
  def decode(r: BsonReader, ctx: DecoderContext): Timestamp =
    Timestamp(r.readInt64())
  
  def getEncoderClass: Class[Timestamp] = classOf[Timestamp]
}

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withCodec(timestampCodec)
  .register[Event]
  .build
```

---

### Issue 4: Sealed Trait Not Recognized

**Problem:**
```scala
sealed trait Animal
case class Dog(name: String) extends Animal
case class Cat(name: String) extends Animal

// Only registers Animal, not subtypes
```

**Solution:** Register the sealed trait (subtypes are automatic):
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Animal]  // Automatically includes Dog and Cat
  .build
```

---

### Issue 5: Field Names Don't Match MongoDB

**Problem:**
```scala
// Your Scala model
case class User(fullName: String)

// But MongoDB has: {"name": "Alice"}
```

**Solution:** Use `@BsonProperty`:
```scala
import org.mongodb.scala.bson.annotations.BsonProperty

case class User(@BsonProperty("name") fullName: String)
```

---

### Issue 6: Enum Not Working

**Problem:**
```scala
enum Status:
  case Active, Inactive

case class User(status: Status)
// Compile error
```

**Solution:** Register enum with `EnumValueCodecProvider`:
```scala
import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider[Status]())
  .register[User]
  .build
```

---

### Issue 7: Collections Not Encoding Properly

**Problem:**
```scala
case class Playlist(songs: List[Song])
case class Song(title: String)

// Error: No codec for List[Song]
```

**Solution:** Register the element type:
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Song]      // Register element type
  .register[Playlist]  // Collections handled automatically
  .build
```

---

## Performance Comparison

### Manual Codecs vs MongoScala3Codec

| Metric | Manual Codec | MongoScala3Codec |
|--------|--------------|------------------|
| Lines of Code | ~50-100 per type | ~1 per type |
| Compile Time | Fast | Slightly slower (macro expansion) |
| Runtime Performance | Baseline | ~Same (within 5%) |
| Type Safety | Manual | Automatic |
| Maintainability | Low | High |

---

## Gradual Migration Strategy

You can migrate incrementally without breaking existing code:

### Phase 1: New Types Only

```scala
// Keep existing manual codecs
val manualRegistry = CodecRegistries.fromCodecs(
  new OldUserCodec(),
  new OldAddressCodec()
)

// Use MongoScala3Codec for new types
val autoRegistry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[NewFeature]
  .register[NewModel]
  .build

// Combine registries
val combinedRegistry = CodecRegistries.fromRegistries(
  manualRegistry,
  autoRegistry
)
```

### Phase 2: Replace One Type at a Time

```scala
// Remove OldUserCodec, replace with automatic
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]  // Now automatic
  .withCodec(new OldAddressCodec())  // Still manual
  .build
```

### Phase 3: Full Migration

```scala
// All types using MongoScala3Codec
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[User]
  .register[Address]
  .register[Company]
  .build
```

---

## Next Steps

- 📖 [Feature Overview](FEATURES.md) - Learn about all features
- ❓ [FAQ](FAQ.md) - Common questions and troubleshooting
- 🚀 [Quickstart](QUICKSTART.md) - 5-minute getting started guide

