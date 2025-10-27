package io.github.mbannour.mongo.codecs

import scala.util.{Failure, Success, Try}

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** Property-based tests for codec round-trip encoding/decoding using ScalaCheck.
  *
  * These tests verify that for any randomly generated value, encoding to BSON and then decoding back produces an identical value.
  */
class PropertyBasedCodecSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  // Test models
  case class SimplePerson(_id: ObjectId, name: String, age: Int)
  case class PersonWithOption(_id: ObjectId, name: String, email: Option[String], age: Int)
  case class Address(_id: ObjectId, street: String, city: String, zipCode: Int)
  case class PersonWithNested(_id: ObjectId, name: String, address: Address)
  case class CollectionHolder(_id: ObjectId, tags: List[String], scores: Seq[Int])
  case class MapHolder(_id: ObjectId, attributes: Map[String, String])

  // Custom generators for test data
  given Arbitrary[ObjectId] = Arbitrary(Gen.const(new ObjectId()))

  given Arbitrary[SimplePerson] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    age <- Gen.choose(0, 120)
  yield SimplePerson(id, name, age))

  given Arbitrary[PersonWithOption] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    email <- Gen.option(Gen.alphaNumStr.suchThat(_.nonEmpty).map(s => s"$s@example.com"))
    age <- Gen.choose(0, 120)
  yield PersonWithOption(id, name, email, age))

  given Arbitrary[Address] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    street <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    city <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    zipCode <- Gen.choose(10000, 99999)
  yield Address(id, street, city, zipCode))

  given Arbitrary[PersonWithNested] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    address <- Arbitrary.arbitrary[Address]
  yield PersonWithNested(id, name, address))

  given Arbitrary[CollectionHolder] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    tags <- Gen.listOfN(5, Gen.alphaNumStr.suchThat(_.nonEmpty))
    scores <- Gen.listOfN(3, Gen.choose(0, 100))
  yield CollectionHolder(id, tags, scores))

  given Arbitrary[MapHolder] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    keys <- Gen.listOfN(3, Gen.alphaNumStr.suchThat(_.nonEmpty))
    values <- Gen.listOfN(3, Gen.alphaNumStr.suchThat(_.nonEmpty))
    attributes = keys.zip(values).toMap
  yield MapHolder(id, attributes))

  // Generators for Either and Try
  given [L: Arbitrary, R: Arbitrary]: Arbitrary[Either[L, R]] = Arbitrary(
    Gen.oneOf(
      Arbitrary.arbitrary[L].map(Left(_)),
      Arbitrary.arbitrary[R].map(Right(_))
    )
  )

  given [T: Arbitrary]: Arbitrary[Try[T]] = Arbitrary(
    Gen.oneOf(
      Arbitrary.arbitrary[T].map(Success(_)),
      Gen.alphaNumStr.map(msg => Failure(new Exception(msg)))
    )
  )

  // Default BSON codec registry
  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  "SimplePerson codec" should "round-trip correctly for all generated values" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[SimplePerson]
      .build

    given codec: Codec[SimplePerson] = registry.get(classOf[SimplePerson])

    forAll { (person: SimplePerson) =>
      val roundTripped = CodecTestKit.roundTrip(person)
      roundTripped shouldBe person
    }
  }

  "PersonWithOption codec" should "round-trip correctly with Some values" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .encodeNone
      .register[PersonWithOption]
      .build

    given codec: Codec[PersonWithOption] = registry.get(classOf[PersonWithOption])

    forAll { (person: PersonWithOption) =>
      val roundTripped = CodecTestKit.roundTrip(person)
      roundTripped shouldBe person
    }
  }

  it should "round-trip correctly with NoneHandling.Ignore" in {
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .withConfig(config)
      .register[PersonWithOption]
      .build

    given codec: Codec[PersonWithOption] = registry.get(classOf[PersonWithOption])

    forAll { (person: PersonWithOption) =>
      val roundTripped = CodecTestKit.roundTrip(person)
      roundTripped shouldBe person
    }
  }

  "PersonWithNested codec" should "round-trip correctly with nested case classes" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Address]
      .register[PersonWithNested]
      .build

    given codec: Codec[PersonWithNested] = registry.get(classOf[PersonWithNested])

    forAll { (person: PersonWithNested) =>
      val roundTripped = CodecTestKit.roundTrip(person)
      roundTripped shouldBe person
    }
  }

  "CollectionHolder codec" should "round-trip correctly with List and Seq" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[CollectionHolder]
      .build

    given codec: Codec[CollectionHolder] = registry.get(classOf[CollectionHolder])

    forAll { (holder: CollectionHolder) =>
      val roundTripped = CodecTestKit.roundTrip(holder)
      roundTripped.tags shouldBe holder.tags
      roundTripped.scores shouldBe holder.scores
    }
  }

  "MapHolder codec" should "round-trip correctly with Map fields" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[MapHolder]
      .build

    given codec: Codec[MapHolder] = registry.get(classOf[MapHolder])

    forAll { (holder: MapHolder) =>
      val roundTripped = CodecTestKit.roundTrip(holder)
      roundTripped.attributes shouldBe holder.attributes
    }
  }

  // Edge case tests
  "Codec" should "handle empty strings" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[SimplePerson]
      .build

    given codec: Codec[SimplePerson] = registry.get(classOf[SimplePerson])

    val person = SimplePerson(new ObjectId(), "", 25)
    val roundTripped = CodecTestKit.roundTrip(person)
    roundTripped shouldBe person
  }

  it should "handle Unicode characters" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[SimplePerson]
      .build

    given codec: Codec[SimplePerson] = registry.get(classOf[SimplePerson])

    val person = SimplePerson(new ObjectId(), "José María 日本語 🎉", 30)
    val roundTripped = CodecTestKit.roundTrip(person)
    roundTripped shouldBe person
  }

  it should "handle boundary values for Int" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[SimplePerson]
      .build

    given codec: Codec[SimplePerson] = registry.get(classOf[SimplePerson])

    Seq(Int.MinValue, -1, 0, 1, Int.MaxValue).foreach { age =>
      val person = SimplePerson(new ObjectId(), "Test", age)
      val roundTripped = CodecTestKit.roundTrip(person)
      roundTripped shouldBe person
    }
  }

  it should "handle empty collections" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[CollectionHolder]
      .build

    given codec: Codec[CollectionHolder] = registry.get(classOf[CollectionHolder])

    val holder = CollectionHolder(new ObjectId(), List.empty, Seq.empty)
    val roundTripped = CodecTestKit.roundTrip(holder)
    roundTripped shouldBe holder
  }

  it should "handle empty maps" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[MapHolder]
      .build

    given codec: Codec[MapHolder] = registry.get(classOf[MapHolder])

    val holder = MapHolder(new ObjectId(), Map.empty)
    val roundTripped = CodecTestKit.roundTrip(holder)
    roundTripped shouldBe holder
  }

  // Test with default values
  case class WithDefaults(_id: ObjectId, name: String, status: String = "active", count: Int = 0)

  "Codec with default values" should "use defaults when fields are missing" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[WithDefaults]
      .build

    given codec: Codec[WithDefaults] = registry.get(classOf[WithDefaults])

    val obj = WithDefaults(new ObjectId(), "test")
    val roundTripped = CodecTestKit.roundTrip(obj)
    roundTripped shouldBe obj
    roundTripped.status shouldBe "active"
    roundTripped.count shouldBe 0
  }
end PropertyBasedCodecSpec
