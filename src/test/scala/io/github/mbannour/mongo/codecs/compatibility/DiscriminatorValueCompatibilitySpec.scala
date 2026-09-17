package io.github.mbannour.mongo.codecs.compatibility

import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.{BsonDocument, BsonInt32, BsonInvalidOperationException, BsonString}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonDiscriminator
import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

sealed trait Vehicle

@BsonDiscriminator("car")
case class Car(plate: String) extends Vehicle

// Deliberately unannotated: its effective discriminator is its simple name, and it must stay that way even
// though a sibling opted into a custom value.
case class Lorry(axles: Int) extends Vehicle

/** BSON compatibility test for `@BsonDiscriminator` values. See [[UserCompatibilitySpec]] for the package-level contract.
  *
  * A discriminator value is the most load-bearing string in a stored ADT: it is the only thing that tells a reader which subtype a document
  * holds. `@BsonDiscriminator` exists so that value can be decoupled from the Scala type name - so a type can be renamed, or made to match
  * a collection written by another system - which means the mapping must be frozen in both directions.
  *
  * [[SealedTraitCompatibilitySpec]] freezes the default (simple-name) values and the discriminator field name; this spec covers custom
  * values and their migration semantics.
  */
class DiscriminatorValueCompatibilitySpec extends AnyFlatSpec with Matchers:

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec()
  )

  private val registry: CodecRegistry = RegistryBuilder.from(primitives).registerSealed[Vehicle].build
  given codec: Codec[Vehicle] = registry.get(classOf[Vehicle])

  private val frozenCarBson = new BsonDocument()
    .append("_type", new BsonString("car"))
    .append("plate", new BsonString("X-1"))

  private val frozenLorryBson = new BsonDocument()
    .append("_type", new BsonString("Lorry"))
    .append("axles", new BsonInt32(3))

  "An annotated subtype" should "record the annotation's value, not its type name" in {
    CodecTestKit.assertBsonStructure[Vehicle](Car("X-1"), frozenCarBson)
  }

  it should "decode a hand-written document that was never produced by encode" in {
    CodecTestKit.fromBsonDocument[Vehicle](frozenCarBson) shouldBe Car("X-1")
  }

  it should "round-trip to an equal value" in {
    CodecTestKit.roundTrip[Vehicle](Car("X-1")) shouldBe Car("X-1")
  }

  "An unannotated sibling" should "keep its simple name, unaffected by the annotated subtype" in {
    CodecTestKit.assertBsonStructure[Vehicle](Lorry(3), frozenLorryBson)
    CodecTestKit.fromBsonDocument[Vehicle](frozenLorryBson) shouldBe Lorry(3)
    CodecTestKit.roundTrip[Vehicle](Lorry(3)) shouldBe Lorry(3)
  }

  /** Adding `@BsonDiscriminator("car")` to a `Car` whose documents were written as `"Car"` is a data migration. The library has no alias
    * table, so the old value is simply unknown - and failing loudly beats silently decoding into the wrong subtype.
    */
  "A document written under the old default value" should "not be readable after a custom value is adopted" in {
    val writtenBeforeTheAnnotation = new BsonDocument()
      .append("_type", new BsonString("Car"))
      .append("plate", new BsonString("X-1"))

    val error = intercept[BsonInvalidOperationException] {
      CodecTestKit.fromBsonDocument[Vehicle](writtenBeforeTheAnnotation)
    }

    error.getMessage should include("Car")
  }

  "Discriminator values" should "be matched exactly, so case alone distinguishes them" in {
    val wrongCase = new BsonDocument()
      .append("_type", new BsonString("CAR"))
      .append("plate", new BsonString("X-1"))

    intercept[BsonInvalidOperationException] {
      CodecTestKit.fromBsonDocument[Vehicle](wrongCase)
    }
  }
end DiscriminatorValueCompatibilitySpec
