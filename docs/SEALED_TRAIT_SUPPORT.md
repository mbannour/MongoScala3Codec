# Sealed Trait Support in MongoScala3Codec

## Overview

MongoScala3Codec now provides **automatic codec generation for sealed traits and classes** using Scala 3 metaprogramming. This feature enables polymorphic serialization with automatic discriminator field handling.

## Quick Start

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder
import org.mongodb.scala.MongoClient

// Define your sealed hierarchy
sealed trait Animal
case class Dog(name: String, breed: String) extends Animal
case class Cat(name: String, lives: Int) extends Animal
case class Bird(name: String, canFly: Boolean) extends Animal

// Register with a single call
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Animal]  // Registers Animal + all case class subtypes
  .build

// Use polymorphically
val animals: List[Animal] = List(
  Dog("Rex", "Labrador"),
  Cat("Whiskers", 9),
  Bird("Tweety", true)
)

// Encodes to BSON with automatic discriminator:
// {"_type": "Dog", "name": "Rex", "breed": "Labrador"}
// {"_type": "Cat", "name": "Whiskers", "lives": 9}
// {"_type": "Bird", "name": "Tweety", "canFly": true}
```

## Features

### ✅ Automatic Discriminator Field

The codec automatically adds a discriminator field (default: `_type`) during encoding and uses it during decoding to determine the concrete type.

```scala
sealed trait Status
case class Active(since: Long) extends Status
case class Inactive(reason: String) extends Status

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY).registerSealed[Status].build
val codec = registry.get(classOf[Status])

// Encodes with discriminator
val status: Status = Active(System.currentTimeMillis())
// BSON: {"_type": "Active", "since": 1234567890}
```

### ✅ Single Registration Call

One call to `registerSealed[T]` automatically registers:
- The sealed trait/class itself
- All concrete case class subtypes
- Proper codecs for polymorphic usage

```scala
// Before (manual discriminator):
val registry = builder
  .register[Circle]      // Manual registration
  .register[Rectangle]   // Manual registration
  .register[Triangle]    // Manual registration
  .build

// After (automatic):
val registry = builder
  .registerSealed[Shape]  // Registers all at once!
  .build
```

### ✅ Batch Registration with `registerSealedAll`

Register multiple sealed traits efficiently with a single call:

```scala
sealed trait Animal
case class Dog(name: String) extends Animal
case class Cat(name: String) extends Animal

sealed trait Vehicle
case class Car(make: String) extends Vehicle
case class Bike(brand: String) extends Vehicle

sealed trait Status
case class Active(since: Long) extends Status
case class Inactive(reason: String) extends Status

// Register all three sealed traits at once - more efficient!
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealedAll[(Animal, Vehicle, Status)]
  .build
```

**Performance benefit:** `registerSealedAll` builds the temporary registry only once for all sealed traits, making it significantly faster than chaining multiple `registerSealed` calls.

### ✅ Nested Sealed Traits

Sealed traits can be used as fields in case classes:

```scala
sealed trait PaymentMethod
case class CreditCard(number: String) extends PaymentMethod
case class BankTransfer(account: String) extends PaymentMethod

case class Order(_id: ObjectId, amount: Double, payment: PaymentMethod)

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[PaymentMethod]
  .register[Order]
  .build

// Works perfectly!
val order = Order(new ObjectId(), 99.99, CreditCard("1234"))
```

### ✅ Collections of Sealed Types

Works seamlessly with List, Vector, Set, etc.:

```scala
case class Zoo(name: String, animals: List[Animal])

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Animal]
  .register[Zoo]
  .build

val zoo = Zoo("City Zoo", List(
  Dog("Max", "Beagle"),
  Cat("Luna", 3),
  Bird("Polly", true)
))

// Round-trips perfectly with discriminators
```

### ✅ Custom Discriminator Field

Configure the discriminator field name:

```scala
val config = CodecConfig(discriminatorField = "_class")

val registry = baseRegistry
  .withConfig(config)
  .registerSealed[Animal]
  .build

// Uses "_class" instead of "_type"
// BSON: {"_class": "Dog", "name": "Rex", ...}
```

### ✅ Multi-Level Hierarchies

Supports nested sealed hierarchies:

```scala
sealed trait Vehicle
sealed trait Motorized extends Vehicle
case class Car(make: String) extends Motorized
case class Motorcycle(cc: Int) extends Motorized
case class Bicycle(gears: Int) extends Vehicle

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[Vehicle]  // Registers all types in hierarchy
  .build
```

## Supported Types

### ✅ Sealed Trait
```scala
sealed trait MyTrait
case class SubType1(...) extends MyTrait
case class SubType2(...) extends MyTrait
```

### ✅ Sealed Class
```scala
sealed class MyClass
case class SubType1(...) extends MyClass
case class SubType2(...) extends MyClass
```

### ✅ Sealed Abstract Class
```scala
sealed abstract class MyAbstractClass
case class SubType1(...) extends MyAbstractClass
case class SubType2(...) extends MyAbstractClass
```

### ❌ Case Objects (Not Supported)

Case objects are NOT supported in sealed hierarchies. Use Scala 3 enums instead:

```scala
// ❌ NOT SUPPORTED
sealed trait Status
case object Active extends Status
case object Inactive extends Status

