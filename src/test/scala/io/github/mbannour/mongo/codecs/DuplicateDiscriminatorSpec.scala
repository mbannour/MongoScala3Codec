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
// declared here and only the derivation is put through typeCheckErrors below.

sealed trait DupAnimal

@BsonDiscriminator("animal")
case class DupDog(name: String) extends DupAnimal

@BsonDiscriminator("animal")
case class DupCat(name: String) extends DupAnimal

sealed trait MixAnimal

@BsonDiscriminator("MixCat")
case class MixDog(name: String) extends MixAnimal

case class MixCat(name: String) extends MixAnimal

sealed trait TriRoot

@BsonDiscriminator("x")
case class TriA(v: Int) extends TriRoot

@BsonDiscriminator("x")
case class TriB(v: Int) extends TriRoot

@BsonDiscriminator("x")
case class TriC(v: Int) extends TriRoot

sealed trait DupTransport
sealed trait DupMotorized extends DupTransport

@BsonDiscriminator("vehicle")
case class DupBus(seats: Int) extends DupMotorized

@BsonDiscriminator("vehicle")
case class DupCar(doors: Int) extends DupMotorized

// Two subtypes reaching the same simple name through different objects: no annotation involved, yet the
// effective values collide just the same.
sealed trait SameNameRoot

object Dogs:
  case class Pet(name: String) extends SameNameRoot

object Cats:
  case class Pet(name: String) extends SameNameRoot

// Valid hierarchies.

sealed trait Pet

@BsonDiscriminator("dog")
case class Puppy(name: String) extends Pet

@BsonDiscriminator("cat")
case class Kitten(name: String) extends Pet

sealed trait Kennel

@BsonDiscriminator("dog")
case class Boarder(name: String) extends Kennel

case class Warden(name: String) extends Kennel

// Same values as Pet's subtypes: uniqueness is per hierarchy, not global.
sealed trait Toy

@BsonDiscriminator("dog")
case class ChewToy(name: String) extends Toy

@BsonDiscriminator("cat")
case class Scratcher(name: String) extends Toy

// "bird" and "Bird" are different BSON strings, so these do not collide.
sealed trait Cage

@BsonDiscriminator("bird")
case class Budgie(name: String) extends Cage

@BsonDiscriminator("Bird")
case class Parrot(name: String) extends Cage

/** Within one sealed hierarchy every concrete subtype must have a distinct effective discriminator value, otherwise a document cannot say
  * which subtype it holds. Checked at derivation, on effective values, so a custom value colliding with another subtype's default name is
  * caught too.
  */
