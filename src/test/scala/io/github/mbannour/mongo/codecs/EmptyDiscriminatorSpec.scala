package io.github.mbannour.mongo.codecs

import scala.compiletime.testing.{Error, typeCheckErrors}

import org.bson.BsonDocument
import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonDiscriminator
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

// Invalid hierarchies. Declaring them is fine; only deriving a codec for them must fail, so each one is
// declared here and only the derivation goes through typeCheckErrors. (typeCheckErrors does not see
// class-level annotations on classes declared inside its own string.)

sealed trait EmptyAnimal

@BsonDiscriminator("")
case class EmptyDog(name: String) extends EmptyAnimal

case class EmptyCat(name: String) extends EmptyAnimal

// Empty on both: this breaks the empty rule and the uniqueness rule at once.
sealed trait TwoEmptyAnimal

@BsonDiscriminator("")
case class TwoEmptyDog(name: String) extends TwoEmptyAnimal

@BsonDiscriminator("")
case class TwoEmptyCat(name: String) extends TwoEmptyAnimal

sealed trait EmptyTransport
sealed trait EmptyMotorized extends EmptyTransport

@BsonDiscriminator("")
case class EmptyBus(seats: Int) extends EmptyMotorized

case class EmptyBicycle(gears: Int) extends EmptyTransport

// Valid: one character is a perfectly good discriminator.
sealed trait Terse

@BsonDiscriminator("x")
case class TerseDog(name: String) extends Terse

case class TerseCat(name: String) extends Terse

// Valid, and deliberately so: " " is not "" under exact-string semantics, and this task adds no
// whitespace policy. Recorded here so that introducing one later is a visible decision.
sealed trait Blank

@BsonDiscriminator(" ")
case class BlankDog(name: String) extends Blank

case class BlankCat(name: String) extends Blank

/** An explicit `@BsonDiscriminator` value must not be empty: `{"_type": ""}` is indistinguishable from a missing discriminator to anyone
  * reading a document, and it is never what the annotation was reached for.
  *
  * Only explicit values are checked. An unannotated subtype keeps its simple name, which is never empty.
  */
class EmptyDiscriminatorSpec extends AnyFlatSpec with Matchers:

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec()
  )

  private def messageOf(errors: List[Error]): String = errors.map(_.message).mkString("\n")

  "A subtype annotated with an empty value" should "fail to compile, naming the annotation and the subtype" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{EmptyAnimal, RegistryBuilder}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .registerSealed[EmptyAnimal]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message should include("BsonDiscriminator")
      message should include("EmptyDog")
      message.toLowerCase should include("empty")
    }
  }

  it should "fail the same way as a concrete subtype of a multi-level hierarchy" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{EmptyTransport, RegistryBuilder}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.IntegerCodec()))
        .registerSealed[EmptyTransport]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message should include("BsonDiscriminator")
      message should include("EmptyBus")
      message.toLowerCase should include("empty")
    }
  }

  // Both rules are broken at once. The empty value is the more basic mistake and is reported first,
  // which falls out of computing effective values before grouping them - no ordering machinery.
  "Two subtypes both annotated with an empty value" should "be reported as an empty value, not as a duplicate" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{RegistryBuilder, TwoEmptyAnimal}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .registerSealed[TwoEmptyAnimal]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message should include("BsonDiscriminator")
      message.toLowerCase should include("empty")
      message.toLowerCase should not include "duplicate"
    }
  }

  "A one-character value" should "stay valid: the rule is about emptiness, not length" in {
    val registry: CodecRegistry = RegistryBuilder.from(primitives).registerSealed[Terse].build

    given codec: Codec[Terse] = registry.get(classOf[Terse])

    CodecTestKit.assertBsonStructure[Terse](TerseDog("Rex"), BsonDocument.parse("""{"_type": "x", "name": "Rex"}"""))
    CodecTestKit.assertBsonStructure[Terse](TerseCat("Milo"), BsonDocument.parse("""{"_type": "TerseCat", "name": "Milo"}"""))

    CodecTestKit.roundTrip[Terse](TerseDog("Rex")) shouldBe TerseDog("Rex")
    CodecTestKit.roundTrip[Terse](TerseCat("Milo")) shouldBe TerseCat("Milo")
  }

  "A blank value" should "stay valid, since values are compared as exact strings and nothing is trimmed" in {
    val registry: CodecRegistry = RegistryBuilder.from(primitives).registerSealed[Blank].build

    given codec: Codec[Blank] = registry.get(classOf[Blank])

    CodecTestKit.assertBsonStructure[Blank](BlankDog("Rex"), BsonDocument.parse("""{"_type": " ", "name": "Rex"}"""))

    CodecTestKit.roundTrip[Blank](BlankDog("Rex")) shouldBe BlankDog("Rex")
  }
end EmptyDiscriminatorSpec