// ✅ USE THIS INSTEAD
enum Status:
  case Active, Inactive
```

## Migration from Manual Discriminators

### Before (Manual Pattern)

```scala
sealed trait Shape
case class Circle(radius: Double, shapeType: String = "Circle") extends Shape
case class Rectangle(width: Double, height: Double, shapeType: String = "Rectangle") extends Shape

val registry = builder
  .register[Circle]
  .register[Rectangle]
  .build
```

**Problems:**
- Manual discriminator field clutters case classes
- Must register each subtype individually
- Cannot use sealed trait polymorphically
- Discriminator is part of the data model

### After (registerSealed)

```scala
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

val registry = builder
  .registerSealed[Shape]  // Done!
  .build
```

**Benefits:**
- ✅ Clean case classes (no discriminator field)
- ✅ Single registration call
- ✅ Full polymorphic support
- ✅ Transparent discriminator handling

## Implementation Details

### Discriminator Strategy

**Default:** `SimpleName`
- Uses the simple class name (e.g., "Dog", "Cat")
- Configurable via `CodecConfig.discriminatorField`

**Future:** `FullyQualifiedName` support planned
- For handling name collisions across packages

### Encoding Process

1. Codec writes `writeStartDocument()`
2. Codec writes discriminator field: `writer.writeString("_type", "Dog")`
3. Codec delegates to concrete case class codec for field writing
4. Codec writes `writeEndDocument()`

### Decoding Process

1. Codec uses mark/reset to peek at discriminator field
2. Codec reads discriminator value (e.g., "Dog")
3. Codec looks up concrete class from discriminator map
4. Codec resets reader and delegates to concrete codec
5. Concrete codec decodes the full document (skipping discriminator)

## Examples

See the test files for comprehensive examples:
- `SealedTraitCodecSpec.scala` - Basic functionality tests
- `SealedTraitIntegrationSpec.scala` - Integration tests with property-based testing

## API Reference

### registerSealed[T]

```scala
inline def registerSealed[T: ClassTag]: RegistryBuilder
```

Registers a sealed trait/class and all its concrete case class subtypes.

**Parameters:**
- `T` - The sealed trait, class, or abstract class type

**Returns:**
- Updated `RegistryBuilder` with registered codecs

**Example:**
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealed[MySealedType]
  .build
```

### registerSealedAll[T <: Tuple]

```scala
inline def registerSealedAll[T <: Tuple]: RegistryBuilder
```

Registers multiple sealed traits/classes efficiently in a single call. More performant than chaining multiple `registerSealed` calls as it builds the temporary registry only once.

**Parameters:**
- `T` - A tuple of sealed trait, class, or abstract class types

**Returns:**
- Updated `RegistryBuilder` with registered codecs for all types

**Example:**
```scala
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .registerSealedAll[(Animal, Vehicle, Status)]
  .build
```

**Performance:**
- ✅ Single temporary registry build (vs N builds for N types)
- ✅ Batch processing of all sealed hierarchies
- ✅ Optimal for registering 2+ sealed traits

### CodecConfig

```scala
case class CodecConfig(
  noneHandling: NoneHandling = NoneHandling.Encode,
  discriminatorField: String = "_type",
  discriminatorStrategy: DiscriminatorStrategy = DiscriminatorStrategy.SimpleName
)
```

**Fields:**
- `discriminatorField` - Name of the BSON field storing the type discriminator
- `discriminatorStrategy` - Strategy for generating discriminator values (currently only SimpleName)

## Limitations

1. **Case Objects Not Supported**
   - Only case classes are supported as sealed subtypes
   - Use Scala 3 enums for simple enumerations

2. **Type Parameters**
   - Sealed traits with type parameters are not currently supported
   - Planned for future release

3. **Compile-Time Only**
   - Subclass discovery happens at compile time
   - Runtime-added subtypes are not supported

## Performance

- ✅ Compile-time code generation (no runtime reflection)
- ✅ Efficient discriminator reading with mark/reset
- ✅ Cached codec lookups
- ✅ Zero overhead compared to manual discriminators

## Comparison with mongo-scala-driver

This implementation mirrors the Scala 2 macro approach from mongo-scala-driver:

| Feature | mongo-scala-driver | MongoScala3Codec |
|---------|-------------------|------------------|
| Discriminator field | `_t` (hardcoded) | `_type` (configurable) |
| Registration | `Macros.createCodecProvider` | `registerSealed[T]` |
| Case objects | ❌ Not supported | ❌ Not supported |
| Scala version | Scala 2 macros | Scala 3 metaprogramming |
| Mark/reset pattern | ✅ Used | ✅ Used |
| Subclass discovery | Compile-time | Compile-time |

## Troubleshooting

### "Cannot generate codec for sealed trait"
- Make sure you're using `registerSealed[T]` not `register[T]`
- Verify `T` is actually sealed (`sealed trait`, `sealed class`, or `sealed abstract class`)

### "No case class subclasses found"
- Ensure at least one case class extends the sealed type
- Case objects are not supported - use case classes

### "Unknown discriminator value"
- Discriminator in BSON doesn't match any registered subtype
- Check that all subtypes are case classes extending the sealed type

### StackOverflowError
- May indicate circular codec dependencies
- Ensure subclass codecs are registered properly

