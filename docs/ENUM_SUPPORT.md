# Enumeration Support in MongoScala3Codec

Comprehensive guide for using Scala 3 enums with MongoDB in MongoScala3Codec.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Basic Enum Types](#basic-enum-types)
  - [String-Based Enums](#string-based-enums)
  - [Ordinal-Based Enums](#ordinal-based-enums)
- [Custom Field Enums](#custom-field-enums)
  - [Automatic Code Detection](#automatic-code-detection)
  - [Using @BsonEnum Annotation](#using-bsonenum-annotation)
  - [Custom EnumValueCodecProvider](#custom-enumvalueccodecprovider)
- [Advanced Use Cases](#advanced-use-cases)
  - [Optional Enum Fields](#optional-enum-fields)
  - [Collections of Enums](#collections-of-enums)
  - [Maps with Enum Values](#maps-with-enum-values)
  - [Nested Case Classes with Enums](#nested-case-classes-with-enums)
  - [Multiple Enums in One Class](#multiple-enums-in-one-class)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Migration Guide](#migration-guide)
- [API Reference](#api-reference)

## Overview

MongoScala3Codec provides comprehensive support for Scala 3 enums with MongoDB. The library handles enum serialization and deserialization using compile-time macros, avoiding reflection where possible for better performance and type safety.

**⚠️ Important:** This library does **not support sealed classes or sealed traits**. Use Scala 3 `enum` types instead. Scala 3 enumerations provide a superior alternative to sealed class hierarchies with better type safety, compile-time validation, and seamless MongoDB integration.

### Key Features

- ✅ **String-based serialization**: Store enums as their name/toString value
- ✅ **Ordinal-based serialization**: Store enums as their ordinal (index) value
- ✅ **Custom field support**: Use custom fields (like `code`, `value`, `id`) for serialization
- ✅ **@BsonEnum annotation**: Explicitly specify which field to use for custom enums
- ✅ **Automatic code detection**: Automatically detects and uses `code` field without annotation
- ✅ **Optional enums**: Full support for `Option[EnumType]`
- ✅ **Collections**: Lists, Sets, Sequences of enums
- ✅ **Maps**: Maps with enum values
- ✅ **Type safety**: Compile-time validation and better error messages
- ✅ **Query support**: Query MongoDB by enum values

## Quick Start

```scala
import io.github.mbannour.mongo.codecs.*
import io.github.mbannour.bson.macros.BsonEnum
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient

// 1. Define your enum
enum Priority:
  case Low, Medium, High

// 2. Define your case class
case class Task(_id: ObjectId, title: String, priority: Priority)

// 3. Create codec provider for the enum
val priorityProvider = EnumValueCodecProvider.forStringEnum[Priority]

// 4. Build registry with enum codec
val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(priorityProvider)
  .register[Task]
  .build

// 5. Use with MongoDB
val database = mongoClient.getDatabase("mydb").withCodecRegistry(registry)
val collection = database.getCollection[Task]("tasks")

// Insert
val task = Task(new ObjectId(), "Complete report", Priority.High)
collection.insertOne(task)

// Query
val highPriorityTasks = collection.find(
  Filters.equal("priority", Priority.High)
).toFuture()
```

## Basic Enum Types

### String-Based Enums

String-based enums are stored in MongoDB using their name (toString value).

**Definition:**

```scala
enum Color:
  case Red, Green, Blue, Yellow

case class ColoredItem(
  _id: ObjectId,
  name: String,
  color: Color
)
```

**Setup:**

```scala
import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

// Create codec provider
val colorProvider = EnumValueCodecProvider.forStringEnum[Color]

// Register with codec registry
val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(colorProvider)
  .register[ColoredItem]
  .build
```

**BSON Representation:**

```json
{
  "_id": ObjectId("..."),
  "name": "Ruby Gem",
  "color": "Red"
}
```

**Advantages:**
- ✅ Human-readable in database
- ✅ Resilient to enum reordering
- ✅ Easy to query and debug

**Use Cases:**
- Status fields (Active, Inactive, Pending)
- Categories (Electronics, Clothing, Books)
- Simple enumerations without additional data

### Ordinal-Based Enums

Ordinal-based enums are stored using their index (0, 1, 2, ...).

**Definition:**

```scala
enum Level:
  case Beginner    // ordinal = 0
  case Intermediate // ordinal = 1
  case Advanced    // ordinal = 2
  case Expert      // ordinal = 3

case class UserProfile(
  _id: ObjectId,
  username: String,
  level: Level
)
```

**Setup:**

```scala
// Create ordinal codec provider
val levelProvider = EnumValueCodecProvider.forOrdinalEnum[Level]

// Register with codec registry
val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(levelProvider)
  .register[UserProfile]
  .build
```

**BSON Representation:**

```json
{
  "_id": ObjectId("..."),
  "username": "player123",
  "level": 2
}
```

**Advantages:**
- ✅ Compact storage (integers are smaller than strings)
- ✅ Efficient for large datasets
- ✅ Fast comparison operations

**Disadvantages:**
- ⚠️ **Breaking change risk**: Adding/removing enum values changes ordinals
- ⚠️ Less readable in database

**Use Cases:**
- Ordered progressions (Beginner → Expert)
- Performance-critical applications
- When enum order is stable and meaningful

## Custom Field Enums

### Automatic Code Detection

MongoScala3Codec automatically detects and uses a `code` field in enums without requiring annotation.

**Definition:**

```scala
enum StatusCode(val code: Int):
  case Success extends StatusCode(200)
  case Created extends StatusCode(201)
  case BadRequest extends StatusCode(400)
  case NotFound extends StatusCode(404)
  case ServerError extends StatusCode(500)

case class ApiResponse(
  _id: ObjectId,
  message: String,
  status: StatusCode  // No annotation needed!
)
```

**Setup:**

```scala
// Standard string-based provider works
val statusProvider = EnumValueCodecProvider.forStringEnum[StatusCode]

val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(statusProvider)
  .register[ApiResponse]
  .build
```

**How It Works:**

When decoding from BSON:
1. If value is a **String**: Uses `valueOf` to find enum by name
2. If value is an **Int**:
   - First tries ordinal lookup
   - If ordinal is out of bounds, automatically tries `code` field
   - Finds enum where `code == value`

**BSON Representation:**

String encoding:
```json
{
  "_id": ObjectId("..."),
  "message": "Resource not found",
  "status": "NotFound"
}
```

Integer encoding (with custom codec):
```json
{
  "_id": ObjectId("..."),
  "message": "Resource not found",
  "status": 404
}
```

**Advantages:**
- ✅ Backward compatible with existing `code` pattern
- ✅ No annotation required
- ✅ Works with both string and integer values

### Using @BsonEnum Annotation

Use `@BsonEnum` annotation to explicitly specify which field to use for serialization.

**Definition:**

```scala
import io.github.mbannour.bson.macros.BsonEnum

enum Priority(val value: String, val weight: Int):
  case Low extends Priority("low", 1)
  case Medium extends Priority("medium", 5)
  case High extends Priority("high", 10)
  case Critical extends Priority("critical", 20)

case class TaskItem(
  _id: ObjectId,
  title: String,
  @BsonEnum(nameField = "value") priority: Priority
)
```

**Setup:**

You need to create a custom codec provider that uses the specified field:

```scala
import org.bson.codecs.{Codec, StringCodec}

// Provide String codec
given Codec[String] = new StringCodec()

// Create custom codec provider
val priorityProvider = EnumValueCodecProvider[Priority, String](
  toValue = _.value,  // How to encode: get the value field
  fromValue = str => Priority.values.find(_.value == str).getOrElse(
    throw new IllegalArgumentException(s"Invalid priority: $str")
  )
)

val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(priorityProvider)
  .register[TaskItem]
  .build
```

**BSON Representation:**

```json
{
  "_id": ObjectId("..."),
  "title": "Fix critical bug",
  "priority": "critical"
}
```

**Multiple Custom Fields Example:**

```scala
enum Category(val id: String, val displayName: String):
  case Electronics extends Category("ELEC", "Electronics")
  case Clothing extends Category("CLTH", "Clothing")
  case Food extends Category("FOOD", "Food & Beverages")
  case Books extends Category("BOOK", "Books & Media")

case class Product(
  _id: ObjectId,
  name: String,
  @BsonEnum(nameField = "id") category: Category,
  price: Double
)

// Codec provider using id field
given Codec[String] = new StringCodec()

val categoryProvider = EnumValueCodecProvider[Category, String](
  toValue = _.id,
  fromValue = id => Category.values.find(_.id == id).getOrElse(
    throw new IllegalArgumentException(s"Invalid category id: $id")
  )
)
```

**BSON Representation:**

```json
{
  "_id": ObjectId("..."),
  "name": "Laptop",
  "category": "ELEC",
  "price": 999.99
}
```

### Custom EnumValueCodecProvider

The most flexible approach is to create custom codec providers.

**Basic Pattern:**

```scala
import org.bson.codecs.{Codec, StringCodec, IntegerCodec}

// String-based custom codec
given Codec[String] = new StringCodec()

val myEnumProvider = EnumValueCodecProvider[MyEnum, String](
  toValue = enum => /* convert enum to string */,
  fromValue = str => /* convert string to enum */
)

// Integer-based custom codec
given Codec[Int] = new IntegerCodec().asInstanceOf[Codec[Int]]

val myEnumProvider = EnumValueCodecProvider[MyEnum, Int](
  toValue = enum => /* convert enum to int */,
  fromValue = int => /* convert int to enum */
)
```

**Complete Example:**

```scala
enum Permission(val bitFlag: Int):
  case Read extends Permission(1)
  case Write extends Permission(2)
  case Execute extends Permission(4)
  case Admin extends Permission(8)

case class AccessControl(
  _id: ObjectId,
  userId: String,
  permissions: List[Permission]
)

// Create integer-based codec for Permission
given Codec[Int] = new IntegerCodec().asInstanceOf[Codec[Int]]

val permissionProvider = EnumValueCodecProvider[Permission, Int](
  toValue = _.bitFlag,
  fromValue = flag => Permission.values.find(_.bitFlag == flag).getOrElse(
    throw new IllegalArgumentException(s"Invalid permission flag: $flag")
  )
)

val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(permissionProvider)
  .register[AccessControl]
  .build
```

**BSON Representation:**

```json
{
  "_id": ObjectId("..."),
  "userId": "user123",
  "permissions": [1, 2, 4]
}
```

## Advanced Use Cases

### Optional Enum Fields

Enums work seamlessly with `Option` types.

**Definition:**

```scala
enum Theme:
  case Light, Dark, Auto

case class UserSettings(
  _id: ObjectId,
  username: String,
  theme: Option[Theme],
  notifications: Option[Boolean]
)
```

**Setup:**

```scala
val themeProvider = EnumValueCodecProvider.forStringEnum[Theme]

val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone  // Important: None values won't be stored
  .withProviders(themeProvider)
  .register[UserSettings]
  .build
```

**Usage:**

```scala
// With Some value
val settings1 = UserSettings(
  _id = new ObjectId(),
  username = "alice",
  theme = Some(Theme.Dark),
  notifications = Some(true)
)

// With None value (field will be omitted from BSON with ignoreNone)
val settings2 = UserSettings(
  _id = new ObjectId(),
  username = "bob",
  theme = None,
  notifications = None
)
```

**BSON Representations:**

With `Some` value:
```json
{
  "_id": ObjectId("..."),
  "username": "alice",
  "theme": "Dark",
  "notifications": true
}
```

With `None` value (using `ignoreNone`):
```json
{
  "_id": ObjectId("..."),
  "username": "bob"
}
```

With `None` value (using `encodeNone`):
```json
{
  "_id": ObjectId("..."),
  "username": "bob",
  "theme": null,
  "notifications": null
}
```

### Collections of Enums

Enums work with all Scala collection types.

**Definition:**

```scala
enum Tag:
  case Important, Urgent, Review, Archive

case class Document(
  _id: ObjectId,
  title: String,
  tags: List[Tag],           // List
  categories: Set[Tag],       // Set
  priorities: Seq[Tag],       // Seq
  metadata: Vector[Tag]       // Vector
)
```

**Setup:**

```scala
val tagProvider = EnumValueCodecProvider.forStringEnum[Tag]

val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(tagProvider)
  .register[Document]
  .build
```

**BSON Representation:**

```json
{
  "_id": ObjectId("..."),
  "title": "Project Proposal",
  "tags": ["Important", "Review"],
  "categories": ["Important", "Urgent"],
  "priorities": ["Urgent", "Important"],
  "metadata": ["Review"]
}
```

**Empty Collections:**

```scala
val doc = Document(
  _id = new ObjectId(),
  title = "Empty Doc",
  tags = List.empty,
  categories = Set.empty,
  priorities = Seq.empty,
  metadata = Vector.empty
)
```

```json
{
  "_id": ObjectId("..."),
  "title": "Empty Doc",
  "tags": [],
  "categories": [],
  "priorities": [],
  "metadata": []
}
```

### Maps with Enum Values

Enums can be used as values in Maps.

**Definition:**

```scala
enum Status:
  case Pending, InProgress, Completed, Failed

case class ProjectTracker(
  _id: ObjectId,
  projectName: String,
  taskStatuses: Map[String, Status]  // Task ID -> Status
)
```

**Setup:**

```scala
val statusProvider = EnumValueCodecProvider.forStringEnum[Status]

val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(statusProvider)
  .register[ProjectTracker]
  .build
```

**Usage:**

```scala
val project = ProjectTracker(
  _id = new ObjectId(),
  projectName = "Website Redesign",
  taskStatuses = Map(
    "TASK-001" -> Status.Completed,
    "TASK-002" -> Status.InProgress,
    "TASK-003" -> Status.Pending
  )
)
```

**BSON Representation:**

```json
{
  "_id": ObjectId("..."),
  "projectName": "Website Redesign",
  "taskStatuses": {
    "TASK-001": "Completed",
    "TASK-002": "InProgress",
    "TASK-003": "Pending"
  }
}
```

**Note:** Enums as **keys** in Maps are not directly supported. Use enum values as strings for keys:

```scala
// Not recommended
Map[Status, String]  // Won't work directly

// Recommended
Map[String, String]  // Use status.toString as key
```

### Nested Case Classes with Enums

Enums work within nested structures.

**Definition:**

```scala
enum OrderStatus:
  case Placed, Processing, Shipped, Delivered

enum PaymentMethod:
  case CreditCard, PayPal, BankTransfer

case class OrderItem(
  productId: String,
  quantity: Int,
  status: OrderStatus
)

case class Order(
  _id: ObjectId,
  orderId: String,
  items: List[OrderItem],
  paymentMethod: PaymentMethod,
  overallStatus: OrderStatus
)
```

**Setup:**

```scala
val orderStatusProvider = EnumValueCodecProvider.forStringEnum[OrderStatus]
val paymentMethodProvider = EnumValueCodecProvider.forStringEnum[PaymentMethod]

val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(orderStatusProvider, paymentMethodProvider)
  .register[OrderItem]  // Register nested class
  .register[Order]
  .build
```

**BSON Representation:**

```json
{
  "_id": ObjectId("..."),
  "orderId": "ORD-12345",
  "items": [
    {
      "productId": "PROD-001",
      "quantity": 2,
      "status": "Shipped"
    },
    {
      "productId": "PROD-002",
      "quantity": 1,
      "status": "Processing"
    }
  ],
  "paymentMethod": "CreditCard",
  "overallStatus": "Processing"
}
```

### Multiple Enums in One Class

You can use multiple different enums in a single case class.

**Definition:**

```scala
enum Difficulty:
  case Easy, Medium, Hard, Expert

enum GameMode:
  case Story, Survival, Creative, Multiplayer

enum Character:
  case Warrior, Mage, Archer, Rogue

case class GameSession(
  _id: ObjectId,
  playerName: String,
  difficulty: Difficulty,
  mode: GameMode,
  character: Character,
  level: Int
)
```

**Setup:**

```scala
val difficultyProvider = EnumValueCodecProvider.forStringEnum[Difficulty]
val gameModeProvider = EnumValueCodecProvider.forStringEnum[GameMode]
val characterProvider = EnumValueCodecProvider.forStringEnum[Character]

val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(difficultyProvider, gameModeProvider, characterProvider)
  .register[GameSession]
  .build
```

**BSON Representation:**

```json
{
  "_id": ObjectId("..."),
  "playerName": "DragonSlayer",
  "difficulty": "Hard",
  "mode": "Survival",
  "character": "Warrior",
  "level": 42
}
```

## Best Practices

### 1. Choose the Right Serialization Strategy

| Strategy | Use When | Avoid When |
|----------|----------|------------|
| **String-based** | Human-readable data needed, enum order may change, debugging is important | Storage space is critical, very high volume |
| **Ordinal-based** | Enum order is stable, performance critical, storage space matters | Enum order might change, adding/removing values |
| **Custom field** | Domain has existing codes/IDs, integration with external systems | Simple enumerations without additional meaning |

### 2. Naming Conventions

```scala
// ✅ Good: Clear, descriptive names
enum OrderStatus:
  case Pending, Processing, Shipped, Delivered, Cancelled

// ❌ Bad: Ambiguous names
enum Status:
  case S1, S2, S3  // What do these mean?
```

### 3. Document Custom Fields

```scala
// ✅ Good: Document the meaning of custom fields
enum HttpStatus(val code: Int, val message: String):
  /** Standard success response */
  case OK extends HttpStatus(200, "OK")

  /** Resource created successfully */
  case Created extends HttpStatus(201, "Created")

  /** Client error - bad request */
  case BadRequest extends HttpStatus(400, "Bad Request")
```

### 4. Use @BsonEnum for Clarity

```scala
// ✅ Good: Explicit about which field is used
case class Task(
  _id: ObjectId,
  title: String,
  @BsonEnum(nameField = "code") priority: Priority
)

// ⚠️ Less clear: Relies on automatic detection
case class Task(
  _id: ObjectId,
  title: String,
  priority: Priority  // Will it use code? value? name?
)
```

### 5. Handle Migration Carefully

When changing enum serialization strategy:

```scala
// Before migration: ordinal-based
enum Status:
  case Active, Inactive, Pending

// During migration: Support both old (ordinal) and new (string) formats
val statusProvider = EnumValueCodecProvider[Status, String](
  toValue = _.toString,  // Write as string
  fromValue = str => Status.values.find(_.toString == str).getOrElse(
    // Fallback: try to parse as ordinal for old data
    Status.values.lift(str.toIntOption.getOrElse(-1))
      .getOrElse(throw new IllegalArgumentException(s"Invalid status: $str"))
  )
)
```

### 6. Validate Enum Values

```scala
// ✅ Good: Validate and provide clear error messages
val categoryProvider = EnumValueCodecProvider[Category, String](
  toValue = _.id,
  fromValue = id => Category.values.find(_.id == id).getOrElse {
    val validIds = Category.values.map(_.id).mkString(", ")
    throw new IllegalArgumentException(
      s"Invalid category id: '$id'. Valid values are: $validIds"
    )
  }
)
```

### 7. Test Enum Roundtrips

Always test that enums serialize and deserialize correctly:

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit.*

test("Priority enum roundtrip") {
  val task = Task(new ObjectId(), "Test", Priority.High)

  // Test roundtrip
  val roundtripped = roundTrip(task)
  roundtripped shouldBe task

  // Test BSON structure
  val bsonDoc = toBsonDocument(task)
  bsonDoc.getString("priority").getValue shouldBe "High"
}
```

## Troubleshooting

### Issue: "No enum value found for: X"

**Cause:** The enum value in the database doesn't match any enum case.

**Solutions:**

1. Check the BSON value in MongoDB:
```scala
val docCollection: MongoCollection[Document] = database.getCollection("collection")
val doc = docCollection.find().first().futureValue
println(doc.getString("enumField"))  // Check actual value
```

2. Add fallback handling:
```scala
val provider = EnumValueCodecProvider[Status, String](
  toValue = _.toString,
  fromValue = str => Status.values.find(_.toString == str)
    .getOrElse(Status.Unknown)  // Fallback to Unknown
)
```

### Issue: "ClassCastException" when reading enum

**Cause:** BSON type mismatch (e.g., expecting String but got Int).

**Solutions:**

1. Check BSON type:
```javascript
// In MongoDB shell
db.collection.findOne()
// Check if field is string, int, or something else
```

2. Update codec to handle both types:
```scala
// Handle both String and Int values
val provider = EnumValueCodecProvider[Status, String](
  toValue = _.code.toString,
  fromValue = str => {
    // Try as string first
    Status.values.find(_.code.toString == str)
      .orElse(
        // Try as int
        str.toIntOption.flatMap(i => Status.values.find(_.code == i))
      )
      .getOrElse(throw new IllegalArgumentException(s"Invalid status: $str"))
  }
)
```

### Issue: "@BsonEnum annotation not working"

**Cause:** The codec provider doesn't use the custom field.

**Solution:** Create a custom codec provider:

```scala
// ❌ Wrong: This ignores @BsonEnum
val provider = EnumValueCodecProvider.forStringEnum[Priority]

// ✅ Correct: Use custom provider that respects the field
given Codec[String] = new StringCodec()

val provider = EnumValueCodecProvider[Priority, String](
  toValue = _.value,  // Use the value field as specified in @BsonEnum
  fromValue = str => Priority.values.find(_.value == str).getOrElse(
    throw new IllegalArgumentException(s"Invalid priority: $str")
  )
)
```

### Issue: "No enum value with ordinal X"

**Cause:** Enum was reordered or values were added/removed.

**Solutions:**

1. Switch to string-based encoding (recommended):
```scala
val provider = EnumValueCodecProvider.forStringEnum[MyEnum]
```

2. Migrate existing data:
```javascript
// MongoDB migration script
db.collection.find({ status: { $type: "int" } }).forEach(doc => {
  const statusMapping = {
    0: "Active",
    1: "Inactive",
    2: "Pending"
  };
  db.collection.updateOne(
    { _id: doc._id },
    { $set: { status: statusMapping[doc.status] } }
  );
});
```

### Issue: "Enum in nested case class not working"

**Cause:** Nested case class not registered in codec registry.

**Solution:** Register all nested classes:

```scala
val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
  .ignoreNone
  .withProviders(statusProvider)
  .register[NestedClass]  // ✅ Register nested class first
  .register[ParentClass]
  .build
```

### Issue: Query by enum not finding documents

**Cause:** Type mismatch between query and stored value.

**Solutions:**

1. Verify stored type:
```javascript
db.collection.findOne()  // Check actual stored value
```

2. Use correct query type:
```scala
// If stored as string
collection.find(Filters.equal("status", Status.Active.toString))

// If stored with custom field
collection.find(Filters.equal("status", Status.Active.code))

// If using EnumValueCodecProvider, it should work directly
collection.find(Filters.equal("status", Status.Active))
```

## Migration Guide

### From Reflection-Based to Macro-Based

The new enum support uses compile-time macros instead of runtime reflection.

**Before (Reflection-based):**

```scala
// Old code used reflection internally
val statusCodec = ... // Used Class.forName, valueOf, etc.
```

**After (Macro-based):**

```scala
// New code uses compile-time macros
val statusProvider = EnumValueCodecProvider.forStringEnum[Status]

// For custom fields, create explicit provider
given Codec[String] = new StringCodec()

val statusProvider = EnumValueCodecProvider[Status, String](
  toValue = _.value,
  fromValue = str => Status.values.find(_.value == str).getOrElse(
    throw new IllegalArgumentException(s"Invalid status: $str")
  )
)
```

**Benefits:**
- ✅ Compile-time type safety
- ✅ Better error messages
- ✅ No runtime reflection overhead
- ✅ Explicit field mapping

### From Ordinal to String-Based

**Step 1:** Add migration codec that reads both formats:

```scala
val migrationProvider = EnumValueCodecProvider[Status, String](
  toValue = _.toString,  // Write as string (new format)
  fromValue = str => {
    // Try string first (new format)
    Status.values.find(_.toString == str)
      .orElse {
        // Try ordinal (old format)
        str.toIntOption.flatMap(ord => Status.values.lift(ord))
      }
      .getOrElse(throw new IllegalArgumentException(s"Invalid status: $str"))
  }
)
```

**Step 2:** Run migration script:

```javascript
// Convert all ordinal values to strings
db.collection.find({ status: { $type: "int" } }).forEach(doc => {
  const statusNames = ["Active", "Inactive", "Pending"];
  if (doc.status >= 0 && doc.status < statusNames.length) {
    db.collection.updateOne(
      { _id: doc._id },
      { $set: { status: statusNames[doc.status] } }
    );
  }
});
```

**Step 3:** Switch to string-only codec:

```scala
val statusProvider = EnumValueCodecProvider.forStringEnum[Status]
```

## API Reference

### EnumValueCodecProvider

Factory for creating enum codec providers.

#### Methods

##### `forStringEnum[E <: Enum]`

Creates a codec provider that serializes enums as strings using their name.

```scala
def forStringEnum[E <: Enum: ClassTag]: CodecProvider
```

**Example:**
```scala
val provider = EnumValueCodecProvider.forStringEnum[Color]
```

##### `forOrdinalEnum[E <: Enum]`

Creates a codec provider that serializes enums as integers using their ordinal.

```scala
def forOrdinalEnum[E <: Enum: ClassTag]: CodecProvider
```

**Example:**
```scala
val provider = EnumValueCodecProvider.forOrdinalEnum[Level]
```

##### `apply[E, V]`

Creates a custom codec provider with explicit encoding/decoding functions.

```scala
def apply[E <: Enum: ClassTag, V](
  toValue: E => V,
  fromValue: V => E
)(using valueCodec: Codec[V]): CodecProvider
```

**Parameters:**
- `toValue`: Function to convert enum to value type V
- `fromValue`: Function to convert value type V back to enum
- `valueCodec`: Implicit codec for the value type V

**Example:**
```scala
given Codec[Int] = new IntegerCodec().asInstanceOf[Codec[Int]]

val provider = EnumValueCodecProvider[Priority, Int](
  toValue = _.weight,
  fromValue = w => Priority.values.find(_.weight == w).getOrElse(
    throw new IllegalArgumentException(s"Invalid weight: $w")
  )
)
```

### @BsonEnum Annotation

Annotation to specify custom field name for enum serialization.

```scala
case class BsonEnum(nameField: String = "") extends StaticAnnotation
```

**Parameters:**
- `nameField`: Name of the enum field to use for serialization (default: empty string uses toString)

**Example:**
```scala
import io.github.mbannour.bson.macros.BsonEnum

case class Task(
  _id: ObjectId,
  title: String,
  @BsonEnum(nameField = "value") priority: Priority
)
```

**Note:** You must create a corresponding codec provider that actually uses this field.

### EnumCodecGenerator (Internal)

Internal macro-based generator for enum codecs. Not typically used directly.

**Methods:**
- `fromString[E](value: String, customField: String): E`
- `fromInt[E](value: Int, customField: String): E`
- `toString[E](value: E, customField: String): String`
- `toInt[E](value: E, customField: String): Int`

---

## Summary

MongoScala3Codec provides comprehensive enum support with:

1. **Multiple serialization strategies**: String, ordinal, and custom fields
2. **Type safety**: Compile-time validation with macros
3. **Flexibility**: Support for all Scala collection types and nested structures
4. **Performance**: Minimal runtime overhead
5. **Developer experience**: Clear APIs and helpful error messages

Choose the serialization strategy that best fits your use case, and always test your enum roundtrips to ensure correct behavior.

For more information, see:
- [FEATURES.md](FEATURES.md) - Overview of all library features
- [HOW_IT_WORKS.md](HOW_IT_WORKS.md) - Deep dive into internals
- [BSON_TYPE_MAPPING.md](BSON_TYPE_MAPPING.md) - Type mapping reference
