package io.github.mbannour.mongo.codecs

import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.{BsonDocument, BsonInvalidOperationException}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonDiscriminator
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

sealed trait Animal

@BsonDiscriminator("dog")
case class Dog(name: String) extends Animal

case class Cat(name: String) extends Animal

case class Owner(name: String, pet: Animal)

sealed trait Vehicle

@BsonDiscriminator("car")
case class Car(doors: Int) extends Vehicle

@BsonDiscriminator("van")
case class Van(load: Int) extends Vehicle

sealed trait Transport
sealed trait Motorized extends Transport

@BsonDiscriminator("bus")
case class Bus(seats: Int) extends Motorized

case class Bicycle(gears: Int) extends Transport

/** `@BsonDiscriminator` sets the discriminator value for one concrete subtype. Everything else about the sealed-trait representation frozen
  * by [[io.github.mbannour.mongo.codecs.compatibility.SealedTraitCompatibilitySpec]] stays as it was.
  */
class BsonDiscriminatorSpec extends AnyFlatSpec with Matchers:

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec()
  )

  private val animalRegistry: CodecRegistry = RegistryBuilder.from(primitives).registerSealed[Animal].build
  private given animalCodec: Codec[Animal] = animalRegistry.get(classOf[Animal])

  "An annotated subtype" should "encode with the annotation value as its discriminator" in {
    CodecTestKit.assertBsonStructure[Animal](
      Dog("Rex"),
      BsonDocument.parse("""{"_type": "dog", "name": "Rex"}""")
    )
  }

  it should "still write the discriminator first" in {
    CodecTestKit.toBsonDocument[Animal](Dog("Rex")).keySet().iterator().next() shouldBe "_type"
  }

  it should "round-trip through the root codec" in {
    CodecTestKit.roundTrip[Animal](Dog("Rex")) shouldBe Dog("Rex")
  }

  it should "decode a hand-written document that was never produced by encode" in {
    val golden = BsonDocument.parse("""{"_type": "dog", "name": "Rex"}""")

    CodecTestKit.fromBsonDocument[Animal](golden) shouldBe Dog("Rex")
  }

  "An unannotated subtype" should "keep the simple type name, unchanged from the frozen representation" in {
    CodecTestKit.assertBsonStructure[Animal](
      Cat("Milo"),
      BsonDocument.parse("""{"_type": "Cat", "name": "Milo"}""")
    )
  }

  it should "round-trip and decode old documents unchanged" in {
    CodecTestKit.roundTrip[Animal](Cat("Milo")) shouldBe Cat("Milo")

    val golden = BsonDocument.parse("""{"_type": "Cat", "name": "Milo"}""")
    CodecTestKit.fromBsonDocument[Animal](golden) shouldBe Cat("Milo")
  }

  "A hierarchy where every subtype is annotated" should "use each subtype's own value" in {
    val registry = RegistryBuilder.from(primitives).registerSealed[Vehicle].build

    given codec: Codec[Vehicle] = registry.get(classOf[Vehicle])

    CodecTestKit.assertBsonStructure[Vehicle](Car(5), BsonDocument.parse("""{"_type": "car", "doors": 5}"""))
    CodecTestKit.assertBsonStructure[Vehicle](Van(1200), BsonDocument.parse("""{"_type": "van", "load": 1200}"""))

    CodecTestKit.roundTrip[Vehicle](Car(5)) shouldBe Car(5)
    CodecTestKit.roundTrip[Vehicle](Van(1200)) shouldBe Van(1200)
  }

  "A configured discriminator field" should "carry the annotation value, and only under that field" in {
    val registry = RegistryBuilder
      .from(primitives)
      .withConfig(CodecConfig(discriminatorField = "_class"))
      .registerSealed[Animal]
      .build

    given codec: Codec[Animal] = registry.get(classOf[Animal])

    CodecTestKit.assertBsonStructure[Animal](
      Dog("Rex"),
      BsonDocument.parse("""{"_class": "dog", "name": "Rex"}""")
    )

    CodecTestKit.toBsonDocument[Animal](Dog("Rex")).containsKey("_type") shouldBe false
    CodecTestKit.roundTrip[Animal](Dog("Rex")) shouldBe Dog("Rex")
  }

  "An annotated subtype nested as a field" should "carry the annotation value inside that field's document" in {
    val registry = RegistryBuilder.from(primitives).registerSealed[Animal].register[Owner].build

    given codec: Codec[Owner] = registry.get(classOf[Owner])

    CodecTestKit.assertBsonStructure(
      Owner("Alice", Dog("Rex")),
      BsonDocument.parse("""{"name": "Alice", "pet": {"_type": "dog", "name": "Rex"}}""")
    )

    CodecTestKit.roundTrip(Owner("Alice", Dog("Rex"))) shouldBe Owner("Alice", Dog("Rex"))
  }

  "The concrete subtype codec" should "use the same discriminator value as the root codec" in {
    val dogCodec: Codec[Dog] = animalRegistry.get(classOf[Dog])

    CodecTestKit.assertBsonStructure(
      Dog("Rex"),
      BsonDocument.parse("""{"_type": "dog", "name": "Rex"}""")
    )(using dogCodec)

    CodecTestKit.roundTrip(Dog("Rex"))(using dogCodec) shouldBe Dog("Rex")
  }

  "A multi-level hierarchy" should "still flatten to the concrete subtype, using its annotation value" in {
    val registry = RegistryBuilder.from(primitives).registerSealed[Transport].build

    given codec: Codec[Transport] = registry.get(classOf[Transport])

    // Bus extends Motorized extends Transport, yet only the concrete subtype's value is recorded.
    CodecTestKit.assertBsonStructure[Transport](Bus(40), BsonDocument.parse("""{"_type": "bus", "seats": 40}"""))
    CodecTestKit.assertBsonStructure[Transport](Bicycle(21), BsonDocument.parse("""{"_type": "Bicycle", "gears": 21}"""))

    CodecTestKit.roundTrip[Transport](Bus(40)) shouldBe Bus(40)
    CodecTestKit.roundTrip[Transport](Bicycle(21)) shouldBe Bicycle(21)
  }

  // Characterization, not a wish: annotating a subtype deliberately changes what it persists, and no
  // alias for the old value is provided. Migrating existing documents is a separate concern.
  "A document written before the subtype was annotated" should "no longer decode" in {
    val oldDocument = BsonDocument.parse("""{"_type": "Dog", "name": "Rex"}""")

    val error = intercept[BsonInvalidOperationException] {
      CodecTestKit.fromBsonDocument[Animal](oldDocument)
    }

    error.getMessage should include("Unknown discriminator value 'Dog'")
  }
end BsonDiscriminatorSpec
