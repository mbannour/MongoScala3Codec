package io.github.mbannour.mongo.codecs

import java.util.UUID

import scala.compiletime.testing.{Error, typeCheckErrors}

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonReader, BsonWriter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** A user-supplied codec, to check that derivation reuses it rather than trying to own every field type. */
final case class Money(cents: Long)

class MoneyCodec extends Codec[Money]:
  override def encode(writer: BsonWriter, value: Money, context: EncoderContext): Unit = writer.writeInt64(value.cents)
  override def decode(reader: BsonReader, context: DecoderContext): Money = Money(reader.readInt64())
  override def getEncoderClass: Class[Money] = classOf[Money]

case class GenericBox[A](value: A)

/** The v1.0 supported-type contract, as measured rather than as documented.
  *
  * Derivation owns a fixed set of field shapes: the nine primitives, `UUID`, `Option`, `Map[String, V]`, `Iterable`, and case classes and
  * sealed hierarchies. Everything else is handed to whatever `Codec` the registry holds for that class, which is how user and official
  * driver codecs plug in - and also why an unsupported field type compiles and then fails on first encode.
  *
  * The unsupported cases below are characterization, not aspiration: they record today's behavior so a change to it is visible. Types
  * already covered elsewhere (`@BsonId`, `@BsonIgnore`, `@BsonProperty`, discriminators, enums, recursion, constructor defaults) are not
  * repeated here, and neither is plain driver codec behavior.
  */
