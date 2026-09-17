package io.github.mbannour.mongo.codecs.compatibility

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.{BsonArray, BsonDocument, BsonInt32, BsonString}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** BSON compatibility test for the collection shapes in the v1.0 contract. See [[UserCompatibilitySpec]] for the package-level contract.
  *
  * Two structural promises are frozen here: an `Iterable` becomes a BSON array, and a `Map[String, V]` becomes an embedded document rather
  * than an array of pairs. The empty cases matter as much as the populated ones, because "absent" and "present but empty" are different
  * documents to query.
  */
class CollectionCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class Basket(name: String, tags: List[String], sizes: Vector[Int])
  case class Scores(name: String, bySubject: Map[String, Int])

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec()
  )

  private val basketRegistry = RegistryBuilder.from(primitives).register[Basket].build
  given basketCodec: Codec[Basket] = basketRegistry.get(classOf[Basket])

  private val basket = Basket("fruit", List("red", "ripe"), Vector(1, 2))

  private val frozenBasketBson = new BsonDocument()
    .append("name", new BsonString("fruit"))
    .append("tags", new BsonArray(java.util.List.of(new BsonString("red"), new BsonString("ripe"))))
    .append("sizes", new BsonArray(java.util.List.of(new BsonInt32(1), new BsonInt32(2))))

  "Basket codec" should "encode every Iterable as a BSON array, in element order" in {
    CodecTestKit.assertBsonStructure(basket, frozenBasketBson)
  }

  it should "decode a hand-written document that was never produced by encode" in {
    CodecTestKit.fromBsonDocument[Basket](frozenBasketBson) shouldBe basket
  }

  it should "round-trip to an equal value" in {
    CodecTestKit.roundTrip(basket) shouldBe basket
  }

  /** An empty collection is written as an empty array, not omitted and not null, so a document always carries the field and a reader never
    * has to distinguish "no tags" from "field not written yet".
    */
  it should "encode an empty collection as an empty array rather than omitting it" in {
    val empty = Basket("fruit", Nil, Vector.empty)

    val expected = new BsonDocument()
      .append("name", new BsonString("fruit"))
      .append("tags", new BsonArray())
      .append("sizes", new BsonArray())

    CodecTestKit.assertBsonStructure(empty, expected)
    CodecTestKit.fromBsonDocument[Basket](expected) shouldBe empty
    CodecTestKit.roundTrip(empty) shouldBe empty
  }

  private val scoresRegistry = RegistryBuilder.from(primitives).register[Scores].build
  given scoresCodec: Codec[Scores] = scoresRegistry.get(classOf[Scores])

  private val scores = Scores("Alice", Map("math" -> 90, "art" -> 80))

  private val frozenScoresBson = new BsonDocument()
    .append("name", new BsonString("Alice"))
    .append(
      "bySubject",
      new BsonDocument()
        .append("math", new BsonInt32(90))
        .append("art", new BsonInt32(80))
    )

  "Scores codec" should "encode a Map[String, V] as an embedded document keyed by the map key" in {
    CodecTestKit.assertBsonStructure(scores, frozenScoresBson)
  }

  it should "decode a hand-written document that was never produced by encode" in {
    CodecTestKit.fromBsonDocument[Scores](frozenScoresBson) shouldBe scores
  }

  it should "round-trip to an equal value" in {
    CodecTestKit.roundTrip(scores) shouldBe scores
  }

  it should "encode an empty map as an empty document rather than omitting it" in {
    val empty = Scores("Alice", Map.empty)

    val expected = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("bySubject", new BsonDocument())

    CodecTestKit.assertBsonStructure(empty, expected)
    CodecTestKit.fromBsonDocument[Scores](expected) shouldBe empty
    CodecTestKit.roundTrip(empty) shouldBe empty
  }
end CollectionCompatibilitySpec
