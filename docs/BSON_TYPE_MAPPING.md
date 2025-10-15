# BSON Type Coverage

Complete reference for BSON type support in MongoScala3Codec.

## Quick Reference Table

| Scala Type | BSON Type | Support Status | Notes |
|------------|-----------|----------------|-------|
| `String` | String | ✅ Full | UTF-8 encoded |
| `Int` | Int32 | ✅ Full | 32-bit signed integer |
| `Long` | Int64 | ✅ Full | 64-bit signed integer |
| `Double` | Double | ✅ Full | 64-bit IEEE 754 floating point |
| `Float` | Double | ✅ Full | Converted to Double |
| `Boolean` | Boolean | ✅ Full | true/false |
| `Byte` | Int32 | ✅ Full | Stored as Int32 |
| `Short` | Int32 | ✅ Full | Stored as Int32 |
| `Char` | String | ✅ Full | Single character string |
| `BigDecimal` | Decimal128 | ✅ Full | High-precision decimal |
| `BigInt` | String | ✅ Full | Arbitrary precision integer as string |
| `ObjectId` | ObjectId | ✅ Full | MongoDB ObjectId |
| `java.util.UUID` | Binary | ✅ Full | UUID subtype |
| `java.util.Date` | Date | ✅ Full | UTC milliseconds |
| `java.time.Instant` | Date | ✅ Full | UTC milliseconds |
| `java.time.LocalDate` | Date | ✅ Full | Midnight UTC |
| `java.time.LocalDateTime` | Date | ✅ Full | Converted to UTC |
| `java.time.ZonedDateTime` | Date | ✅ Full | Converted to UTC |
| `Array[Byte]` | Binary | ✅ Full | Binary data |
| `Option[T]` | T or null | ✅ Full | Configurable null handling |
| `List[T]` | Array | ✅ Full | Ordered collection |
| `Seq[T]` | Array | ✅ Full | Ordered collection |
| `Vector[T]` | Array | ✅ Full | Ordered collection |
| `Set[T]` | Array | ✅ Full | Unordered (order not preserved) |
| `Map[String, T]` | Document | ✅ Full | Embedded document |
| `Map[K, V]` | Array of pairs | ✅ Full | For non-String keys |
| `Either[L, R]` | Document | ✅ Full | Tagged union with discriminator |
| `scala.util.Try[T]` | Document | ✅ Full | Success/Failure discriminated |
| Case Classes | Document | ✅ Full | Nested documents |
| Sealed Traits | Document | ✅ Full | With type discriminator |
| Enums (simple) | String/Int | ✅ Full | Via EnumValueCodecProvider |
| Opaque Types | Underlying Type | ✅ Full | Zero-cost abstraction |

## Detailed Type Documentation

### Primitive Types

#### Numeric Types

```scala
case class Numbers(
  byteVal: Byte,        // BSON: Int32 (-128 to 127)
  shortVal: Short,      // BSON: Int32 (-32768 to 32767)
  intVal: Int,          // BSON: Int32 (-2^31 to 2^31-1)
  longVal: Long,        // BSON: Int64 (-2^63 to 2^63-1)
  floatVal: Float,      // BSON: Double (converted, may lose precision)
  doubleVal: Double     // BSON: Double (IEEE 754)
)
```

**BSON Storage:**
```json
{
  "byteVal": 42,
  "shortVal": 1000,
  "intVal": 100000,
  "longVal": {"$numberLong": "9223372036854775807"},
  "floatVal": 3.14,
  "doubleVal": 3.141592653589793
}
```

#### String and Character Types

```scala
case class TextData(
  string: String,       // BSON: String (UTF-8)
  char: Char           // BSON: String (single character)
)
```

**BSON Storage:**
```json
{
  "string": "Hello, MongoDB!",
  "char": "A"
}
```

#### Boolean Type

```scala
case class Flags(
  active: Boolean,      // BSON: Boolean
  verified: Boolean
)
```

**BSON Storage:**
```json
{
  "active": true,
  "verified": false
}
```

### High-Precision Numeric Types

#### BigDecimal (Decimal128)

```scala
case class FinancialRecord(
  _id: ObjectId,
  amount: BigDecimal    // BSON: Decimal128 (128-bit IEEE 754)
)

val record = FinancialRecord(
  new ObjectId(),
  BigDecimal("12345.67890123456789")  // Full precision preserved
)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "amount": {"$numberDecimal": "12345.67890123456789"}
}
```

**Use Cases:**
- Financial calculations requiring exact decimal arithmetic
- Currency amounts
- Tax calculations
- Any scenario where floating-point errors are unacceptable

#### BigInt (Arbitrary Precision)