class SupportedTypeContractSpec extends AnyFlatSpec with Matchers:

  case class Primitives(i: Int, l: Long, d: Double, f: Float, b: Boolean, by: Byte, sh: Short, ch: Char, s: String)
  case class Ids(oid: ObjectId, uuid: UUID)
  case class Collections(list: List[String], seq: Seq[Int], vector: Vector[String], set: Set[Int])
  case class Maps(strings: Map[String, String], ints: Map[String, Int])
  case class Binary(data: Array[Byte])
  case class JavaDecimal(amount: java.math.BigDecimal)
  case class ScalaDecimal(amount: BigDecimal)
  case class StringArray(values: Array[String])
  case class Priced(name: String, price: Money)
  case class WithEither(value: Either[String, Int])

  /** The driver's own providers, so that "unsupported" means unsupported rather than "no codec registered". */
  private val driver: CodecRegistry = CodecRegistries.fromProviders(
    new org.bson.codecs.ValueCodecProvider(),
    new org.bson.codecs.BsonValueCodecProvider(),
    new org.bson.codecs.DocumentCodecProvider(),
    new org.bson.codecs.IterableCodecProvider(),
    new org.bson.codecs.MapCodecProvider()
  )

  private def messageOf(errors: List[Error]): String = errors.map(_.message).mkString("\n")

  "The nine primitive field types" should "encode to their frozen BSON types and round-trip" in {
    val registry = RegistryBuilder.from(driver).register[Primitives].build

    given codec: Codec[Primitives] = registry.get(classOf[Primitives])

    val value = Primitives(1, 2L, 3.5d, 4.5f, true, 5.toByte, 6.toShort, 'x', "s")

    // Char is an Int32 of its code point, Float widens to a Double, Byte and Short are Int32.
    CodecTestKit.assertBsonStructure(
      value,
      BsonDocument.parse(
        """{"i": 1, "l": {"$numberLong": "2"}, "d": 3.5, "f": 4.5, "b": true,
           "by": 5, "sh": 6, "ch": 120, "s": "s"}"""
      )
    )

    CodecTestKit.roundTrip(value) shouldBe value
  }

  "ObjectId and UUID" should "encode as an ObjectId and as a plain string" in {
    val registry = RegistryBuilder.from(driver).register[Ids].build

    given codec: Codec[Ids] = registry.get(classOf[Ids])

    val value = Ids(new ObjectId("507f1f77bcf86cd799439011"), UUID.fromString("00000000-0000-0000-0000-000000000001"))

    // Derivation writes UUID itself, as a string. It does not defer to the driver's UuidCodec, which
    // would write Binary subtype 4. docs/BSON_TYPE_MAPPING.md still claims the Binary form.
    CodecTestKit.assertBsonStructure(
      value,
      BsonDocument.parse(
        """{"oid": {"$oid": "507f1f77bcf86cd799439011"},
           "uuid": "00000000-0000-0000-0000-000000000001"}"""
      )
    )

    CodecTestKit.roundTrip(value) shouldBe value
  }

  "List, Seq, Vector and Set" should "encode as BSON arrays and round-trip" in {
    val registry = RegistryBuilder.from(driver).register[Collections].build

    given codec: Codec[Collections] = registry.get(classOf[Collections])

    val value = Collections(List("a"), Seq(1), Vector("v"), Set(2))

    CodecTestKit.assertBsonStructure(
      value,
      BsonDocument.parse("""{"list": ["a"], "seq": [1], "vector": ["v"], "set": [2]}""")
    )

    CodecTestKit.roundTrip(value) shouldBe value
  }

  it should "encode empty collections as empty arrays" in {
    val registry = RegistryBuilder.from(driver).register[Collections].build

    given codec: Codec[Collections] = registry.get(classOf[Collections])

    val empty = Collections(Nil, Seq.empty, Vector.empty, Set.empty)

    CodecTestKit.assertBsonStructure(
      empty,
      BsonDocument.parse("""{"list": [], "seq": [], "vector": [], "set": []}""")
    )

    CodecTestKit.roundTrip(empty) shouldBe empty
  }

  "A Map with String keys" should "encode as an embedded document and round-trip" in {
    val registry = RegistryBuilder.from(driver).register[Maps].build

    given codec: Codec[Maps] = registry.get(classOf[Maps])

    val value = Maps(Map("k" -> "v"), Map("n" -> 1))

    CodecTestKit.assertBsonStructure(
      value,
      BsonDocument.parse("""{"strings": {"k": "v"}, "ints": {"n": 1}}""")
    )

    CodecTestKit.roundTrip(value) shouldBe value
  }

  "A Map with non-String keys" should "fail to compile with an actionable diagnostic" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class IntKeyed(m: Map[Int, String])

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[IntKeyed]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message should include("Map keys must be String")
      message should include("IntKeyed")
    }
  }

  "A field whose type has a user-supplied codec" should "use that codec rather than deriving one" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromRegistries(CodecRegistries.fromCodecs(new MoneyCodec()), driver))
      .register[Priced]
      .build

    given codec: Codec[Priced] = registry.get(classOf[Priced])

    val value = Priced("widget", Money(500))

    // Money is a case class, yet it is written as the int64 its own codec writes, not as a document.
    CodecTestKit.assertBsonStructure(
      value,
      BsonDocument.parse("""{"name": "widget", "price": {"$numberLong": "500"}}""")
    )

    CodecTestKit.roundTrip(value) shouldBe value
  }

  "Array[Byte]" should "reach the driver's byte-array codec and encode as binary" in {
    val registry = RegistryBuilder.from(driver).register[Binary].build

    given codec: Codec[Binary] = registry.get(classOf[Binary])

    val bson = CodecTestKit.toBsonDocument(Binary(Array[Byte](1, 2, 3)))

    bson shouldBe BsonDocument.parse("""{"data": {"$binary": {"base64": "AQID", "subType": "00"}}}""")
  }

  "java.math.BigDecimal" should "reach the driver's decimal codec and encode as Decimal128" in {
    val registry = RegistryBuilder.from(driver).register[JavaDecimal].build

    given codec: Codec[JavaDecimal] = registry.get(classOf[JavaDecimal])

    val bson = CodecTestKit.toBsonDocument(JavaDecimal(new java.math.BigDecimal("12.34")))

    bson shouldBe BsonDocument.parse("""{"amount": {"$numberDecimal": "12.34"}}""")
  }

  // ---------------------------------------------------------------------------------------------
  // Characterization of what is NOT supported. These record today's behavior so that a change to it
  // shows up as a failing test rather than as a surprise.
  // ---------------------------------------------------------------------------------------------

  "scala.math.BigDecimal" should "derive but fail on encode, since no codec covers the Scala class" in {
    val registry = RegistryBuilder.from(driver).register[ScalaDecimal].build

    given codec: Codec[ScalaDecimal] = registry.get(classOf[ScalaDecimal])

    val error = intercept[IllegalArgumentException] {
      CodecTestKit.toBsonDocument(ScalaDecimal(BigDecimal("12.34")))
    }

    error.getMessage should include("scala.math.BigDecimal")
  }

  "An array of a non-byte element type" should "derive but fail on encode" in {
    val registry = RegistryBuilder.from(driver).register[StringArray].build

    given codec: Codec[StringArray] = registry.get(classOf[StringArray])

    val error = intercept[IllegalArgumentException] {
      CodecTestKit.toBsonDocument(StringArray(Array("a")))
    }

    error.getMessage should include("No codec found")
  }

  "Either" should "derive but fail on encode, and stay outside the contract" in {
    val registry = RegistryBuilder.from(driver).register[WithEither].build

    given codec: Codec[WithEither] = registry.get(classOf[WithEither])

    val error = intercept[IllegalArgumentException] {
      CodecTestKit.toBsonDocument(WithEither(Right(1)))
    }

    error.getMessage should include("Either")
  }

  "A tuple field" should "fail to compile, naming the model, the field and the type" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class Tupled(pair: (String, Int))

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[Tupled]
        .build
    """)

    errors should not be empty

    withClue(s"diagnostic was:\n${messageOf(errors)}\n") {
      messageOf(errors) should include("Tupled")
      messageOf(errors) should include("pair")
      messageOf(errors).toLowerCase should include("tuple")
    }
  }

  "A generic case class" should "fail to compile, naming the model, the field and the type" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{GenericBox, RegistryBuilder}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .register[GenericBox[String]]
        .build
    """)

    errors should not be empty

    withClue(s"diagnostic was:\n${messageOf(errors)}\n") {
      messageOf(errors) should include("GenericBox")
      messageOf(errors) should include("value")
      messageOf(errors).toLowerCase should include("unsupported")
    }
  }
end SupportedTypeContractSpec
