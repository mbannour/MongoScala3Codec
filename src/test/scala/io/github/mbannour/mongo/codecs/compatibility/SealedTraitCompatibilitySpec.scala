package io.github.mbannour.mongo.codecs.compatibility

import org.bson.{BsonDocument, BsonInvalidOperationException}
import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecConfig, CodecTestKit, EnumValueCodecProvider, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

// Declared at the top level: the enum decoder resolves its companion by name at runtime, which only
// works for enums that are not nested inside a class or object. See SealedTraitCompatibilitySpec.
enum Colour:
  case Red, Green, Blue

case class Paint(name: String, colour: Colour)

sealed trait Animal
case class Dog(name: String, breed: String) extends Animal
case class Cat(name: String, lives: Int) extends Animal

case class Household(owner: String, pet: Animal)

sealed trait Transport
sealed trait Motorized extends Transport
case class Bus(capacity: Int) extends Motorized
case class Bicycle(gears: Int) extends Transport

/** BSON compatibility tests for sealed-trait ADTs and Scala 3 enums. See [[UserCompatibilitySpec]] for the package-level contract.
  *
  * These freeze the discriminator wire format: documents written today must stay readable by later releases, so neither the discriminator
  * field name, its value, nor its placement may drift silently.
  */
class SealedTraitCompatibilitySpec extends AnyFlatSpec with Matchers:

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.BooleanCodec()
  )

  private val animalRegistry: CodecRegistry = RegistryBuilder.from(primitives).registerSealed[Animal].build
  private given animalCodec: Codec[Animal] = animalRegistry.get(classOf[Animal])

  "A sealed trait subtype encoded through the root codec" should "carry a _type discriminator ahead of its payload" in {
    CodecTestKit.assertBsonStructure[Animal](
      Dog("Rex", "Labrador"),
      BsonDocument.parse("""{"_type": "Dog", "name": "Rex", "breed": "Labrador"}""")
    )

    CodecTestKit.assertBsonStructure[Animal](
      Cat("Tom", 9),
      BsonDocument.parse("""{"_type": "Cat", "name": "Tom", "lives": 9}""")
    )
  }

  it should "use the simple type name as the discriminator value" in {
    CodecTestKit.toBsonDocument[Animal](Dog("Rex", "Labrador")).getString("_type").getValue shouldBe "Dog"
  }

  it should "round-trip through the root codec" in {
    CodecTestKit.roundTrip[Animal](Dog("Rex", "Labrador")) shouldBe Dog("Rex", "Labrador")
    CodecTestKit.roundTrip[Animal](Cat("Tom", 9)) shouldBe Cat("Tom", 9)
  }

  "A concrete subtype codec" should "also write the discriminator, so directly encoded values stay decodable as the trait" in {
    val dogCodec: Codec[Dog] = animalRegistry.get(classOf[Dog])

    CodecTestKit.assertBsonStructure(
      Dog("Rex", "Labrador"),
      BsonDocument.parse("""{"_type": "Dog", "name": "Rex", "breed": "Labrador"}""")
    )(using dogCodec)
  }

  "Golden ADT documents" should "decode directly into the right subtype" in {
    val golden = BsonDocument.parse("""{"_type": "Dog", "name": "Rex", "breed": "Labrador"}""")

    CodecTestKit.fromBsonDocument[Animal](golden) shouldBe Dog("Rex", "Labrador")
  }

  it should "decode even when the discriminator is not the first field" in {
    val golden = BsonDocument.parse("""{"name": "Rex", "breed": "Labrador", "_type": "Dog"}""")

    CodecTestKit.fromBsonDocument[Animal](golden) shouldBe Dog("Rex", "Labrador")
  }

  "A sealed trait used as a field" should "nest the discriminator inside that field's document" in {
    val registry = RegistryBuilder.from(primitives).registerSealed[Animal].register[Household].build

    given codec: Codec[Household] = registry.get(classOf[Household])

    CodecTestKit.assertBsonStructure(
      Household("Ann", Cat("Tom", 9)),
      BsonDocument.parse("""{"owner": "Ann", "pet": {"_type": "Cat", "name": "Tom", "lives": 9}}""")
    )

    CodecTestKit.roundTrip(Household("Ann", Cat("Tom", 9))) shouldBe Household("Ann", Cat("Tom", 9))
  }

  "A multi-level hierarchy" should "flatten to the concrete type name, with no record of the intermediate trait" in {
    val registry = RegistryBuilder.from(primitives).registerSealed[Transport].build

    given codec: Codec[Transport] = registry.get(classOf[Transport])

    // Bus extends Motorized extends Transport, yet only "Bus" is recorded.
    CodecTestKit.assertBsonStructure[Transport](Bus(40), BsonDocument.parse("""{"_type": "Bus", "capacity": 40}"""))
    CodecTestKit.assertBsonStructure[Transport](Bicycle(21), BsonDocument.parse("""{"_type": "Bicycle", "gears": 21}"""))

    CodecTestKit.roundTrip[Transport](Bus(40)) shouldBe Bus(40)
  }

  "A configured discriminator field name" should "replace the default _type in the frozen representation" in {
    val registry = RegistryBuilder
      .from(primitives)
      .withConfig(CodecConfig(discriminatorField = "_class"))
      .registerSealed[Animal]
      .build

    given codec: Codec[Animal] = registry.get(classOf[Animal])

    CodecTestKit.assertBsonStructure[Animal](
      Dog("Rex", "Labrador"),
      BsonDocument.parse("""{"_class": "Dog", "name": "Rex", "breed": "Labrador"}""")
    )
  }

  "A document with no discriminator" should "fail to decode with a message naming the missing field" in {
    val document = BsonDocument.parse("""{"name": "Rex", "breed": "Labrador"}""")

    val error = intercept[BsonInvalidOperationException] {
      CodecTestKit.fromBsonDocument[Animal](document)
    }

    error.getMessage should include("Missing discriminator field '_type'")
  }

  "A document with an unknown discriminator value" should "fail to decode and list the known discriminators" in {
    val document = BsonDocument.parse("""{"_type": "Hamster", "name": "Nibbles"}""")

    val error = intercept[BsonInvalidOperationException] {
      CodecTestKit.fromBsonDocument[Animal](document)
    }

    error.getMessage should include("Unknown discriminator value 'Hamster'")
    error.getMessage should include("Dog")
    error.getMessage should include("Cat")
  }

  "A Scala 3 enum registered as a string enum" should "encode as its case name, with no discriminator" in {
    val base = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(EnumValueCodecProvider.forStringEnum[Colour]),
      primitives
    )
    val registry = RegistryBuilder.from(base).register[Paint].build

    given codec: Codec[Paint] = registry.get(classOf[Paint])

    CodecTestKit.assertBsonStructure(Paint("wall", Colour.Green), BsonDocument.parse("""{"name": "wall", "colour": "Green"}"""))
    CodecTestKit.roundTrip(Paint("wall", Colour.Green)) shouldBe Paint("wall", Colour.Green)
  }

  "A Scala 3 enum registered as an ordinal enum" should "encode as its ordinal" in {
    val base = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(EnumValueCodecProvider.forOrdinalEnum[Colour]),
      primitives
    )
    val registry = RegistryBuilder.from(base).register[Paint].build

    given codec: Codec[Paint] = registry.get(classOf[Paint])

    CodecTestKit.assertBsonStructure(Paint("wall", Colour.Green), BsonDocument.parse("""{"name": "wall", "colour": 1}"""))
    CodecTestKit.roundTrip(Paint("wall", Colour.Green)) shouldBe Paint("wall", Colour.Green)
  }
end SealedTraitCompatibilitySpec
