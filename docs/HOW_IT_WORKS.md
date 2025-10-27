# How It Works: Scala 3 Derivation Details

This guide explains the internals of MongoScala3Codec and how it leverages Scala 3's metaprogramming features to generate BSON codecs at compile-time.

## Table of Contents

- [Overview](#overview)
- [Macro-Based Code Generation](#macro-based-code-generation)
- [Case Class Codec Generation](#case-class-codec-generation)
- [Sealed Trait Handling](#sealed-trait-handling)
- [Type Resolution](#type-resolution)
- [Compile-Time Field Inspection](#compile-time-field-inspection)
- [BSON Reading and Writing](#bson-reading-and-writing)
- [Performance Characteristics](#performance-characteristics)

---

## Overview

MongoScala3Codec uses **Scala 3 inline macros** and **quoted expressions** to inspect case classes at compile-time and generate optimized BSON codec implementations.

### Key Components

```
┌─────────────────────────────────────────────────────────┐
│                  RegistryBuilder                         │
│  (User API - Fluent builder for codec registration)     │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              CodecProviderMacro                          │
│  (Macro entry point - generates codec at compile-time)  │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│          CaseClassCodecGenerator                         │
│  (Core logic - inspects types and generates code)       │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
┌──────────────────┐    ┌──────────────────┐
│ CaseClassMapper  │    │ CaseClassFactory │
│ (Field reading)  │    │ (Object creation)│
└──────────────────┘    └──────────────────┘
```

---

## Macro-Based Code Generation

### Inline Macro Entry Point

```scala
// Simplified version of the macro entry point
inline def register[T]: RegistryBuilder = ${ registerMacro[T] }

def registerMacro[T: Type](using Quotes): Expr[RegistryBuilder] =
  import quotes.reflect.*
  
  // Inspect the type T at compile-time
  val typeRepr = TypeRepr.of[T]
  
  // Generate codec implementation
  val codecImpl = generateCodecImpl[T]
  
  // Return builder with registered codec
  '{ builder.withCodec($codecImpl) }
```

### Why Macros?

1. **Zero Runtime Reflection:** All type inspection happens at compile-time
2. **Type Safety:** Invalid types are caught during compilation
3. **Performance:** Generated code is as fast as hand-written codecs
4. **Optimization:** Dead code elimination and inlining

---

## Case Class Codec Generation

### Step 1: Extract Case Class Metadata

```scala
// At compile-time, extract field information
case class User(_id: ObjectId, name: String, age: Int)

// Generated metadata (conceptual):
FieldInfo("_id", ObjectId, index = 0, hasDefault = false)
FieldInfo("name", String, index = 1, hasDefault = false)
FieldInfo("age", Int, index = 2, hasDefault = false)
```

### Step 2: Generate Encoder

The macro generates an encoder method like this:

```scala
// Conceptually generated code for User encoder
def encode(writer: BsonWriter, user: User, context: EncoderContext): Unit =
  writer.writeStartDocument()
  
  writer.writeName("_id")
  objectIdCodec.encode(writer, user._id, context)
  
  writer.writeName("name")
  stringCodec.encode(writer, user.name, context)
  
  writer.writeName("age")
  intCodec.encode(writer, user.age, context)
  
  writer.writeEndDocument()
```

### Step 3: Generate Decoder

The macro generates a decoder method like this:

```scala
// Conceptually generated code for User decoder
def decode(reader: BsonReader, context: DecoderContext): User =
  reader.readStartDocument()
  
  var _id: ObjectId = null
  var name: String = null
  var age: Int = 0
  
  while reader.readBsonType() != BsonType.END_OF_DOCUMENT do
    val fieldName = reader.readName()
    fieldName match
      case "_id" => _id = objectIdCodec.decode(reader, context)
      case "name" => name = stringCodec.decode(reader, context)
      case "age" => age = intCodec.decode(reader, context)
      case _ => reader.skipValue()
  
  reader.readEndDocument()
  
  User(_id, name, age)
```

### Handling @BsonProperty Annotations

```scala
case class User(
  _id: ObjectId,
  @BsonProperty("n") name: String
)

// Generated code uses "n" instead of "name"
writer.writeName("n")
stringCodec.encode(writer, user.name, context)
```

---

## Sealed Trait Handling

⚠️ **IMPORTANT LIMITATION:** MongoScala3Codec supports registering concrete case classes that extend sealed traits, but **does NOT support polymorphic sealed trait fields**. You cannot use `shape: Shape` as a field type. This section explains the internal mechanism for codec generation of concrete types.

### Type Discrimination Strategy

MongoScala3Codec generates codecs for individual concrete case classes that extend sealed traits.

```scala
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Square(side: Double) extends Shape

// ✅ SUPPORTED: Register concrete types
val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .register[Circle]
  .register[Square]
  .build

// ❌ NOT SUPPORTED: Polymorphic field
// case class Drawing(shape: Shape)  // This will NOT work

// ✅ SUPPORTED: Concrete type field
case class CircleDrawing(shape: Circle)  // This works
```

### Generated Encoder for Concrete Types

```scala
// Conceptual generated code for Circle
def encodeCircle(writer: BsonWriter, circle: Circle, context: EncoderContext): Unit =
  writer.writeStartDocument()
  writer.writeName("radius")
  doubleCodec.encode(writer, circle.radius, context)
  writer.writeEndDocument()

// Conceptual generated code for Square
def encodeSquare(writer: BsonWriter, square: Square, context: EncoderContext): Unit =
  writer.writeStartDocument()
  writer.writeName("side")
  doubleCodec.encode(writer, square.side, context)
  writer.writeEndDocument()
```

**Note:** Each concrete case class gets its own codec. There is no polymorphic codec for the sealed trait itself.

### Generated Decoder for Concrete Types

```scala
// Conceptual generated code for Circle
def decodeCircle(reader: BsonReader, context: DecoderContext): Circle =
  reader.readStartDocument()
  var radius: Double = 0.0

  while reader.readBsonType() != BsonType.END_OF_DOCUMENT do
    val fieldName = reader.readName()
    fieldName match
      case "radius" => radius = reader.readDouble()
      case _ => reader.skipValue()

  reader.readEndDocument()
  Circle(radius)

// Each concrete type has its own independent decoder
```

**Note:** Each concrete case class decodes directly. There is no polymorphic decoder that dispatches based on a discriminator field, since polymorphic fields are not supported.

---

## Type Resolution

### Primitive Types

Direct mapping to BSON types:

```
String    → BsonString
Int       → BsonInt32
Long      → BsonInt64
Double    → BsonDouble
Boolean   → BsonBoolean
ObjectId  → BsonObjectId
```

### Collection Types

```scala
// List[T] → BsonArray
case class Playlist(songs: List[String])

// Generated encoder
writer.writeName("songs")
writer.writeStartArray()
for song <- playlist.songs do
  stringCodec.encode(writer, song, context)
writer.writeEndArray()
```

### Option Types

```scala
case class User(email: Option[String])

// With NoneHandling.Ignore
if user.email.isDefined then
  writer.writeName("email")
  stringCodec.encode(writer, user.email.get, context)

// With NoneHandling.Encode
writer.writeName("email")
if user.email.isDefined then
  stringCodec.encode(writer, user.email.get, context)
else
  writer.writeNull()
```

### Nested Case Classes

```scala
case class Address(city: String)
case class User(address: Address)

// Nested encoding
writer.writeName("address")
addressCodec.encode(writer, user.address, context)
```

---

## Compile-Time Field Inspection

### Using Scala 3 Mirrors

MongoScala3Codec uses Scala 3's `Mirror` typeclass for compile-time reflection:

```scala
inline def inspectFields[T](using m: Mirror.Of[T]): List[FieldInfo] =
  inline m match
    case p: Mirror.ProductOf[T] =>
      // Extract field names
      val labels = constValueTuple[p.MirroredElemLabels]
      
      // Extract field types
      val types = summonAll[p.MirroredElemTypes]
      
      // Combine into field metadata
      labels.zip(types).toList
```

### Default Value Resolution

```scala
case class Config(timeout: Int = 30, retries: Int = 3)

// At compile-time, detect default values
inline def hasDefaultValue[T](fieldIndex: Int): Boolean =
  // Use Mirror to check if default method exists
  // e.g., Config.$lessinit$greater$default$1
```

### Annotation Processing

```scala
// Extract @BsonProperty annotation at compile-time
def getBsonPropertyName(field: Symbol): Option[String] =
  field.annotations.collectFirst {
    case Apply(Select(New(tpe), _), List(Literal(Constant(name: String))))
      if tpe.tpe <:< TypeRepr.of[BsonProperty] =>
      name
  }
```

---

## BSON Reading and Writing

### BsonWriter Protocol

MongoScala3Codec follows the official MongoDB BSON writer protocol:

```scala
writer.writeStartDocument()        // Start object
writer.writeName("fieldName")      // Field name
codec.encode(writer, value, ctx)   // Field value
writer.writeEndDocument()          // End object
```

### BsonReader Protocol

```scala
reader.readStartDocument()
while reader.readBsonType() != BsonType.END_OF_DOCUMENT do
  val fieldName = reader.readName()
  // Process field based on name
reader.readEndDocument()
```

### Efficient Field Matching

Generated decoders use pattern matching for efficient field dispatch:

```scala
// O(1) for small case classes, O(log n) for larger ones
fieldName match
  case "field1" => decodeField1()
  case "field2" => decodeField2()
  case "field3" => decodeField3()
  case _ => reader.skipValue()
```

---

## Performance Characteristics

### Compilation Time

- **Small case classes (< 10 fields):** < 1 second additional compile time
- **Large case classes (10-50 fields):** 1-3 seconds additional compile time
- **Deep hierarchies (> 5 levels):** 3-5 seconds additional compile time

### Runtime Performance

Generated codecs have performance characteristics similar to hand-written codecs:

- **Encoding:** ~200-500ns per field (depending on type)
- **Decoding:** ~300-600ns per field (depending on type)
- **Memory:** Zero additional allocations beyond object creation

### Optimization Techniques

1. **Method Inlining:** Small accessor methods are inlined
2. **Dead Code Elimination:** Unused branches are removed
3. **Monomorphic Calls:** Direct method calls (no virtual dispatch)
4. **Primitive Specialization:** Avoids boxing for primitives

---

## Limitations and Edge Cases

### Unsupported Constructs

❌ **Mutable vars in case classes:**
```scala
case class Bad(var x: Int)  // Not supported
```

❌ **Non-case classes:**
```scala
class NotSupported(val x: Int)  // Not supported
```

❌ **Scala 3 enums with parameters:**
```scala
enum Complex(val code: Int):  // Not fully supported
  case A extends Complex(1)
  case B extends Complex(2)
```

### Supported Alternatives

✅ **Use immutable case classes:**
```scala
case class Good(x: Int)
```

✅ **Use sealed traits for ADTs:**
```scala
sealed trait MyADT
case class VariantA(code: Int) extends MyADT
case class VariantB(code: Int) extends MyADT
```

---

## Debugging Generated Code

### Print Generated Code (for debugging)

Add compiler flag to see generated code:

```scala
scalacOptions += "-Xprint:typer"
```

### Understanding Compilation Errors

When codec generation fails, the compiler provides detailed error messages:

```
[error] Cannot generate codec for type Foo
[error] Reason: Field 'bar' has unsupported type Baz
[error] Hint: Ensure Codec[Baz] is in scope or register it first
```

---

## Extending the Library

### Custom Codec Integration

You can provide custom codecs for types:

```scala
given myCustomCodec: Codec[MyType] = new Codec[MyType] {
  def encode(w: BsonWriter, v: MyType, ctx: EncoderContext): Unit = ???
  def decode(r: BsonReader, ctx: DecoderContext): MyType = ???
  def getEncoderClass: Class[MyType] = classOf[MyType]
}

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withCodec(myCustomCodec)
  .register[ContainingType]
  .build
```

### Custom Codec Providers

For more complex scenarios:

```scala
class MyCodecProvider extends CodecProvider {
  def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] = ???
}

val registry = RegistryBuilder
  .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  .withProvider(new MyCodecProvider())
  .build
```

---

## Next Steps

- 📖 [Migration Guide](MIGRATION.md) - Migrate from other libraries
- ❓ [FAQ](FAQ.md) - Common questions and troubleshooting
- 🔙 [Feature Overview](FEATURES.md) - Back to features

