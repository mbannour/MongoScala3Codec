package io.github.mbannour.mongo.codecs

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.{BsonBoolean, BsonDocument, BsonInt32, BsonString}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** The published testing API, exercised the way a user would.
  *
  * Every expectation here is built by hand rather than taken from the codec, because a kit that produced its own oracle would pass whatever
  * the codec did. The kit's job is only to drive `Codec[T]` and compare; these tests check that it drives it faithfully and that a mismatch
  * is actually reported.
  */
/** Representative shapes, declared top level because sealed hierarchies and enums must be. */
sealed trait KitAnimal
case class KitDog(name: String, breed: String) extends KitAnimal

enum KitColour:
  case Red, Green, Blue

case class KitPaint(name: String, colour: KitColour)

case class KitNode(value: String, next: Option[KitNode])

final case class KitTicket(code: String)

class KitTicketCodec extends Codec[KitTicket]:
  override def encode(w: org.bson.BsonWriter, v: KitTicket, c: org.bson.codecs.EncoderContext): Unit = w.writeString(v.code)
  override def decode(r: org.bson.BsonReader, c: org.bson.codecs.DecoderContext): KitTicket = KitTicket(r.readString())
  override def getEncoderClass: Class[KitTicket] = classOf[KitTicket]

case class KitOrder(ticket: KitTicket, buyer: String)

