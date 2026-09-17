package io.github.mbannour.mongo.codecs.compatibility

import org.bson.{BsonDocument, BsonInt32, BsonInvalidOperationException, BsonString, BsonType}
import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecConfig, CodecTestKit, EnumValueCodecProvider, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

// Declared at the top level, which is the shape whose wire format these tests freeze. Enums nested in
// an object or class encode and decode identically; see NestedEnumSpec.
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

  /** Key order is deliberately *not* part of the contract: `BsonDocument` equality is `Map` equality, so every golden in this package
    * compares structurally and a future change to field order would not fail them.
    *
    * Discriminator placement is the one exception, asserted here on its own rather than left to ride on document equality that would never
    * have caught it. A reader that streams a document decides which subtype to build before it consumes the payload, so the discriminator
    * arriving first is a property of what this library writes - even though our own decoder does not require it, as the test below shows.
    */
  it should "write the discriminator as the first key, at the root and inside a nested document" in {
    import scala.jdk.CollectionConverters.*

    CodecTestKit.toBsonDocument[Animal](Dog("Rex", "Labrador")).keySet().asScala.head shouldBe "_type"

    val registry = RegistryBuilder.from(primitives).registerSealed[Animal].register[Household].build
    val householdCodec: Codec[Household] = registry.get(classOf[Household])

    val nested = CodecTestKit.toBsonDocument(Household("Ann", Dog("Rex", "Labrador")))(using householdCodec)

    nested.getDocument("pet").keySet().asScala.head shouldBe "_type"
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

    CodecTestKit.fromBsonDocument[Animal](
      BsonDocument.parse("""{"_class": "Dog", "name": "Rex", "breed": "Labrador"}""")
    ) shouldBe Dog("Rex", "Labrador")

    CodecTestKit.roundTrip[Animal](Dog("Rex", "Labrador")) shouldBe Dog("Rex", "Labrador")
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

    val expected = new BsonDocument()
      .append("name", new BsonString("wall"))
      .append("colour", new BsonString("Green"))

    CodecTestKit.assertBsonStructure(Paint("wall", Colour.Green), expected)
    CodecTestKit.fromBsonDocument[Paint](expected) shouldBe Paint("wall", Colour.Green)
    CodecTestKit.roundTrip(Paint("wall", Colour.Green)) shouldBe Paint("wall", Colour.Green)
  }

  it should "reject a stored value that names no case of the enum" in {
    val base = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(EnumValueCodecProvider.forStringEnum[Colour]),
      primitives
    )
    val registry = RegistryBuilder.from(base).register[Paint].build

    given codec: Codec[Paint] = registry.get(classOf[Paint])

    val error = intercept[RuntimeException] {
      CodecTestKit.fromBsonDocument[Paint](
        new BsonDocument().append("name", new BsonString("wall")).append("colour", new BsonString("Puce"))
      )
    }

    error.getMessage should include("Puce")
  }

  "A Scala 3 enum registered as an ordinal enum" should "encode as its ordinal" in {
    val base = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(EnumValueCodecProvider.forOrdinalEnum[Colour]),
      primitives
    )
    val registry = RegistryBuilder.from(base).register[Paint].build

    given codec: Codec[Paint] = registry.get(classOf[Paint])

    // Written as Int32, not Int64: the ordinal is stated as a typed value rather than left to a JSON
    // literal, because the numeric type is part of what a stored document commits to.
    val expected = new BsonDocument()
      .append("name", new BsonString("wall"))
      .append("colour", new BsonInt32(1))

    CodecTestKit.assertBsonStructure(Paint("wall", Colour.Green), expected)
    CodecTestKit.toBsonDocument(Paint("wall", Colour.Green)).get("colour").getBsonType shouldBe BsonType.INT32
    CodecTestKit.fromBsonDocument[Paint](expected) shouldBe Paint("wall", Colour.Green)
    CodecTestKit.roundTrip(Paint("wall", Colour.Green)) shouldBe Paint("wall", Colour.Green)
  }

  // An ordinal is a position, not a name, so reordering or inserting enum cases silently changes what every
  // stored document means. Recorded here because it is the one enum representation with no self-describing
  // value to fall back on; the library offers no migration for it.
  it should "read an ordinal back by position, which is what makes case order part of the stored contract" in {
    val base = CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(EnumValueCodecProvider.forOrdinalEnum[Colour]),
      primitives
    )
    val registry = RegistryBuilder.from(base).register[Paint].build

    given codec: Codec[Paint] = registry.get(classOf[Paint])

    CodecTestKit.fromBsonDocument[Paint](
      new BsonDocument().append("name", new BsonString("wall")).append("colour", new BsonInt32(0))
    ) shouldBe Paint("wall", Colour.Red)

    CodecTestKit.fromBsonDocument[Paint](
      new BsonDocument().append("name", new BsonString("wall")).append("colour", new BsonInt32(2))
    ) shouldBe Paint("wall", Colour.Blue)
  }
end SealedTraitCompatibilitySpec