```scala
case class LargeNumber(
  _id: ObjectId,
  value: BigInt         // BSON: String (arbitrary precision)
)

val large = LargeNumber(
  new ObjectId(),
  BigInt("12345678901234567890123456789")
)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "value": "12345678901234567890123456789"
}
```

**Note:** BigInt is stored as a string to preserve arbitrary precision beyond Int64 limits.

### MongoDB Types

#### ObjectId

```scala
case class Document(
  _id: ObjectId         // BSON: ObjectId (12-byte identifier)
)

val doc = Document(new ObjectId())
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "507f1f77bcf86cd799439011"}
}
```

#### UUID

```scala
import java.util.UUID

case class User(
  _id: ObjectId,
  uuid: UUID            // BSON: Binary (subtype 4)
)

val user = User(new ObjectId(), UUID.randomUUID())
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "uuid": {"$binary": {"base64": "...", "subType": "04"}}
}
```

### Date and Time Types

```scala
import java.util.Date
import java.time.{Instant, LocalDate, LocalDateTime, ZonedDateTime}

case class TimeData(
  _id: ObjectId,
  javaDate: Date,              // BSON: Date (UTC milliseconds)
  instant: Instant,            // BSON: Date (UTC milliseconds)
  localDate: LocalDate,        // BSON: Date (midnight UTC)
  localDateTime: LocalDateTime, // BSON: Date (converted to UTC)
  zonedDateTime: ZonedDateTime  // BSON: Date (converted to UTC)
)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "javaDate": {"$date": 1634567890000},
  "instant": {"$date": 1634567890000},
  "localDate": {"$date": 1634515200000},
  "localDateTime": {"$date": 1634567890000},
  "zonedDateTime": {"$date": 1634567890000}
}
```

**Important Notes:**
- All date/time types are stored as UTC milliseconds since epoch
- `LocalDate` is stored as midnight UTC (time component is 00:00:00)
- `LocalDateTime` assumes system default timezone for conversion
- `ZonedDateTime` preserves timezone info during conversion but stores as UTC

### Binary Data

```scala
case class BinaryData(
  _id: ObjectId,
  data: Array[Byte]     // BSON: Binary (generic binary)
)

val binary = BinaryData(
  new ObjectId(),
  "Hello".getBytes("UTF-8")
)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "data": {"$binary": {"base64": "SGVsbG8=", "subType": "00"}}
}
```

### Option Types

```scala
case class OptionalFields(
  _id: ObjectId,
  requiredField: String,
  optionalField: Option[String]
)

// With NoneHandling.Encode (default)
val withNull = OptionalFields(new ObjectId(), "required", None)
// BSON: {"_id": ..., "requiredField": "required", "optionalField": null}

// With NoneHandling.Ignore
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)
val withoutField = OptionalFields(new ObjectId(), "required", None)
// BSON: {"_id": ..., "requiredField": "required"}
```

**Configuration:**
```scala
// Encode None as null (default)
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)

// Omit None fields from document
given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)
```

### Collection Types

#### List, Seq, Vector

```scala
case class Collections(
  _id: ObjectId,
  list: List[String],
  seq: Seq[Int],
  vector: Vector[Double]
)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "list": ["item1", "item2", "item3"],
  "seq": [1, 2, 3],
  "vector": [1.1, 2.2, 3.3]
}
```

#### Set

```scala
case class Tags(
  _id: ObjectId,
  tags: Set[String]     // BSON: Array (order not preserved)
)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "tags": ["scala", "mongodb", "functional"]
}
```

**Note:** Set ordering is not preserved in BSON. When decoded, elements may be in different order.

#### Map with String Keys

```scala
case class Attributes(
  _id: ObjectId,
  metadata: Map[String, String]  // BSON: Document
)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "metadata": {
    "key1": "value1",
    "key2": "value2"
  }
}
```

#### Map with Non-String Keys

```scala
case class IntMap(
  _id: ObjectId,
  scores: Map[Int, String]  // BSON: Array of [key, value] pairs
)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "scores": [
    [1, "value1"],
    [2, "value2"]
  ]
}
```

### Either Type

```scala
case class Result(
  _id: ObjectId,
  data: Either[String, Int]  // BSON: Document with discriminator
)

val leftResult = Result(new ObjectId(), Left("error"))
val rightResult = Result(new ObjectId(), Right(42))
```

**BSON Storage:**
```json
// Left
{
  "_id": {"$oid": "..."},
  "data": {
    "_tag": "Left",
    "value": "error"
  }
}

// Right
{
  "_id": {"$oid": "..."},
  "data": {
    "_tag": "Right",
    "value": 42
  }
}
```

### Try Type