class CodecTestKitApiSpec extends AnyFlatSpec with Matchers:

  case class User(name: String, age: Int, active: Boolean)

  private val registry = RegistryBuilder
    .from(
      CodecRegistries.fromCodecs(
        new org.bson.codecs.StringCodec(),
        new org.bson.codecs.IntegerCodec(),
        new org.bson.codecs.BooleanCodec()
      )
    )
    .register[User]
    .build

  private val kit = CodecTestKit(registry.get(classOf[User]))

  private val alice = User("Alice", 37, true)

  private val aliceBson = new BsonDocument()
    .append("name", new BsonString("Alice"))
    .append("age", new BsonInt32(37))
    .append("active", new BsonBoolean(true))

  "A kit" should "assert a value against an independently written document" in {
    kit.assertBson(alice, aliceBson)
  }

  it should "fail, showing both documents, when the encoding does not match" in {
    val wrong = new BsonDocument()
      .append("name", new BsonString("Bob"))
      .append("age", new BsonInt32(37))
      .append("active", new BsonBoolean(true))

    val error = intercept[AssertionError](kit.assertBson(alice, wrong))

    error.getMessage should include("Bob")
    error.getMessage should include("Alice")
  }

  it should "assert that a hand-written document decodes to an expected value" in {
    kit.assertDecode(aliceBson, alice)
  }

  it should "fail, showing both values, when the decoded value does not match" in {
    val error = intercept[AssertionError](kit.assertDecode(aliceBson, User("Bob", 37, true)))

    error.getMessage should include("Bob")
    error.getMessage should include("Alice")
  }

  it should "assert that a value survives encoding and decoding" in {
    kit.assertRoundTrip(alice)
  }

  it should "return the encoded document for callers that need to inspect it" in {
    kit.encode(alice) shouldBe aliceBson
  }

  it should "return the decoded value for callers that need to inspect it" in {
    kit.decode(aliceBson) shouldBe alice
  }

  /** A decode failure is the user's own compatibility signal - a field their model requires is not in the document - so the kit must let it
    * through rather than reporting an assertion failure of its own.
    */
  it should "let a decode failure propagate with its original cause" in {
    val incomplete = new BsonDocument().append("name", new BsonString("Alice"))

    val error = intercept[RuntimeException](kit.decode(incomplete))

    error shouldBe a[RuntimeException]
    error should not be a[AssertionError]
    error.getMessage should include("age")
  }

  it should "be built from a codec, so the codec under test stays visible and explicit" in {
    val codec: Codec[User] = registry.get(classOf[User])

    CodecTestKit(codec).encode(alice) shouldBe aliceBson
  }

  // The shapes below are already covered by the compatibility package. What is checked here is only that the
  // kit composes with them: it must add nothing, assume nothing about how the codec was obtained, and not
  // disturb the deferred resolution a recursive codec depends on.

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.BooleanCodec()
  )

  "A kit built on a sealed root codec" should "carry the discriminator, with no ADT-specific API" in {
    val registry = RegistryBuilder.from(primitives).registerSealed[KitAnimal].build
    val kit = CodecTestKit[KitAnimal](registry.get(classOf[KitAnimal]))

    val expected = new BsonDocument()
      .append("_type", new BsonString("KitDog"))
      .append("name", new BsonString("Rex"))
      .append("breed", new BsonString("Labrador"))

    kit.assertBson(KitDog("Rex", "Labrador"), expected)
    kit.assertDecode(expected, KitDog("Rex", "Labrador"))
    kit.assertRoundTrip(KitDog("Rex", "Labrador"))
  }

  /** The case that decided the kit takes its codec as a parameter: one type, two codecs. A `using`-based API cannot express this without
    * disambiguating by hand at every call.
    */
  "Two kits over one type" should "test a string enum and an ordinal enum side by side" in {
    def registryFor(provider: org.bson.codecs.configuration.CodecProvider) =
      RegistryBuilder
        .from(CodecRegistries.fromRegistries(CodecRegistries.fromProviders(provider), primitives))
        .register[KitPaint]
        .build

    val stringKit = CodecTestKit(registryFor(EnumValueCodecProvider.forStringEnum[KitColour]).get(classOf[KitPaint]))
    val ordinalKit = CodecTestKit(registryFor(EnumValueCodecProvider.forOrdinalEnum[KitColour]).get(classOf[KitPaint]))

    stringKit.assertBson(
      KitPaint("wall", KitColour.Green),
      new BsonDocument().append("name", new BsonString("wall")).append("colour", new BsonString("Green"))
    )

    ordinalKit.assertBson(
      KitPaint("wall", KitColour.Green),
      new BsonDocument().append("name", new BsonString("wall")).append("colour", new BsonInt32(1))
    )
  }

  "A kit built on a recursive codec" should "not disturb the codec's deferred resolution" in {
    val registry = RegistryBuilder.from(primitives).register[KitNode].build
    val kit = CodecTestKit(registry.get(classOf[KitNode]))

    val node = KitNode("a", Some(KitNode("b", None)))

    val expected = new BsonDocument()
      .append("value", new BsonString("a"))
      .append(
        "next",
        new BsonDocument().append("value", new BsonString("b")).append("next", new org.bson.BsonNull())
      )

    kit.assertBson(node, expected)
    kit.assertDecode(expected, node)
    kit.assertRoundTrip(node)
  }

  /** The kit must not assume a codec was macro-derived: a hand-written one goes through the same path. */
  "A kit over a model with a hand-written field codec" should "work through the ordinary registry mechanism" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromRegistries(CodecRegistries.fromCodecs(new KitTicketCodec()), primitives))
      .register[KitOrder]
      .build

    val kit = CodecTestKit(registry.get(classOf[KitOrder]))

    val expected = new BsonDocument()
      .append("ticket", new BsonString("T-1"))
      .append("buyer", new BsonString("Alice"))

    kit.assertBson(KitOrder(KitTicket("T-1"), "Alice"), expected)
    kit.assertDecode(expected, KitOrder(KitTicket("T-1"), "Alice"))
  }

  /** The kit's boundary, asserted so it is a known limit rather than a surprise. A codec whose top level is a scalar has no document form -
    * BSON forbids a bare value at the root of a document - so it cannot be driven directly. Such a codec is tested as a field of a model,
    * which the previous test does. Widening the kit to scalars would mean a second pair of encode/decode operations for a case the
    * compatibility suite already covers this way.
    */
  "A kit built on a codec that writes a bare scalar" should "fail against BSON's own root-level rule, not silently" in {
    val kit = CodecTestKit(new KitTicketCodec())

    val error = intercept[org.bson.BsonInvalidOperationException](kit.encode(KitTicket("T-9")))

    error.getMessage should include("root level")
  }
end CodecTestKitApiSpec
