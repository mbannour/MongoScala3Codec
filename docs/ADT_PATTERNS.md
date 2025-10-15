# Advanced ADT Patterns and Discriminator Strategies

Complete guide to sealed traits, enums, and algebraic data types (ADTs) in MongoScala3Codec.

## Table of Contents

- [Sealed Trait Basics](#sealed-trait-basics)
- [Discriminator Strategies](#discriminator-strategies)
- [Enum Support](#enum-support)
- [Complex ADT Patterns](#complex-adt-patterns)
- [Validation Patterns](#validation-patterns)
- [Best Practices](#best-practices)

---

## Sealed Trait Basics

### Current Implementation

MongoScala3Codec currently supports sealed traits through **concrete case class registration**. Each case class in a sealed hierarchy is registered independently.

```scala
sealed trait Vehicle
case class Car(_id: ObjectId, brand: String, doors: Int) extends Vehicle
case class Motorcycle(_id: ObjectId, brand: String, cc: Int) extends Vehicle
case class Truck(_id: ObjectId, brand: String, capacity: Double) extends Vehicle

// Register each concrete type
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Car]
  .register[Motorcycle]
  .register[Truck]
  .build

// Use type-specific collections
val carCollection: MongoCollection[Car] = 
  database.getCollection[Car]("vehicles")

val motorcycleCollection: MongoCollection[Motorcycle] = 
  database.getCollection[Motorcycle]("vehicles")
```

**Benefits:**
- ✅ Type-safe collections per concrete type
- ✅ No discriminator overhead if not needed
- ✅ Clear separation of concerns

**Limitations:**
- ⚠️ Cannot store polymorphic sealed trait fields directly
- ⚠️ Requires separate collections or manual discriminator management

---

## Discriminator Strategies

### Manual Discriminator Pattern

Add a discriminator field to your case classes for manual polymorphic querying:

```scala
sealed trait Payment
case class CreditCard(
  _id: ObjectId,
  _type: String = "CreditCard",  // Manual discriminator
  cardNumber: String,
  expiry: String
) extends Payment

case class BankTransfer(
  _id: ObjectId,
  _type: String = "BankTransfer",  // Manual discriminator
  accountNumber: String,
  routingNumber: String
) extends Payment

case class Cash(
  _id: ObjectId,
  _type: String = "Cash",  // Manual discriminator
  amount: Double
) extends Payment

// Register all types
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[CreditCard]
  .register[BankTransfer]
  .register[Cash]
  .build
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "_type": "CreditCard",
  "cardNumber": "1234-5678-9012-3456",
  "expiry": "12/25"
}
```

**Querying by Type:**
```scala
import org.mongodb.scala.model.Filters

// Query all credit card payments
val creditCards = database
  .getCollection[CreditCard]("payments")
  .withCodecRegistry(registry)
  .find(Filters.eq("_type", "CreditCard"))
  .toFuture()

// Query all payments (any type) as documents
val allPayments = database
  .getCollection[Document]("payments")
  .find()
  .toFuture()
```

### Custom Discriminator Field Names

Configure discriminator field names via case class design:

```scala
sealed trait Event
case class UserEvent(
  _id: ObjectId,
  eventType: String = "user",  // Custom field name
  userId: String,
  action: String
) extends Event

case class SystemEvent(
  _id: ObjectId,
  eventType: String = "system",  // Custom field name
  level: String,
  message: String
) extends Event
```

**Benefits:**
- Custom field naming for compatibility
- Domain-specific discriminators
- Flexible querying strategies

---

## Enum Support

### Simple Enums (String-Based)

```scala
enum Priority:
  case Low, Medium, High

case class Task(_id: ObjectId, title: String, priority: Priority)

import io.github.mbannour.mongo.codecs.EnumValueCodecProvider

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider[Priority]())
  .register[Task]
  .build
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "title": "Complete report",
  "priority": "High"
}
```

### Ordinal-Based Enums

```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(EnumValueCodecProvider[Priority](useOrdinal = true))
  .register[Task]
  .build
```

**BSON Storage:**
```json
{
  "_id": {"$oid": "..."},
  "title": "Complete report",
  "priority": 2
}
```

### Enum Best Practices

✅ **DO:**
- Use string-based for human readability
- Use ordinal-based for storage efficiency
- Document enum values in code comments

❌ **DON'T:**
- Add parameters to enums (use sealed traits instead)
- Change enum order after deployment (breaks ordinal mapping)
- Mix enum encoding strategies in the same collection

---

## Complex ADT Patterns

### Nested ADTs

```scala
// Inner ADT
sealed trait Color
case object Red extends Color
case object Green extends Color
case object Blue extends Color

// Outer case class with ADT field
case class Product(
  _id: ObjectId,
  name: String,
  color: String  // Store color as String instead of sealed trait
)

// Helper methods for conversion
object Product:
  def fromColor(color: Color): String = color match
    case Red   => "red"
    case Green => "green"
    case Blue  => "blue"
  
  def toColor(str: String): Color = str match
    case "red"   => Red
    case "green" => Green
    case "blue"  => Blue
    case _       => throw new IllegalArgumentException(s"Unknown color: $str")
```

### Event Sourcing Pattern

```scala
sealed trait EventData
case class UserRegistered(email: String, timestamp: Long)
case class UserLoggedIn(sessionId: String, timestamp: Long)
case class UserLoggedOut(sessionId: String, timestamp: Long)

case class Event(
  _id: ObjectId,
  userId: String,
  eventType: String,
  data: String  // Store as JSON string
)

// Serialize/deserialize event data
import io.circe.syntax.*
import io.circe.parser.*

object Event:
  def create(userId: String, data: EventData): Event =
    val (eventType, json) = data match
      case ur: UserRegistered => ("UserRegistered", ur.asJson.noSpaces)
      case ul: UserLoggedIn   => ("UserLoggedIn", ul.asJson.noSpaces)
      case uo: UserLoggedOut  => ("UserLoggedOut", uo.asJson.noSpaces)
    
    Event(new ObjectId(), userId, eventType, json)
```

### Type-Safe Wrapper Pattern

```scala
sealed trait EntityType
case object UserEntity extends EntityType
case object OrderEntity extends EntityType
case object ProductEntity extends EntityType

case class TypedDocument(
  _id: ObjectId,
  entityType: String,  // "user", "order", "product"
  data: Map[String, Any]  // Flexible data storage
)

// Type-safe accessors
object TypedDocument:
  def forUser(data: Map[String, Any]): TypedDocument =
    TypedDocument(new ObjectId(), "user", data)
  
  def forOrder(data: Map[String, Any]): TypedDocument =
    TypedDocument(new ObjectId(), "order", data)
  
  def isUser(doc: TypedDocument): Boolean =
    doc.entityType == "user"
```

---

## Validation Patterns

### Decode-Time Validation

```scala
case class Email(value: String):
  require(value.contains("@"), "Invalid email format")
  require(value.length <= 255, "Email too long")

case class User(_id: ObjectId, name: String, email: Email)

// Validation happens automatically during decode
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Email]
  .register[User]
  .build

// If invalid email in DB, decode throws IllegalArgumentException
```

### Smart Constructors with Validation

```scala
case class ValidatedUser private (
  _id: ObjectId,
  name: String,
  age: Int,
  email: String
)

object ValidatedUser:
  def apply(
    _id: ObjectId,
    name: String,
    age: Int,
    email: String
  ): Either[String, ValidatedUser] =
    for
      validName  <- validateName(name)
      validAge   <- validateAge(age)
      validEmail <- validateEmail(email)
    yield new ValidatedUser(_id, validName, validAge, validEmail)
  
  private def validateName(name: String): Either[String, String] =
    if name.trim.isEmpty then Left("Name cannot be empty")
    else if name.length > 100 then Left("Name too long")
    else Right(name)
  
  private def validateAge(age: Int): Either[String, Int] =
    if age < 0 then Left("Age cannot be negative")
    else if age > 150 then Left("Age unrealistic")
    else Right(age)
  
  private def validateEmail(email: String): Either[String, String] =
    if !email.contains("@") then Left("Invalid email")
    else Right(email)
```

### Custom Validation Hook Pattern

```scala
trait Validatable[T]:
  def validate(value: T): Either[String, T]

case class Order(_id: ObjectId, total: Double, items: List[String]):
  def validate: Either[String, Order] =
    for
      _ <- validateTotal
      _ <- validateItems
    yield this
  
  private def validateTotal: Either[String, Unit] =
    if total < 0 then Left("Total cannot be negative")
    else if total > 1000000 then Left("Total exceeds maximum")
    else Right(())
  
  private def validateItems: Either[String, Unit] =
    if items.isEmpty then Left("Order must have at least one item")
    else Right(())

// Use after decoding
val order = collection.find().first().head()
order.validate match
  case Right(validOrder) => processOrder(validOrder)
  case Left(error)       => handleError(error)
```

### Refined Types Pattern

```scala
opaque type PositiveInt = Int
object PositiveInt:
  def apply(value: Int): Either[String, PositiveInt] =
    if value > 0 then Right(value)
    else Left(s"Value must be positive, got: $value")
  
  def unsafe(value: Int): PositiveInt = value
  
  extension (n: PositiveInt)
    def value: Int = n

opaque type NonEmptyString = String
object NonEmptyString:
  def apply(value: String): Either[String, NonEmptyString] =
    if value.trim.nonEmpty then Right(value)
    else Left("String cannot be empty")
  
  def unsafe(value: String): NonEmptyString = value
  
  extension (s: NonEmptyString)
    def value: String = s

case class ValidatedProduct(
  _id: ObjectId,
  name: NonEmptyString,
  quantity: PositiveInt
)
```

### Validation in RegistryBuilder Extension

```scala
extension (builder: RegistryBuilder)
  def registerWithValidation[T](validate: T => Either[String, T])(using ClassTag[T], CodecConfig): RegistryBuilder =
    val baseCodec = builder.register[T].build.get(ClassTag[T].runtimeClass.asInstanceOf[Class[T]])
    
    val validatingCodec = new Codec[T]:
      override def encode(w: BsonWriter, v: T, ctx: EncoderContext): Unit =
        validate(v) match
          case Right(valid) => baseCodec.encode(w, valid, ctx)
          case Left(error)  => throw new IllegalArgumentException(s"Validation failed: $error")
      
      override def decode(r: BsonReader, ctx: DecoderContext): T =
        val decoded = baseCodec.decode(r, ctx)
        validate(decoded) match
          case Right(valid) => valid
          case Left(error)  => throw new IllegalArgumentException(s"Validation failed: $error")
      
      override def getEncoderClass: Class[T] = baseCodec.getEncoderClass
    
    builder.withCodec(validatingCodec)
```

---

## Best Practices

### Design Guidelines

✅ **DO:**
1. **Use concrete case classes** for sealed trait members
2. **Add discriminator fields** manually if you need polymorphic queries
3. **Keep sealed hierarchies shallow** (1-2 levels deep)
4. **Document discriminator values** in code comments
5. **Use enums for simple value sets** (< 10 values)
6. **Validate at domain boundaries** (API input, database decode)

❌ **DON'T:**
1. **Don't use polymorphic sealed trait fields** (not yet supported)
2. **Don't nest ADTs deeply** (makes querying difficult)
3. **Don't change enum order** after deployment
4. **Don't validate repeatedly** (performance impact)
5. **Don't mix discriminator strategies** in same collection

### Performance Considerations

| Pattern | Encoding Cost | Decoding Cost | Storage Overhead |
|---------|---------------|---------------|------------------|
| Concrete case class | O(fields) | O(fields) | None |
| Manual discriminator | O(fields) + O(1) | O(fields) + O(1) | +1 field |
| String enum | O(1) | O(1) | String length |
| Ordinal enum | O(1) | O(1) | 4 bytes |
| Validation | O(fields) × 2 | O(fields) × 2 | None |

### Testing ADT Codecs

```scala
import io.github.mbannour.mongo.codecs.CodecTestKit
import org.scalatest.flatspec.AnyFlatSpec

class PaymentCodecSpec extends AnyFlatSpec:
  
  sealed trait Payment
  case class CreditCard(_id: ObjectId, cardNumber: String) extends Payment
  case class Cash(_id: ObjectId, amount: Double) extends Payment
  
  val registry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .register[CreditCard]
    .register[Cash]
    .build
  
  "CreditCard codec" should "round-trip correctly" in {
    given codec: Codec[CreditCard] = registry.get(classOf[CreditCard])
    
    val cc = CreditCard(new ObjectId(), "1234-5678-9012-3456")
    CodecTestKit.assertCodecSymmetry(cc)
  }
  
  "Cash codec" should "round-trip correctly" in {
    given codec: Codec[Cash] = registry.get(classOf[Cash])
    
    val cash = Cash(new ObjectId(), 100.50)
    CodecTestKit.assertCodecSymmetry(cash)
  }
  
  "Discriminator field" should "be present in BSON" in {
    case class Tagged(_id: ObjectId, _type: String = "CreditCard", data: String)
    given codec: Codec[Tagged] = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[Tagged]
      .build
      .get(classOf[Tagged])
    
    val tagged = Tagged(new ObjectId(), data = "test")
    val bson = CodecTestKit.toBsonDocument(tagged)
    
    assert(bson.containsKey("_type"))
    assert(bson.getString("_type").getValue == "CreditCard")
  }
```

---

## Migration from Polymorphic ADTs

If you have existing code expecting polymorphic sealed trait support:

### Before (Not Supported)
```scala
sealed trait Status
case class Active(since: Long) extends Status
case class Inactive(reason: String) extends Status

case class User(_id: ObjectId, status: Status)  // ❌ Not supported
```

### After (Supported Pattern 1: Store as String)
```scala
sealed trait Status
case class Active(since: Long) extends Status
case class Inactive(reason: String) extends Status

case class User(_id: ObjectId, statusType: String, statusData: String)

object User:
  def withStatus(id: ObjectId, status: Status): User =
    val (statusType, statusData) = status match
      case Active(since) => ("active", since.toString)
      case Inactive(reason) => ("inactive", reason)
    User(id, statusType, statusData)
```

### After (Supported Pattern 2: Separate Collections)
```scala
case class ActiveUser(_id: ObjectId, since: Long)
case class InactiveUser(_id: ObjectId, reason: String)

// Use separate collections or type-specific queries
val activeUsers = database.getCollection[ActiveUser]("users_active")
val inactiveUsers = database.getCollection[InactiveUser]("users_inactive")
```

---

## Next Steps

- 📖 [BSON Type Mapping](BSON_TYPE_MAPPING.md) - Complete type reference
- 🧪 [Testing Guide](TESTING.md) - Testing ADT codecs
- ❓ [FAQ](FAQ.md) - ADT troubleshooting