class DuplicateDiscriminatorSpec extends AnyFlatSpec with Matchers:

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec()
  )

  private def messageOf(errors: List[Error]): String = errors.map(_.message).mkString("\n")

  "Two subtypes annotated with the same value" should "fail to compile, naming the value and both subtypes" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{DupAnimal, RegistryBuilder}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .registerSealed[DupAnimal]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message.toLowerCase should include("duplicate")
      message.toLowerCase should include("discriminator")
      message should include("animal")
      message should include("DupDog")
      message should include("DupCat")
    }
  }

  "A custom value colliding with another subtype's default name" should "fail to compile" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{MixAnimal, RegistryBuilder}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .registerSealed[MixAnimal]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message.toLowerCase should include("duplicate")
      message.toLowerCase should include("discriminator")
      message should include("MixCat")
      message should include("MixDog")
      // MixCat carries no annotation, so blaming the annotation would send the reader to the wrong place.
      message should not include "Duplicate @BsonDiscriminator annotation"
    }
  }

  "Three subtypes sharing one value" should "fail to compile, naming all three" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{RegistryBuilder, TriRoot}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.IntegerCodec()))
        .registerSealed[TriRoot]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message.toLowerCase should include("duplicate")
      message should include("TriA")
      message should include("TriB")
      message should include("TriC")
    }
  }

  "A duplicate in a flattened multi-level hierarchy" should "fail to compile" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{DupTransport, RegistryBuilder}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.IntegerCodec()))
        .registerSealed[DupTransport]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message.toLowerCase should include("duplicate")
      message should include("vehicle")
      message should include("DupBus")
      message should include("DupCar")
    }
  }

  "Two subtypes sharing a simple name through different objects" should "fail to compile, naming them unambiguously" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{RegistryBuilder, SameNameRoot}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .registerSealed[SameNameRoot]
        .build
    """)

    errors should not be empty

    val message = messageOf(errors)

    withClue(s"diagnostic was:\n$message\n") {
      message.toLowerCase should include("duplicate")
      message should include("Pet")
      // 'Pet', 'Pet' would name neither of them, so the owning objects have to appear.
      message should include("Dogs")
      message should include("Cats")
    }
  }

  "A configured discriminator field" should "not let a duplicate through" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import io.github.mbannour.mongo.codecs.{CodecConfig, DupAnimal, RegistryBuilder}
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
        .withConfig(CodecConfig(discriminatorField = "_class"))
        .registerSealed[DupAnimal]
        .build
    """)

    errors should not be empty

    withClue(s"diagnostic was:\n${messageOf(errors)}\n") {
      messageOf(errors).toLowerCase should include("duplicate")
    }
  }

  "Distinct custom values" should "compile, and keep each subtype's own value" in {
    val registry: CodecRegistry = RegistryBuilder.from(primitives).registerSealed[Pet].build

    given codec: Codec[Pet] = registry.get(classOf[Pet])

    CodecTestKit.assertBsonStructure[Pet](Puppy("Rex"), BsonDocument.parse("""{"_type": "dog", "name": "Rex"}"""))
    CodecTestKit.assertBsonStructure[Pet](Kitten("Milo"), BsonDocument.parse("""{"_type": "cat", "name": "Milo"}"""))

    CodecTestKit.roundTrip[Pet](Puppy("Rex")) shouldBe Puppy("Rex")
    CodecTestKit.roundTrip[Pet](Kitten("Milo")) shouldBe Kitten("Milo")

    CodecTestKit.fromBsonDocument[Pet](BsonDocument.parse("""{"_type": "cat", "name": "Milo"}""")) shouldBe Kitten("Milo")
  }

  "A hierarchy mixing a custom value with a default one" should "compile, since validation does not require annotating everything" in {
    val registry: CodecRegistry = RegistryBuilder.from(primitives).registerSealed[Kennel].build

    given codec: Codec[Kennel] = registry.get(classOf[Kennel])

    CodecTestKit.assertBsonStructure[Kennel](Boarder("Rex"), BsonDocument.parse("""{"_type": "dog", "name": "Rex"}"""))
    CodecTestKit.assertBsonStructure[Kennel](Warden("Ann"), BsonDocument.parse("""{"_type": "Warden", "name": "Ann"}"""))

    CodecTestKit.roundTrip[Kennel](Boarder("Rex")) shouldBe Boarder("Rex")
    CodecTestKit.roundTrip[Kennel](Warden("Ann")) shouldBe Warden("Ann")
  }

  "The same values in an unrelated hierarchy" should "stay valid, since uniqueness is per hierarchy" in {
    val registry: CodecRegistry = RegistryBuilder.from(primitives).registerSealed[Toy].build

    given codec: Codec[Toy] = registry.get(classOf[Toy])

    CodecTestKit.assertBsonStructure[Toy](ChewToy("Bone"), BsonDocument.parse("""{"_type": "dog", "name": "Bone"}"""))
    CodecTestKit.assertBsonStructure[Toy](Scratcher("Post"), BsonDocument.parse("""{"_type": "cat", "name": "Post"}"""))

    CodecTestKit.roundTrip[Toy](ChewToy("Bone")) shouldBe ChewToy("Bone")
  }

  "Values differing only in case" should "stay valid, since discriminators compare as exact strings" in {
    val registry: CodecRegistry = RegistryBuilder.from(primitives).registerSealed[Cage].build

    given codec: Codec[Cage] = registry.get(classOf[Cage])

    CodecTestKit.assertBsonStructure[Cage](Budgie("Blue"), BsonDocument.parse("""{"_type": "bird", "name": "Blue"}"""))
    CodecTestKit.assertBsonStructure[Cage](Parrot("Coco"), BsonDocument.parse("""{"_type": "Bird", "name": "Coco"}"""))

    CodecTestKit.roundTrip[Cage](Budgie("Blue")) shouldBe Budgie("Blue")
    CodecTestKit.roundTrip[Cage](Parrot("Coco")) shouldBe Parrot("Coco")
  }
end DuplicateDiscriminatorSpec
