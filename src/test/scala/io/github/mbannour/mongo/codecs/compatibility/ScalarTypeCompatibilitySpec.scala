package io.github.mbannour.mongo.codecs.compatibility

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.{BsonDocument, BsonDouble, BsonInt32, BsonInt64, BsonString, BsonType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** BSON compatibility test for the scalar field types derivation writes itself. See [[UserCompatibilitySpec]] for the package-level
  * contract.
  *
  * The BSON type, not just the value, is the contract: a query written against an `int` field stops matching if the same field starts
  * arriving as a `long`, and MongoDB comparisons are type-aware. Four of the seven mappings below widen or change representation, so they
  * are worth stating rather than assuming.
  */
class ScalarTypeCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class Scalars(
      int: Int,
      long: Long,
      double: Double,
      float: Float,
      short: Short,
      byte: Byte,
      char: Char,
      string: String
  )

  private val registry = RegistryBuilder
    .from(
      CodecRegistries.fromCodecs(
        new org.bson.codecs.StringCodec(),
        new org.bson.codecs.IntegerCodec(),
        new org.bson.codecs.LongCodec(),
        new org.bson.codecs.DoubleCodec()
      )
    )
    .register[Scalars]
    .build

  given codec: Codec[Scalars] = registry.get(classOf[Scalars])

  private val scalars = Scalars(1, 2L, 3.5d, 4.5f, 5.toShort, 6.toByte, 'x', "s")

  /** `x` is code point 120. `Float` widens to `Double`, and `Short`, `Byte` and `Char` all narrow to `Int32`, so the stored document has
    * fewer distinct numeric types than the Scala model does.
    */
  private val frozenBson = new BsonDocument()
    .append("int", new BsonInt32(1))
    .append("long", new BsonInt64(2L))
    .append("double", new BsonDouble(3.5d))
    .append("float", new BsonDouble(4.5d))
    .append("short", new BsonInt32(5))
    .append("byte", new BsonInt32(6))
    .append("char", new BsonInt32(120))
    .append("string", new BsonString("s"))

  "Scalars codec" should "encode each scalar to its frozen BSON type" in {
    CodecTestKit.assertBsonStructure(scalars, frozenBson)
  }

  it should "state the frozen BSON type of every scalar field explicitly" in {
    val encoded = CodecTestKit.toBsonDocument(scalars)

    encoded.get("int").getBsonType shouldBe BsonType.INT32
    encoded.get("long").getBsonType shouldBe BsonType.INT64
    encoded.get("double").getBsonType shouldBe BsonType.DOUBLE
    encoded.get("float").getBsonType shouldBe BsonType.DOUBLE
    encoded.get("short").getBsonType shouldBe BsonType.INT32
    encoded.get("byte").getBsonType shouldBe BsonType.INT32
    encoded.get("char").getBsonType shouldBe BsonType.INT32
    encoded.get("string").getBsonType shouldBe BsonType.STRING
  }

  it should "decode a hand-written document that was never produced by encode" in {
    CodecTestKit.fromBsonDocument[Scalars](frozenBson) shouldBe scalars
  }

  it should "round-trip to an equal value, recovering the narrower Scala types" in {
    CodecTestKit.roundTrip(scalars) shouldBe scalars
  }
end ScalarTypeCompatibilitySpec
