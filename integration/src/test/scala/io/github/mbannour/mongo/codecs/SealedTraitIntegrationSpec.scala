package io.github.mbannour.mongo.codecs

import scala.reflect.ClassTag

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter, BsonInvalidOperationException}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*
import io.github.mbannour.mongo.codecs.CodecTestKit

/** Integration tests for sealed trait codec generation with property-based testing.
  *
  * These tests verify that sealed traits work correctly with:
  *   - Automatic discriminator field generation
  *   - Round-trip encoding/decoding
  *   - Collections and nested structures
  *   - Property-based testing for robustness
  */
class SealedTraitIntegrationSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  sealed trait Animal
  case class Dog(name: String, breed: String) extends Animal
  case class Cat(name: String, lives: Int) extends Animal
  case class Bird(name: String, canFly: Boolean) extends Animal

  sealed trait Shape
  case class Circle(_id: ObjectId, radius: Double) extends Shape
  case class Rectangle(_id: ObjectId, width: Double, height: Double) extends Shape
  case class Triangle(_id: ObjectId, base: Double, height: Double) extends Shape

  case class Zoo(name: String, animals: List[Animal])
  case class Owner(name: String, pet: Animal)
  case class Shelter(location: String, residents: Vector[Animal])

  // Multi-level sealed hierarchy
  sealed trait Vehicle
  sealed trait Motorized extends Vehicle
  case class Car(make: String, model: String) extends Motorized
  case class Motorcycle(brand: String, cc: Int) extends Motorized
  case class Bicycle(brand: String, gears: Int) extends Vehicle

  // Sealed class (not trait)
  sealed class Status
  case class Active(since: Long) extends Status
  case class Inactive(reason: String) extends Status

  // Sealed abstract class
  sealed abstract class PaymentMethod
  case class CreditCard(number: String, cvv: String) extends PaymentMethod
  case class BankTransfer(accountNumber: String) extends PaymentMethod

  // Complex nested example
  case class Task(_id: ObjectId, name: String, status: Status, assignedTo: Option[Owner])

  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  given Arbitrary[ObjectId] = Arbitrary(Gen.const(new ObjectId()))

  given Arbitrary[Dog] = Arbitrary(for
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    breed <- Gen.oneOf("Labrador", "Beagle", "Poodle", "Bulldog", "Collie")
  yield Dog(name, breed))

  given Arbitrary[Cat] = Arbitrary(for
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    lives <- Gen.choose(1, 9)
  yield Cat(name, lives))

  given Arbitrary[Bird] = Arbitrary(for
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    canFly <- Arbitrary.arbitrary[Boolean]
  yield Bird(name, canFly))

  given Arbitrary[Animal] = Arbitrary(
    Gen.oneOf(
      Arbitrary.arbitrary[Dog],
      Arbitrary.arbitrary[Cat],
      Arbitrary.arbitrary[Bird]
    )
  )

  given Arbitrary[Circle] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    r <- Gen.choose(0.1, 1000.0)
  yield Circle(id, r))

  given Arbitrary[Rectangle] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    w <- Gen.choose(0.1, 1000.0)
    h <- Gen.choose(0.1, 1000.0)
  yield Rectangle(id, w, h))

  "registerSealed for Animal" should "create codecs for all subtypes" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    val animalCodec = registry.get(classOf[Animal])
    animalCodec should not be null
    animalCodec.getEncoderClass shouldBe classOf[Animal]

    val dogCodec = registry.get(classOf[Dog])
    dogCodec should not be null

    val catCodec = registry.get(classOf[Cat])
    catCodec should not be null
  }

  it should "round-trip Dog through Animal codec" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    given Codec[Animal] = registry.get(classOf[Animal])

    forAll { (dog: Dog) =>
      val animal: Animal = dog
      val roundtripped = CodecTestKit.roundTrip(animal)

      roundtripped shouldBe dog
      roundtripped shouldBe a[Dog]
    }
  }

  it should "round-trip Cat through Animal codec" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    given Codec[Animal] = registry.get(classOf[Animal])

    forAll { (cat: Cat) =>
      val animal: Animal = cat
      val roundtripped = CodecTestKit.roundTrip(animal)

      roundtripped shouldBe cat
      roundtripped shouldBe a[Cat]
    }
  }

  it should "round-trip Bird through Animal codec" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    given Codec[Animal] = registry.get(classOf[Animal])

    forAll { (bird: Bird) =>
      val animal: Animal = bird
      val roundtripped = CodecTestKit.roundTrip(animal)

      roundtripped shouldBe bird
      roundtripped shouldBe a[Bird]
    }
  }

  "Sealed trait codec" should "write discriminator field to BSON" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    val codec = registry.get(classOf[Animal])
    val dog: Animal = Dog("Rex", "Labrador")

    val doc = new BsonDocument()
    val writer = new BsonDocumentWriter(doc)
    codec.encode(writer, dog, org.bson.codecs.EncoderContext.builder().build())

    doc.containsKey("_type") shouldBe true
    doc.getString("_type").getValue shouldBe "Dog"
    doc.getString("name").getValue shouldBe "Rex"
    doc.getString("breed").getValue shouldBe "Labrador"
  }

  it should "use custom discriminator field name" in {
    val config = CodecConfig(discriminatorField = "_class")
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .withConfig(config)
      .registerSealed[Animal]
      .build

    val codec = registry.get(classOf[Animal])
    val cat: Animal = Cat("Fluffy", 7)

    val doc = new BsonDocument()
    val writer = new BsonDocumentWriter(doc)
    codec.encode(writer, cat, org.bson.codecs.EncoderContext.builder().build())

    doc.containsKey("_class") shouldBe true
    doc.containsKey("_type") shouldBe false
    doc.getString("_class").getValue shouldBe "Cat"
  }

  "registerSealed for Shape" should "round-trip shapes with ObjectId" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Shape]
      .build

    given Codec[Shape] = registry.get(classOf[Shape])

    forAll { (circle: Circle) =>
      val shape: Shape = circle
      val roundtripped = CodecTestKit.roundTrip(shape)

      roundtripped shouldBe circle
      roundtripped.asInstanceOf[Circle]._id shouldBe circle._id
    }
  }

  it should "handle multiple shape types" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Shape]
      .build

    val codec = registry.get(classOf[Shape])

    val shapes: Seq[Shape] = Seq(
      Circle(new ObjectId(), 5.0),
      Rectangle(new ObjectId(), 10.0, 20.0),
      Triangle(new ObjectId(), 6.0, 8.0)
    )

    shapes.foreach { shape =>
      val roundtripped = CodecTestKit.roundTrip(shape)(using codec)
      roundtripped shouldBe shape
    }
  }

  "registerSealed with nested structures" should "handle sealed trait as field" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .build

    val owner = Owner("Alice", Dog("Buddy", "Golden Retriever"))
    given Codec[Owner] = registry.get(classOf[Owner])

    val roundtripped = CodecTestKit.roundTrip(owner)

    roundtripped shouldBe owner
    roundtripped.pet shouldBe a[Dog]
  }

  it should "handle List of sealed types" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .register[Zoo]
      .build

    val zoo = Zoo(
      "City Zoo",
      List(
        Dog("Rex", "Shepherd"),
        Cat("Whiskers", 9),
        Bird("Tweety", true),
        Dog("Max", "Beagle")
      )
    )

    given Codec[Zoo] = registry.get(classOf[Zoo])
    val roundtripped = CodecTestKit.roundTrip(zoo)

    roundtripped shouldBe zoo
    roundtripped.animals.size shouldBe 4
    roundtripped.animals(0) shouldBe a[Dog]
    roundtripped.animals(1) shouldBe a[Cat]
    roundtripped.animals(2) shouldBe a[Bird]
    roundtripped.animals(3) shouldBe a[Dog]
  }

  it should "handle Vector of sealed types" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .register[Shelter]
      .build

    val shelter = Shelter(
      "Downtown",
      Vector(
        Cat("Luna", 3),
        Dog("Charlie", "Mixed"),
        Cat("Shadow", 5)
      )
    )

    given Codec[Shelter] = registry.get(classOf[Shelter])
    val roundtripped = CodecTestKit.roundTrip(shelter)

    roundtripped shouldBe shelter
    roundtripped.residents.size shouldBe 3
  }

  "registerSealed for multi-level hierarchy" should "handle nested sealed traits" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Vehicle]
      .build

    val codec = registry.get(classOf[Vehicle])

    val vehicles: Seq[Vehicle] = Seq(
      Car("Toyota", "Camry"),
      Motorcycle("Harley", 1200),
      Bicycle("Trek", 21)
    )

    vehicles.foreach { vehicle =>
      val roundtripped = CodecTestKit.roundTrip(vehicle)(using codec)
      roundtripped shouldBe vehicle
    }
  }

  "registerSealed for sealed class" should "work like sealed trait" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Status]
      .build

    val codec = registry.get(classOf[Status])

    val active: Status = Active(System.currentTimeMillis())
    val inactive: Status = Inactive("Completed")

    CodecTestKit.roundTrip(active)(using codec) shouldBe active
    CodecTestKit.roundTrip(inactive)(using codec) shouldBe inactive
  }

  "registerSealed for sealed abstract class" should "work correctly" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[PaymentMethod]
      .build

    val codec = registry.get(classOf[PaymentMethod])

    val creditCard: PaymentMethod = CreditCard("1234-5678-9012-3456", "123")
    val bankTransfer: PaymentMethod = BankTransfer("GB29NWBK60161331926819")

    CodecTestKit.roundTrip(creditCard)(using codec) shouldBe creditCard
    CodecTestKit.roundTrip(bankTransfer)(using codec) shouldBe bankTransfer
  }

  "registerSealed in complex scenario" should "handle multiple sealed traits" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Status]
      .registerSealed[Animal]
      .register[Owner]
      .register[Task]
      .build

    val task = Task(
      _id = new ObjectId(),
      name = "Review code",
      status = Active(System.currentTimeMillis()),
      assignedTo = Some(Owner("Bob", Dog("Buddy", "Labrador")))
    )

    given Codec[Task] = registry.get(classOf[Task])
    val roundtripped = CodecTestKit.roundTrip(task)

    roundtripped shouldBe task
    roundtripped.status shouldBe a[Active]
    roundtripped.assignedTo.get.pet shouldBe a[Dog]
  }

  "Sealed trait codec" should "error on missing discriminator" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    val codec = registry.get(classOf[Animal])

    val doc = new BsonDocument()
    doc.put("name", new org.bson.BsonString("Mystery"))
    doc.put("breed", new org.bson.BsonString("Unknown"))

    val reader = new BsonDocumentReader(doc)
    val context = org.bson.codecs.DecoderContext.builder().build()

    val exception = intercept[BsonInvalidOperationException] {
      codec.decode(reader, context)
    }

    exception.getMessage should include("discriminator")
  }

  it should "error on unknown discriminator value" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    val codec = registry.get(classOf[Animal])

    val doc = new BsonDocument()
    doc.put("_type", new org.bson.BsonString("Elephant"))
    doc.put("name", new org.bson.BsonString("Dumbo"))

    val reader = new BsonDocumentReader(doc)
    val context = org.bson.codecs.DecoderContext.builder().build()

    val exception = intercept[BsonInvalidOperationException] {
      codec.decode(reader, context)
    }

    exception.getMessage should include("Unknown discriminator")
    exception.getMessage should include("Elephant")
  }

  "Property-based testing" should "verify round-trip for arbitrary animals" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    given Codec[Animal] = registry.get(classOf[Animal])

    forAll { (animal: Animal) =>
      whenever(animal != null) {
        val roundtripped = CodecTestKit.roundTrip(animal)
        roundtripped shouldBe animal
      }
    }
  }

  it should "verify discriminator field presence" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    val codec = registry.get(classOf[Animal])

    forAll { (dog: Dog) =>
      val doc = new BsonDocument()
      val writer = new BsonDocumentWriter(doc)
      codec.encode(writer, dog, org.bson.codecs.EncoderContext.builder().build())

      doc.containsKey("_type") shouldBe true
      doc.getString("_type").getValue should (be("Dog") or be("Cat") or be("Bird"))
    }
  }

  "registerSealedAll" should "register multiple sealed traits efficiently" in {
    // Register multiple sealed traits at once - more efficient than separate calls
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealedAll[(Animal, Shape, Vehicle)]
      .build

    // Verify Animal codec works
    val animalCodec = registry.get(classOf[Animal])
    val dog: Animal = Dog("Rex", "Labrador")
    CodecTestKit.roundTrip(dog)(using animalCodec) shouldBe dog

    // Verify Shape codec works
    val shapeCodec = registry.get(classOf[Shape])
    val circle: Shape = Circle(new ObjectId(), 5.0)
    CodecTestKit.roundTrip(circle)(using shapeCodec) shouldBe circle

    // Verify Vehicle codec works
    val vehicleCodec = registry.get(classOf[Vehicle])
    val car: Vehicle = Car("Toyota", "Camry")
    CodecTestKit.roundTrip(car)(using vehicleCodec) shouldBe car
  }

  it should "handle complex scenario with registerSealedAll" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealedAll[(Animal, Status)]
      .register[Task]
      .register[Owner]
      .build

    // Verify complex nested structure with multiple sealed traits
    val task = Task(
      _id = new ObjectId(),
      name = "Walk the dog",
      status = Active(System.currentTimeMillis()),
      assignedTo = Some(Owner("Alice", Dog("Max", "Beagle")))
    )

    given Codec[Task] = registry.get(classOf[Task])
    val roundtripped = CodecTestKit.roundTrip(task)

    roundtripped shouldBe task
    roundtripped.status shouldBe a[Active]
    roundtripped.assignedTo.get.pet shouldBe a[Dog]
  }

  it should "be equivalent to multiple registerSealed calls but more efficient" in {
    // Using registerSealedAll
    val registry1 = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealedAll[(Animal, Vehicle)]
      .build

    // Using separate registerSealed calls
    val registry2 = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .registerSealed[Vehicle]
      .build

    // Both should work identically
    val dog: Animal = Dog("Buddy", "Golden")
    val car: Vehicle = Car("Honda", "Accord")

    val codec1Animal = registry1.get(classOf[Animal])
    val codec2Animal = registry2.get(classOf[Animal])

    CodecTestKit.roundTrip(dog)(using codec1Animal) shouldBe dog
    CodecTestKit.roundTrip(dog)(using codec2Animal) shouldBe dog

    val codec1Vehicle = registry1.get(classOf[Vehicle])
    val codec2Vehicle = registry2.get(classOf[Vehicle])

    CodecTestKit.roundTrip(car)(using codec1Vehicle) shouldBe car
    CodecTestKit.roundTrip(car)(using codec2Vehicle) shouldBe car
  }

  // ===== Concrete-subtype codec must include discriminator (Updates.set path) =====

  it should "include discriminator when encoding concrete subtype directly via registerSealedAll" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealedAll[(Animal, Shape, Vehicle)]
      .build

    // Animal subtype encoded via concrete codec
    val dogDoc = new BsonDocument()
    val dogWriter = new BsonDocumentWriter(dogDoc)
    registry.get(classOf[Dog]).encode(dogWriter, Dog("Rex", "Labrador"), org.bson.codecs.EncoderContext.builder().build())

    dogDoc.containsKey("_type") shouldBe true
    dogDoc.getString("_type").getValue shouldBe "Dog"

    // Shape subtype encoded via concrete codec
    val circleDoc = new BsonDocument()
    val circleWriter = new BsonDocumentWriter(circleDoc)
    registry.get(classOf[Circle]).encode(circleWriter, Circle(new ObjectId(), 5.0), org.bson.codecs.EncoderContext.builder().build())

    circleDoc.containsKey("_type") shouldBe true
    circleDoc.getString("_type").getValue shouldBe "Circle"

    // Vehicle subtype encoded via concrete codec
    val carDoc = new BsonDocument()
    val carWriter = new BsonDocumentWriter(carDoc)
    registry.get(classOf[Car]).encode(carWriter, Car("Toyota", "Camry"), org.bson.codecs.EncoderContext.builder().build())

    carDoc.containsKey("_type") shouldBe true
    carDoc.getString("_type").getValue shouldBe "Car"

    // Sealed codecs can decode what the concrete codecs produced
    val animalCodec  = registry.get(classOf[Animal])
    val shapeCodec   = registry.get(classOf[Shape])
    val vehicleCodec = registry.get(classOf[Vehicle])

    animalCodec.decode(
      new BsonDocumentReader(dogDoc), org.bson.codecs.DecoderContext.builder().build()
    ) shouldBe Dog("Rex", "Labrador")

    shapeCodec.decode(
      new BsonDocumentReader(circleDoc), org.bson.codecs.DecoderContext.builder().build()
    ) shouldBe a[Circle]

    vehicleCodec.decode(
      new BsonDocumentReader(carDoc), org.bson.codecs.DecoderContext.builder().build()
    ) shouldBe Car("Toyota", "Camry")
  }

  it should "use custom discriminator field when encoding concrete subtype directly" in {
    val config = CodecConfig(discriminatorField = "_class")
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .withConfig(config)
      .registerSealed[Animal]
      .build

    // Encode via concrete-class codec
    val catDoc = new BsonDocument()
    val catWriter = new BsonDocumentWriter(catDoc)
    registry.get(classOf[Cat]).encode(catWriter, Cat("Fluffy", 7), org.bson.codecs.EncoderContext.builder().build())

    catDoc.containsKey("_class") shouldBe true
    catDoc.containsKey("_type") shouldBe false
    catDoc.getString("_class").getValue shouldBe "Cat"

    // Sealed codec (also configured with _class) must decode it correctly
    val animalCodec = registry.get(classOf[Animal])
    val result = animalCodec.decode(
      new BsonDocumentReader(catDoc), org.bson.codecs.DecoderContext.builder().build()
    )
    result shouldBe Cat("Fluffy", 7)
    result shouldBe a[Cat]
  }

  "Property-based: direct subtype encoding" should "always carry the discriminator field" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    // Encode using the CONCRETE class codecs (as Updates.set would)
    forAll { (dog: Dog) =>
      val doc = new BsonDocument()
      val writer = new BsonDocumentWriter(doc)
      registry.get(classOf[Dog]).encode(writer, dog, org.bson.codecs.EncoderContext.builder().build())

      doc.containsKey("_type") shouldBe true
      doc.getString("_type").getValue shouldBe "Dog"
    }

    forAll { (cat: Cat) =>
      val doc = new BsonDocument()
      val writer = new BsonDocumentWriter(doc)
      registry.get(classOf[Cat]).encode(writer, cat, org.bson.codecs.EncoderContext.builder().build())

      doc.containsKey("_type") shouldBe true
      doc.getString("_type").getValue shouldBe "Cat"
    }

    forAll { (bird: Bird) =>
      val doc = new BsonDocument()
      val writer = new BsonDocumentWriter(doc)
      registry.get(classOf[Bird]).encode(writer, bird, org.bson.codecs.EncoderContext.builder().build())

      doc.containsKey("_type") shouldBe true
      doc.getString("_type").getValue shouldBe "Bird"
    }
  }

  "Property-based: direct subtype encode → sealed decode" should "round-trip correctly for arbitrary animals" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .build

    val animalCodec = registry.get(classOf[Animal])

    forAll { (dog: Dog) =>
      val doc = new BsonDocument()
      registry.get(classOf[Dog]).encode(new BsonDocumentWriter(doc), dog, org.bson.codecs.EncoderContext.builder().build())
      val decoded = animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build())
      decoded shouldBe dog
      decoded shouldBe a[Dog]
    }

    forAll { (bird: Bird) =>
      val doc = new BsonDocument()
      registry.get(classOf[Bird]).encode(new BsonDocumentWriter(doc), bird, org.bson.codecs.EncoderContext.builder().build())
      val decoded = animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build())
      decoded shouldBe bird
      decoded shouldBe a[Bird]
    }
  }

  // ===== replaceOne / findOneAndReplace: outer-document codec path =====
  //
  // replaceOne and findOneAndReplace encode the full replacement document via the
  // outer type codec, which delegates to the sealed-trait codec for sealed fields.
  // This is a different code path from Updates.set (concrete subtype codec).

  "Property-based: replaceOne/findOneAndReplace simulation" should
      "always encode the sealed field with a discriminator via the outer-document codec" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .build

    val ownerCodec = registry.get(classOf[Owner])

    forAll { (animal: Animal) =>
      val owner = Owner("TestOwner", animal)
      val doc   = new BsonDocument()
      ownerCodec.encode(new BsonDocumentWriter(doc), owner, org.bson.codecs.EncoderContext.builder().build())

      // The nested "pet" sub-document must carry the discriminator
      doc.getDocument("pet").containsKey("_type") shouldBe true

      // And the full document must round-trip correctly
      val decoded = ownerCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build())
      decoded shouldBe owner
    }
  }

  it should "correctly encode the new discriminator when replacing one subtype with another" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .build

    val ownerCodec = registry.get(classOf[Owner])

    // Simulate replacing a Dog document with a Cat document
    forAll { (original: Dog, replacement: Cat) =>
      val replacedOwner = Owner("Alice", replacement)
      val doc           = new BsonDocument()
      ownerCodec.encode(new BsonDocumentWriter(doc), replacedOwner, org.bson.codecs.EncoderContext.builder().build())

      doc.getDocument("pet").getString("_type").getValue shouldBe "Cat"
      ownerCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe replacedOwner
    }
  }

  it should "encode all elements with discriminator for a List[SealedTrait] replacement field" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .register[Zoo]
      .build

    val zooCodec = registry.get(classOf[Zoo])

    forAll { (dog: Dog, cat: Cat, bird: Bird) =>
      val zoo    = Zoo("Test Zoo", List(dog, cat, bird))
      val zooDoc = new BsonDocument()
      zooCodec.encode(new BsonDocumentWriter(zooDoc), zoo, org.bson.codecs.EncoderContext.builder().build())

      val animalsArray = zooDoc.getArray("animals")
      animalsArray.get(0).asDocument().containsKey("_type") shouldBe true
      animalsArray.get(1).asDocument().containsKey("_type") shouldBe true
      animalsArray.get(2).asDocument().containsKey("_type") shouldBe true

      zooCodec.decode(new BsonDocumentReader(zooDoc), org.bson.codecs.DecoderContext.builder().build()) shouldBe zoo
    }
  }

  // ===== Updates.push / Updates.addToSet: element encoded via concrete subtype codec =====

  "Property-based: Updates.push simulation" should
      "always encode the pushed element with a discriminator via the concrete codec" in {
    val registry    = RegistryBuilder.from(defaultBsonRegistry).registerSealed[Animal].build
    val animalCodec = registry.get(classOf[Animal])

    forAll { (dog: Dog) =>
      val doc = new BsonDocument()
      registry.get(classOf[Dog]).encode(new BsonDocumentWriter(doc), dog, org.bson.codecs.EncoderContext.builder().build())
      doc.containsKey("_type") shouldBe true
      doc.getString("_type").getValue shouldBe "Dog"
      animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe dog
    }

    forAll { (cat: Cat) =>
      val doc = new BsonDocument()
      registry.get(classOf[Cat]).encode(new BsonDocumentWriter(doc), cat, org.bson.codecs.EncoderContext.builder().build())
      doc.containsKey("_type") shouldBe true
      doc.getString("_type").getValue shouldBe "Cat"
      animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe cat
    }

    forAll { (bird: Bird) =>
      val doc = new BsonDocument()
      registry.get(classOf[Bird]).encode(new BsonDocumentWriter(doc), bird, org.bson.codecs.EncoderContext.builder().build())
      doc.containsKey("_type") shouldBe true
      doc.getString("_type").getValue shouldBe "Bird"
      animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe bird
    }
  }

  "Property-based: Updates.addToSet simulation" should
      "always encode the set element with a discriminator via the concrete codec" in {
    // Updates.addToSet has the same encoding path as Updates.push at the codec level
    val registry    = RegistryBuilder.from(defaultBsonRegistry).registerSealed[Animal].build
    val animalCodec = registry.get(classOf[Animal])

    forAll { (animal: Animal) =>
      val doc = new BsonDocument()
      animal match
        case d: Dog  => registry.get(classOf[Dog]).encode(new BsonDocumentWriter(doc), d, org.bson.codecs.EncoderContext.builder().build())
        case c: Cat  => registry.get(classOf[Cat]).encode(new BsonDocumentWriter(doc), c, org.bson.codecs.EncoderContext.builder().build())
        case b: Bird => registry.get(classOf[Bird]).encode(new BsonDocumentWriter(doc), b, org.bson.codecs.EncoderContext.builder().build())

      doc.containsKey("_type") shouldBe true
      animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe animal
    }
  }

  // ===== Updates.setOnInsert (upsert): same encoding path as Updates.set =====

  "Property-based: Updates.setOnInsert simulation" should
      "always encode the upserted sealed value with a discriminator via the concrete codec" in {
    val registry    = RegistryBuilder.from(defaultBsonRegistry).registerSealed[Animal].build
    val animalCodec = registry.get(classOf[Animal])

    forAll { (animal: Animal) =>
      val doc = new BsonDocument()
      animal match
        case d: Dog  => registry.get(classOf[Dog]).encode(new BsonDocumentWriter(doc), d, org.bson.codecs.EncoderContext.builder().build())
        case c: Cat  => registry.get(classOf[Cat]).encode(new BsonDocumentWriter(doc), c, org.bson.codecs.EncoderContext.builder().build())
        case b: Bird => registry.get(classOf[Bird]).encode(new BsonDocumentWriter(doc), b, org.bson.codecs.EncoderContext.builder().build())

      doc.containsKey("_type") shouldBe true
      animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe animal
    }
  }

  // ===== bulkWrite =====

  "Property-based: bulkWrite UpdateOneModel simulation" should
      "always encode the update value with a discriminator via the concrete codec" in {
    val registry    = RegistryBuilder.from(defaultBsonRegistry).registerSealed[Animal].build
    val animalCodec = registry.get(classOf[Animal])

    // UpdateOneModel uses Updates.set internally — concrete codec path
    forAll { (dog: Dog) =>
      val doc = new BsonDocument()
      registry.get(classOf[Dog]).encode(new BsonDocumentWriter(doc), dog, org.bson.codecs.EncoderContext.builder().build())
      doc.getString("_type").getValue shouldBe "Dog"
      animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe dog
    }

    forAll { (bird: Bird) =>
      val doc = new BsonDocument()
      registry.get(classOf[Bird]).encode(new BsonDocumentWriter(doc), bird, org.bson.codecs.EncoderContext.builder().build())
      doc.getString("_type").getValue shouldBe "Bird"
      animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe bird
    }
  }

  "Property-based: bulkWrite ReplaceOneModel simulation" should
      "always encode the sealed field with a discriminator via the outer-document codec" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .build

    val ownerCodec = registry.get(classOf[Owner])

    // ReplaceOneModel encodes the full replacement document — outer codec path
    forAll { (animal: Animal) =>
      val owner = Owner("BulkUser", animal)
      val doc   = new BsonDocument()
      ownerCodec.encode(new BsonDocumentWriter(doc), owner, org.bson.codecs.EncoderContext.builder().build())

      doc.getDocument("pet").containsKey("_type") shouldBe true
      ownerCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe owner
    }
  }

  "Property-based: findOneAndUpdate simulation" should
      "encode the replacement sealed value with a discriminator" in {
    val registry    = RegistryBuilder.from(defaultBsonRegistry).registerSealed[Animal].build
    val animalCodec = registry.get(classOf[Animal])

    // findOneAndUpdate with Updates.set encodes the new value via the concrete codec
    forAll { (animal: Animal) =>
      val doc = new BsonDocument()
      animal match
        case d: Dog  => registry.get(classOf[Dog]).encode(new BsonDocumentWriter(doc), d, org.bson.codecs.EncoderContext.builder().build())
        case c: Cat  => registry.get(classOf[Cat]).encode(new BsonDocumentWriter(doc), c, org.bson.codecs.EncoderContext.builder().build())
        case b: Bird => registry.get(classOf[Bird]).encode(new BsonDocumentWriter(doc), b, org.bson.codecs.EncoderContext.builder().build())

      doc.containsKey("_type") shouldBe true
      animalCodec.decode(new BsonDocumentReader(doc), org.bson.codecs.DecoderContext.builder().build()) shouldBe animal
    }
  }

end SealedTraitIntegrationSpec
