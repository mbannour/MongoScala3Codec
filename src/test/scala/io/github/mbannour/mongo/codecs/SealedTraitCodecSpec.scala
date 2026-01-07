package io.github.mbannour.mongo.codecs

import scala.reflect.ClassTag

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{BsonValueCodecProvider, Codec, DecoderContext, EncoderContext}
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter, BsonInvalidOperationException}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Comprehensive test suite for sealed trait/class codec generation.
  *
  * Tests cover:
  *   - Basic sealed trait encoding/decoding
  *   - Discriminator field handling
  *   - Custom discriminator configuration
  *   - Nested sealed types
  *   - Collections of sealed types
  *   - Multi-level hierarchies
  *   - Error cases
  */
class SealedTraitCodecSpec extends AnyFlatSpec with Matchers:

  // Test models
  sealed trait Animal
  case class Dog(name: String, breed: String) extends Animal
  case class Cat(name: String, lives: Int) extends Animal
  case class Bird(name: String, canFly: Boolean) extends Animal

  sealed class Vehicle
  case class Car(model: String, year: Int) extends Vehicle
  case class Motorcycle(model: String, cc: Int) extends Vehicle

  sealed abstract class Shape
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  case class Triangle(base: Double, height: Double) extends Shape

  // Multi-level hierarchy
  sealed trait Transport
  sealed trait Motorized extends Transport
  case class Bus(capacity: Int) extends Motorized
  case class Train(cars: Int) extends Motorized
  case class Bicycle(gears: Int) extends Transport

  // Sealed type as field
  case class Zoo(name: String, animals: List[Animal])
  case class Owner(name: String, pet: Animal)

  // Helper to create test registry
  private def createBaseRegistry(): CodecRegistry =
    CodecRegistries.fromProviders(new BsonValueCodecProvider())

  private inline def createRegistry[T: ClassTag](config: CodecConfig = CodecConfig()): CodecRegistry =
    val baseRegistry = createBaseRegistry()
    RegistryBuilder
      .from(baseRegistry)
      .withConfig(config)
      .registerSealed[T]
      .build

  private inline def encodeToDocument[T: ClassTag](value: T, registry: CodecRegistry): BsonDocument =
    val codec = registry.get(summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()

    codec.encode(writer, value, encoderContext)
    document
  end encodeToDocument

  private inline def decodeFromDocument[T: ClassTag](document: BsonDocument, registry: CodecRegistry): T =
    val codec = registry.get(summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()

    codec.decode(reader, decoderContext)
  end decodeFromDocument

  private inline def roundTrip[T: ClassTag](value: T, registry: CodecRegistry): T =
    val document = encodeToDocument(value, registry)
    decodeFromDocument(document, registry)

  // ===== Basic Sealed Trait Tests =====

  "SealedTraitCodecGenerator" should "generate codec for sealed trait with correct encoder class" in {
    val registry = createRegistry[Animal]()
    val codec = registry.get(classOf[Animal])
    codec.getEncoderClass shouldBe classOf[Animal]
  }

  it should "encode Dog as Animal with discriminator field" in {
    val registry = createRegistry[Animal]()
    val dog: Animal = Dog("Rex", "Labrador")
    val document = encodeToDocument(dog, registry)

    document.getString("_type").getValue shouldBe "Dog"
    document.getString("name").getValue shouldBe "Rex"
    document.getString("breed").getValue shouldBe "Labrador"
    document.size() shouldBe 3 // _type, name, breed
  }

  it should "encode Cat as Animal with discriminator field" in {
    val registry = createRegistry[Animal]()
    val cat: Animal = Cat("Whiskers", 9)
    val document = encodeToDocument(cat, registry)

    document.getString("_type").getValue shouldBe "Cat"
    document.getString("name").getValue shouldBe "Whiskers"
    document.getInt32("lives").getValue shouldBe 9
    document.size() shouldBe 3
  }

  it should "decode Dog from document with discriminator" in {
    val registry = createRegistry[Animal]()
    val dog: Animal = Dog("Buddy", "Beagle")
    val roundtripped = roundTrip(dog, registry)

    roundtripped shouldBe dog
    roundtripped shouldBe a[Dog]
  }

  it should "decode Cat from document with discriminator" in {
    val registry = createRegistry[Animal]()
    val cat: Animal = Cat("Fluffy", 7)
    val roundtripped = roundTrip(cat, registry)

    roundtripped shouldBe cat
    roundtripped shouldBe a[Cat]
  }

  it should "round-trip all subtypes correctly" in {
    val registry = createRegistry[Animal]()

    val dog: Animal = Dog("Max", "Poodle")
    val cat: Animal = Cat("Shadow", 3)
    val bird: Animal = Bird("Tweety", canFly = true)

    roundTrip(dog, registry) shouldBe dog
    roundTrip(cat, registry) shouldBe cat
    roundTrip(bird, registry) shouldBe bird
  }

  // ===== Sealed Class Tests =====

  it should "work with sealed classes (not just traits)" in {
    val registry = createRegistry[Vehicle]()

    val car: Vehicle = Car("Tesla Model 3", 2023)
    val motorcycle: Vehicle = Motorcycle("Harley Davidson", 1200)

    val carDoc = encodeToDocument(car, registry)
    carDoc.getString("_type").getValue shouldBe "Car"
    carDoc.getString("model").getValue shouldBe "Tesla Model 3"
    carDoc.getInt32("year").getValue shouldBe 2023

    roundTrip(car, registry) shouldBe car
    roundTrip(motorcycle, registry) shouldBe motorcycle
  }

  // ===== Sealed Abstract Class Tests =====

  it should "work with sealed abstract classes" in {
    val registry = createRegistry[Shape]()

    val circle: Shape = Circle(5.0)
    val rectangle: Shape = Rectangle(10.0, 20.0)
    val triangle: Shape = Triangle(6.0, 8.0)

    roundTrip(circle, registry) shouldBe circle
    roundTrip(rectangle, registry) shouldBe rectangle
    roundTrip(triangle, registry) shouldBe triangle
  }

  // ===== Custom Discriminator Field Tests =====

  it should "use custom discriminator field name" in {
    val config = CodecConfig(discriminatorField = "_class")
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .withConfig(config)
      .registerSealed[Animal]
      .build

    val dog: Animal = Dog("Charlie", "Bulldog")
    val document = encodeToDocument(dog, registry)

    document.containsKey("_class") shouldBe true
    document.containsKey("_type") shouldBe false
    document.getString("_class").getValue shouldBe "Dog"

    roundTrip(dog, registry) shouldBe dog
  }

  // ===== Nested Sealed Types Tests =====

  it should "handle sealed trait as a field in case class" in {
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .build

    val owner = Owner("Alice", Dog("Spot", "Dalmatian"))
    val roundtripped = roundTrip(owner, registry)

    roundtripped shouldBe owner
    roundtripped.pet shouldBe a[Dog]
  }

  it should "handle collections of sealed types" in {
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealed[Animal]
      .register[Zoo]
      .build

    val zoo = Zoo(
      "City Zoo",
      List(
        Dog("Rex", "German Shepherd"),
        Cat("Mittens", 5),
        Bird("Polly", canFly = true),
        Dog("Buddy", "Golden Retriever")
      )
    )

    encodeToDocument(zoo, registry)
    val roundtripped = roundTrip(zoo, registry)

    roundtripped shouldBe zoo
    roundtripped.animals.size shouldBe 4
    roundtripped.animals(0) shouldBe a[Dog]
    roundtripped.animals(1) shouldBe a[Cat]
    roundtripped.animals(2) shouldBe a[Bird]
    roundtripped.animals(3) shouldBe a[Dog]
  }

  // ===== Multi-level Hierarchy Tests =====

  it should "handle multi-level sealed hierarchies" in {
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealed[Transport]
      .build

    val bus: Transport = Bus(50)
    val train: Transport = Train(10)
    val bicycle: Transport = Bicycle(21)

    roundTrip(bus, registry) shouldBe bus
    roundTrip(train, registry) shouldBe train
    roundTrip(bicycle, registry) shouldBe bicycle

    val busDoc = encodeToDocument(bus, registry)
    busDoc.getString("_type").getValue shouldBe "Bus"
  }

  // ===== Error Cases =====

  it should "error when discriminator field is missing" in {
    val registry = createRegistry[Animal]()
    val codec = registry.get(classOf[Animal])

    // Create document without discriminator
    val document = new BsonDocument()
    document.put("name", org.bson.BsonString("Mystery"))
    document.put("breed", org.bson.BsonString("Unknown"))

    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()

    val exception = intercept[BsonInvalidOperationException] {
      codec.decode(reader, decoderContext)
    }

    exception.getMessage should include("discriminator")
    exception.getMessage should include("_type")
  }

  it should "error when discriminator value is unknown" in {
    val registry = createRegistry[Animal]()
    val codec = registry.get(classOf[Animal])

    // Create document with invalid discriminator
    val document = new BsonDocument()
    document.put("_type", org.bson.BsonString("Elephant")) // Not a valid subtype
    document.put("name", org.bson.BsonString("Dumbo"))

    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()

    val exception = intercept[BsonInvalidOperationException] {
      codec.decode(reader, decoderContext)
    }

    exception.getMessage should include("Unknown discriminator")
    exception.getMessage should include("Elephant")
  }

  it should "error when trying to encode null sealed trait value" in {
    val registry = createRegistry[Animal]()
    val codec = registry.get(classOf[Animal])

    val writer = new BsonDocumentWriter(new BsonDocument())
    val encoderContext = EncoderContext.builder().build()

    val exception = intercept[BsonInvalidOperationException] {
      codec.encode(writer, null.asInstanceOf[Animal], encoderContext)
    }

    exception.getMessage should include("null")
  }

  // ===== Integration with Existing Case Class Codecs =====

  it should "work seamlessly with regular case class registration" in {
    case class Farm(name: String, animals: List[Animal], owner: Owner)

    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .register[Farm]
      .build

    val farm = Farm(
      "Green Acres",
      List(Dog("Bingo", "Collie"), Cat("Tom", 2)),
      Owner("Bob", Bird("Chirpy", canFly = false))
    )

    val roundtripped = roundTrip(farm, registry)
    roundtripped shouldBe farm
  }

end SealedTraitCodecSpec
