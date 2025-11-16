# BigDecimal Codec Fix - Summary

## Problem
You were getting this error:
```
java.lang.IllegalArgumentException: No codec found for type: scala.math.BigDecimal
```

## Root Cause
The MongoDB Java driver's `DEFAULT_CODEC_REGISTRY` includes codecs for Java types (`java.math.BigDecimal`), but **not** for Scala standard library types (`scala.math.BigDecimal`). Even though the documentation mentioned support for BigDecimal, the codec wasn't being registered automatically.

## Solution
Created `ScalaCodecs.scala` which provides codecs for Scala standard library types:
- ✅ `BigDecimal` → BSON Decimal128
- ✅ `BigInt` → BSON String (for arbitrary precision)

## Files Added
1. `src/main/scala/io/github/mbannour/mongo/codecs/ScalaCodecs.scala` - The codec implementations
2. `src/test/scala/io/github/mbannour/mongo/codecs/ScalaCodecsSpec.scala` - Comprehensive tests
3. `SCALA_CODECS_USAGE.md` - Usage documentation

## How to Fix Your EmployeeCrudExample

### Before (Failing Code)
```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder

case class Employee(
  _id: ObjectId,
  name: String,
  salary: BigDecimal  // ❌ This causes: No codec found for type: scala.math.BigDecimal
)

val registry = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .register[Employee]  // ❌ Fails because BigDecimal codec is missing
  .build
```

### After (Working Code)

```scala
import io.github.mbannour.mongo.codecs.RegistryBuilder

case class Employee(
                     _id: ObjectId,
                     name: String,
                     salary: BigDecimal // ✅ Works with ScalaCodecs.bigDecimalCodec
                   )

val registry = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .withCodec(ScalaCodecs.bigDecimalCodec) // ✅ Add BigDecimal codec
  .register[Employee]
  .build
```

Or register all Scala codecs at once:
```scala
val registry = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .withCodecs(ScalaCodecs.all*)  // ✅ Add all Scala type codecs
  .register[Employee]
  .build
```

## Quick Fix for Your Code

Just add this import and one line to your registry builder:

```scala
// Add this import

val registry = MongoClient.DEFAULT_CODEC_REGISTRY
  .newBuilder
  .withCodecs(ScalaCodecs.all*)  // Add this line
  .register[YourCaseClass]
  // ... rest of your registrations
  .build
```

## Testing

All 315 tests pass, including 7 new tests specifically for ScalaCodecs:
- ✅ BigDecimal encoding/decoding
- ✅ BigInt encoding/decoding
- ✅ Large precision values
- ✅ Nested case classes with BigDecimal
- ✅ Collections of BigDecimal/BigInt
- ✅ Optional BigDecimal/BigInt values

## Additional Types

If you need support for other Scala types that are missing codecs, you can easily add them to `ScalaCodecs.scala` following the same pattern:

```scala
val yourCustomCodec: Codec[YourType] = new Codec[YourType]:
  override def encode(writer: BsonWriter, value: YourType, encoderContext: EncoderContext): Unit =
    // Your encoding logic

  override def decode(reader: BsonReader, decoderContext: DecoderContext): YourType =
    // Your decoding logic

  override def getEncoderClass: Class[YourType] = classOf[YourType]
```

## See Also
- `SCALA_CODECS_USAGE.md` - Complete usage guide with examples
- `docs/BSON_TYPE_MAPPING.md` - Reference for all supported types
- `docs/QUICKSTART.md` - Getting started guide