```scala
import scala.util.{Try, Success, Failure}

case class Computation(
  _id: ObjectId,
  result: Try[Int]       // BSON: Document with discriminator
)

val success = Computation(new ObjectId(), Success(42))
val failure = Computation(new ObjectId(), Failure(new Exception("error")))
```

**BSON Storage:**
```json
// Success
{
  "_id": {"$oid": "..."},
  "result": {
    "_tag": "Success",
    "value": 42
  }
}

// Failure
{
  "_id": {"$oid": "..."},
  "result": {
    "_tag": "Failure",
    "exception": "java.lang.Exception: error"
  }
}
```

### Case Classes (Nested Documents)

```scala
case class Address(street: String, city: String, zipCode: Int)
case class Person(_id: ObjectId, name: String, address: Address)

val person = Person(
  new ObjectId(),
  "Alice",
  Address("123 Main St", "Springfield", 12345)
)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "name": "Alice",
  "address": {
    "street": "123 Main St",
    "city": "Springfield",
    "zipCode": 12345
  }
}
```

### Sealed Traits (ADTs)

```scala
sealed trait Shape
case class Circle(_id: ObjectId, radius: Double) extends Shape
case class Rectangle(_id: ObjectId, width: Double, height: Double) extends Shape

// Register concrete implementations
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Circle]
  .register[Rectangle]
  .build
```

**BSON Storage (Circle):**
```json
{
  "_id": {"$oid": "..."},
  "radius": 5.0
}
```

**Note:** Current implementation registers concrete case classes individually. Full polymorphic sealed trait support is planned for future releases.

### Enums

```scala
enum Priority:
  case Low, Medium, High

case class Task(_id: ObjectId, priority: Priority)

import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider[Priority]())  // String-based
  .register[Task]
  .build
```

**BSON Storage (String-based):**
```json
{
  "_id": {"$oid": "..."},
  "priority": "High"
}
```

**BSON Storage (Ordinal-based):**
```scala
.withProvider(EnumValueCodecProvider[Priority](useOrdinal = true))
```
```json
{
  "_id": {"$oid": "..."},
  "priority": 2
}
```

### Opaque Types

```scala
opaque type UserId = String
object UserId:
  def apply(value: String): UserId = value
  extension (id: UserId) def value: String = id

case class User(_id: ObjectId, userId: UserId)
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "userId": "user_12345"
}
```

**Zero Runtime Overhead:** Opaque types are erased at compile time and stored as their underlying type.

## Type Limitations and Workarounds

### ❌ Not Supported

| Type | Status | Workaround |
|------|--------|------------|
| Mutable `var` fields | ❌ Not supported | Use immutable case classes |
| Non-case classes | ❌ Not supported | Convert to case classes |
| Enums with parameters | ❌ Limited support | Use sealed traits |
| Recursive types | ❌ Limited support | Use `Option` to break cycle |
| Generic type parameters | ❌ Limited support | Register concrete types |

### Workaround Examples

#### Self-Referential Types
```scala
// ❌ Not supported
case class Node(value: Int, next: Node)

// ✅ Workaround
case class Node(value: Int, next: Option[Node])
```

#### Enums with Parameters
```scala
// ❌ Limited support
enum Status(val code: Int):
  case Active extends Status(1)
  case Inactive extends Status(0)

// ✅ Workaround
sealed trait Status { def code: Int }
case object Active extends Status { val code = 1 }
case object Inactive extends Status { val code = 0 }
```

## Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|-----------------|-------|
| Primitive encode | O(1) | Direct BSON write |
| Primitive decode | O(1) | Direct BSON read |
| Collection encode | O(n) | Linear in collection size |
| Collection decode | O(n) | Linear in collection size |
| Nested document encode | O(fields) | Linear in field count |
| Nested document decode | O(fields) | Linear in field count |
| Sealed trait encode | O(1) | Pattern match |
| Sealed trait decode | O(1) | Discriminator lookup |

**Memory Overhead:** Minimal - only allocations are for the decoded objects themselves.

## Testing Your Types

Use `CodecTestKit` to verify type support:

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit

case class MyType(_id: ObjectId, data: YourType)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[MyType]
  .build

given codec: Codec[MyType] = registry.get(classOf[MyType])

val instance = MyType(new ObjectId(), YourType(...))

// Test round-trip
CodecTestKit.assertCodecSymmetry(instance)

// Inspect BSON structure
val bson = CodecTestKit.toBsonDocument(instance)
println(bson.toJson())
```

## Next Steps

- 📖 [Feature Overview](FEATURES.md) - Complete feature guide
- 🔧 [Custom Codecs](ADVANCED.md) - Implementing custom type support
- ❓ [FAQ](FAQ.md) - Type-related troubleshooting

