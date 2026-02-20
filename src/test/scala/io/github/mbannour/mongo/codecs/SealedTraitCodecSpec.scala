package io.github.mbannour.mongo.codecs

import scala.reflect.ClassTag

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{BsonValueCodecProvider, Codec, DecoderContext, EncoderContext}
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter, BsonInvalidOperationException}
import org.mongodb.scala.bson.annotations.BsonProperty
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

  // Sealed type with Option fields – used to verify NoneHandling is preserved
  sealed trait Profile
  case class ActiveProfile(username: String, bio: Option[String]) extends Profile
  case class InactiveProfile(username: String, note: Option[String]) extends Profile

  // Sealed type whose subtype uses @BsonProperty field renaming
  sealed trait Event
  case class ClickEvent(@BsonProperty("element_id") elementId: String, x: Int, y: Int) extends Event
  case class PageViewEvent(@BsonProperty("page_url") pageUrl: String) extends Event

  // Sealed trait with exactly one subtype – edge-case regression guard
  sealed trait SingletonHierarchy
  case class OnlyVariant(value: Int, label: String) extends SingletonHierarchy

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

  // ===== Updates.set Scenario: concrete-class codec must include discriminator =====

  it should "include discriminator when encoding a concrete subtype directly (simulating Updates.set)" in {
    val registry = createRegistry[Animal]()

    // Updates.set("animal", Bird(...)) causes the MongoDB driver to look up the codec for the
    // *runtime* class (classOf[Bird]), not the sealed-trait codec.  The resulting BSON must
    // still carry the _type discriminator so that the document can be decoded later.
    val birdCodec = registry.get(classOf[Bird])
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)

    birdCodec.encode(writer, Bird("Tweety", canFly = true), EncoderContext.builder().build())

    document.containsKey("_type") shouldBe true
    document.getString("_type").getValue shouldBe "Bird"
    document.getString("name").getValue shouldBe "Tweety"
    document.getBoolean("canFly").getValue shouldBe true
  }

  it should "decode correctly after a concrete-subtype was encoded directly (Updates.set round-trip)" in {
    val registry = createRegistry[Animal]()

    // Encode via the concrete-class codec (as Updates.set would)
    val birdCodec = registry.get(classOf[Bird])
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    birdCodec.encode(writer, Bird("Tweety", canFly = true), EncoderContext.builder().build())

    // Decode via the sealed-trait codec (as a subsequent find() would)
    val animalCodec = registry.get(classOf[Animal])
    val reader = new org.bson.BsonDocumentReader(document)
    val result = animalCodec.decode(reader, org.bson.codecs.DecoderContext.builder().build())

    result shouldBe Bird("Tweety", canFly = true)
    result shouldBe a[Bird]
  }

  it should "include discriminator for all subtypes when encoded directly" in {
    val registry = createRegistry[Animal]()

    val dogDoc = new BsonDocument()
    registry.get(classOf[Dog]).encode(new BsonDocumentWriter(dogDoc), Dog("Rex", "Lab"), EncoderContext.builder().build())
    dogDoc.getString("_type").getValue shouldBe "Dog"

    val catDoc = new BsonDocument()
    registry.get(classOf[Cat]).encode(new BsonDocumentWriter(catDoc), Cat("Puss", 9), EncoderContext.builder().build())
    catDoc.getString("_type").getValue shouldBe "Cat"

    val birdDoc = new BsonDocument()
    registry.get(classOf[Bird]).encode(new BsonDocumentWriter(birdDoc), Bird("Tweety", true), EncoderContext.builder().build())
    birdDoc.getString("_type").getValue shouldBe "Bird"
  }

  it should "use custom discriminator field name when encoding concrete subtype directly" in {
    val config = CodecConfig(discriminatorField = "_class")
    val registry = createRegistry[Animal](config)

    val dogDoc = new BsonDocument()
    registry.get(classOf[Dog]).encode(new BsonDocumentWriter(dogDoc), Dog("Rex", "Lab"), EncoderContext.builder().build())

    dogDoc.containsKey("_class") shouldBe true
    dogDoc.containsKey("_type") shouldBe false
    dogDoc.getString("_class").getValue shouldBe "Dog"

    // Round-trip: encoded with custom field, decoded by sealed codec configured the same way
    val animalCodec = registry.get(classOf[Animal])
    val result = animalCodec.decode(new org.bson.BsonDocumentReader(dogDoc), org.bson.codecs.DecoderContext.builder().build())
    result shouldBe Dog("Rex", "Lab")
  }

  it should "include discriminator when encoding sealed-class subtypes directly" in {
    val registry = createRegistry[Vehicle]()

    val carDoc = new BsonDocument()
    registry.get(classOf[Car]).encode(new BsonDocumentWriter(carDoc), Car("Tesla", 2023), EncoderContext.builder().build())
    carDoc.containsKey("_type") shouldBe true
    carDoc.getString("_type").getValue shouldBe "Car"

    // Sealed codec can decode what the concrete codec produced
    val vehicleCodec = registry.get(classOf[Vehicle])
    val result = vehicleCodec.decode(new org.bson.BsonDocumentReader(carDoc), org.bson.codecs.DecoderContext.builder().build())
    result shouldBe Car("Tesla", 2023)
  }

  it should "include discriminator when encoding sealed-abstract-class subtypes directly" in {
    val registry = createRegistry[Shape]()

    val circleDoc = new BsonDocument()
    registry.get(classOf[Circle]).encode(new BsonDocumentWriter(circleDoc), Circle(3.14), EncoderContext.builder().build())
    circleDoc.containsKey("_type") shouldBe true
    circleDoc.getString("_type").getValue shouldBe "Circle"

    val shapeCodec = registry.get(classOf[Shape])
    val result = shapeCodec.decode(new org.bson.BsonDocumentReader(circleDoc), org.bson.codecs.DecoderContext.builder().build())
    result shouldBe Circle(3.14)
  }

  it should "include discriminator for multi-level hierarchy subtypes encoded directly" in {
    val registry = createRegistry[Transport]()

    // Bus extends Motorized extends Transport
    val busDoc = new BsonDocument()
    registry.get(classOf[Bus]).encode(new BsonDocumentWriter(busDoc), Bus(50), EncoderContext.builder().build())
    busDoc.containsKey("_type") shouldBe true
    busDoc.getString("_type").getValue shouldBe "Bus"

    // Train extends Motorized extends Transport
    val trainDoc = new BsonDocument()
    registry.get(classOf[Train]).encode(new BsonDocumentWriter(trainDoc), Train(10), EncoderContext.builder().build())
    trainDoc.getString("_type").getValue shouldBe "Train"

    // Bicycle extends Transport directly
    val bicycleDoc = new BsonDocument()
    registry.get(classOf[Bicycle]).encode(new BsonDocumentWriter(bicycleDoc), Bicycle(21), EncoderContext.builder().build())
    bicycleDoc.getString("_type").getValue shouldBe "Bicycle"

    // All decode correctly via the sealed-trait codec
    val transportCodec = registry.get(classOf[Transport])
    transportCodec.decode(new org.bson.BsonDocumentReader(busDoc), org.bson.codecs.DecoderContext.builder().build()) shouldBe Bus(50)
    transportCodec.decode(new org.bson.BsonDocumentReader(trainDoc), org.bson.codecs.DecoderContext.builder().build()) shouldBe Train(10)
    transportCodec.decode(new org.bson.BsonDocumentReader(bicycleDoc), org.bson.codecs.DecoderContext.builder().build()) shouldBe Bicycle(
      21
    )
  }

  it should "preserve NoneHandling.Ignore when encoding sealed subtypes directly" in {
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .ignoreNone
      .registerSealed[Profile]
      .build

    // Encode via concrete-class codec – NoneHandling.Ignore must be preserved by the copy
    val activeDoc = new BsonDocument()
    registry
      .get(classOf[ActiveProfile])
      .encode(
        new BsonDocumentWriter(activeDoc),
        ActiveProfile("alice", None),
        EncoderContext.builder().build()
      )

    // Discriminator must be present
    activeDoc.containsKey("_type") shouldBe true
    activeDoc.getString("_type").getValue shouldBe "ActiveProfile"
    // None bio must be omitted (NoneHandling.Ignore respected)
    activeDoc.containsKey("bio") shouldBe false

    // Round-trip via sealed codec
    val profileCodec = registry.get(classOf[Profile])
    val result = profileCodec.decode(new org.bson.BsonDocumentReader(activeDoc), org.bson.codecs.DecoderContext.builder().build())
    result shouldBe ActiveProfile("alice", None)
  }

  it should "not duplicate discriminator when encoding via the sealed-trait codec (insertOne path)" in {
    val registry = createRegistry[Animal]()
    val dog: Animal = Dog("Rex", "Labrador")

    // Encode using the SEALED codec (insertOne path) – not the concrete codec
    val document = encodeToDocument(dog, registry)

    // _type must appear exactly once
    document.containsKey("_type") shouldBe true
    document.getString("_type").getValue shouldBe "Dog"
    document.size() shouldBe 3 // _type, name, breed – no duplication
  }

  it should "include discriminator when encoding concrete subtypes registered via registerSealedAll" in {
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealedAll[(Animal, Shape)]
      .build

    // Animal subtype
    val birdDoc = new BsonDocument()
    registry.get(classOf[Bird]).encode(new BsonDocumentWriter(birdDoc), Bird("Tweety", true), EncoderContext.builder().build())
    birdDoc.containsKey("_type") shouldBe true
    birdDoc.getString("_type").getValue shouldBe "Bird"

    // Shape subtype
    val rectDoc = new BsonDocument()
    registry.get(classOf[Rectangle]).encode(new BsonDocumentWriter(rectDoc), Rectangle(4.0, 5.0), EncoderContext.builder().build())
    rectDoc.containsKey("_type") shouldBe true
    rectDoc.getString("_type").getValue shouldBe "Rectangle"

    // Both decode correctly via their sealed codecs
    registry
      .get(classOf[Animal])
      .decode(
        new org.bson.BsonDocumentReader(birdDoc),
        org.bson.codecs.DecoderContext.builder().build()
      ) shouldBe Bird("Tweety", true)

    registry
      .get(classOf[Shape])
      .decode(
        new org.bson.BsonDocumentReader(rectDoc),
        org.bson.codecs.DecoderContext.builder().build()
      ) shouldBe Rectangle(4.0, 5.0)
  }

  it should "respect @BsonProperty field renaming in sealed subtypes when encoding directly" in {
    val registry = createRegistry[Event]()

    val clickDoc = new BsonDocument()
    registry
      .get(classOf[ClickEvent])
      .encode(
        new BsonDocumentWriter(clickDoc),
        ClickEvent("btn-submit", 120, 340),
        EncoderContext.builder().build()
      )

    // Discriminator must be present
    clickDoc.containsKey("_type") shouldBe true
    clickDoc.getString("_type").getValue shouldBe "ClickEvent"
    // Field must appear under its @BsonProperty name, not the Scala name
    clickDoc.containsKey("element_id") shouldBe true
    clickDoc.containsKey("elementId") shouldBe false
    clickDoc.getString("element_id").getValue shouldBe "btn-submit"
    clickDoc.getInt32("x").getValue shouldBe 120
    clickDoc.getInt32("y").getValue shouldBe 340

    // Round-trip: sealed codec must decode it back to the correct instance
    val eventCodec = registry.get(classOf[Event])
    val result = eventCodec.decode(
      new org.bson.BsonDocumentReader(clickDoc),
      org.bson.codecs.DecoderContext.builder().build()
    )
    result shouldBe ClickEvent("btn-submit", 120, 340)
    result shouldBe a[ClickEvent]
  }

  it should "handle a sealed trait with exactly one concrete subtype" in {
    val registry = createRegistry[SingletonHierarchy]()

    // Encode via the sealed codec
    val sealedDoc = encodeToDocument[SingletonHierarchy](OnlyVariant(42, "hello"), registry)
    sealedDoc.containsKey("_type") shouldBe true
    sealedDoc.getString("_type").getValue shouldBe "OnlyVariant"
    sealedDoc.getInt32("value").getValue shouldBe 42
    roundTrip[SingletonHierarchy](OnlyVariant(42, "hello"), registry) shouldBe OnlyVariant(42, "hello")

    // Encode via the concrete codec (Updates.set path)
    val concreteDoc = new BsonDocument()
    registry
      .get(classOf[OnlyVariant])
      .encode(
        new BsonDocumentWriter(concreteDoc),
        OnlyVariant(7, "world"),
        EncoderContext.builder().build()
      )
    concreteDoc.containsKey("_type") shouldBe true
    concreteDoc.getString("_type").getValue shouldBe "OnlyVariant"

    // Sealed codec must decode it
    registry
      .get(classOf[SingletonHierarchy])
      .decode(
        new org.bson.BsonDocumentReader(concreteDoc),
        org.bson.codecs.DecoderContext.builder().build()
      ) shouldBe OnlyVariant(7, "world")
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

  // ===== replaceOne / findOneAndReplace: outer-document codec path =====
  //
  // replaceOne and findOneAndReplace encode the full replacement document via
  // the *outer* type codec (e.g. Owner).  The outer codec then delegates to
  // the sealed-trait codec (Animal) for the sealed field, which writes the
  // discriminator.  This is a different code path from Updates.set (which goes
  // straight to the concrete-subtype codec).

  "Sealed trait replaceOne/findOneAndReplace simulation" should
    "encode sealed trait field with discriminator via outer-document codec" in {
      val baseRegistry = createBaseRegistry()
      val registry = RegistryBuilder
        .from(baseRegistry)
        .registerSealed[Animal]
        .register[Owner]
        .build

      val ownerCodec = registry.get(classOf[Owner])
      val owner = Owner("Alice", Dog("Rex", "Labrador"))
      val doc = new BsonDocument()
      ownerCodec.encode(new BsonDocumentWriter(doc), owner, EncoderContext.builder().build())

      val petDoc = doc.getDocument("pet")
      petDoc.containsKey("_type") shouldBe true
      petDoc.getString("_type").getValue shouldBe "Dog"
      petDoc.getString("name").getValue shouldBe "Rex"
      petDoc.getString("breed").getValue shouldBe "Labrador"

      ownerCodec.decode(new BsonDocumentReader(doc), DecoderContext.builder().build()) shouldBe owner
    }

  it should "work for every sealed subtype as the replacement value" in {
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .build

    val ownerCodec = registry.get(classOf[Owner])

    val cases = Seq(
      Owner("Alice", Dog("Rex", "Labrador")),
      Owner("Bob", Cat("Whiskers", 9)),
      Owner("Carol", Bird("Tweety", canFly = true))
    )

    cases.foreach { owner =>
      val doc = new BsonDocument()
      ownerCodec.encode(new BsonDocumentWriter(doc), owner, EncoderContext.builder().build())
      val petDoc = doc.getDocument("pet")
      petDoc.containsKey("_type") shouldBe true
      ownerCodec.decode(new BsonDocumentReader(doc), DecoderContext.builder().build()) shouldBe owner
    }
  }

  it should "write the new discriminator when replacing one subtype with another" in {
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .build

    val ownerCodec = registry.get(classOf[Owner])

    // Simulate: document originally had a Dog, replacement has a Cat
    val replacement = Owner("Alice", Cat("Whiskers", 9))
    val doc = new BsonDocument()
    ownerCodec.encode(new BsonDocumentWriter(doc), replacement, EncoderContext.builder().build())

    doc.getDocument("pet").getString("_type").getValue shouldBe "Cat"
    ownerCodec.decode(new BsonDocumentReader(doc), DecoderContext.builder().build()) shouldBe replacement
  }

  it should "work for a collection field (List[SealedTrait]) in the replacement document" in {
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealed[Animal]
      .register[Zoo]
      .build

    val zoo = Zoo("City Zoo", List(Dog("Rex", "Lab"), Cat("Luna", 3), Bird("Polly", true)))
    val zooDoc = new BsonDocument()
    registry.get(classOf[Zoo]).encode(new BsonDocumentWriter(zooDoc), zoo, EncoderContext.builder().build())

    // Every element in the "animals" array must carry a discriminator
    val animalsArray = zooDoc.getArray("animals")
    animalsArray.get(0).asDocument().getString("_type").getValue shouldBe "Dog"
    animalsArray.get(1).asDocument().getString("_type").getValue shouldBe "Cat"
    animalsArray.get(2).asDocument().getString("_type").getValue shouldBe "Bird"

    registry.get(classOf[Zoo]).decode(new BsonDocumentReader(zooDoc), DecoderContext.builder().build()) shouldBe zoo
  }

  // ===== Updates.push: same encoding path as Updates.set, but element goes into an array =====

  "Sealed trait Updates.push simulation" should
    "encode the pushed element with discriminator via concrete codec" in {
      val registry = createRegistry[Animal]()

      // Updates.push("animals", Bird("Tweety", true)) causes the MongoDB driver to
      // encode the Bird value via registry.get(classOf[Bird]) — the concrete codec.
      val pushedDoc = new BsonDocument()
      registry
        .get(classOf[Bird])
        .encode(
          new BsonDocumentWriter(pushedDoc),
          Bird("Tweety", canFly = true),
          EncoderContext.builder().build()
        )

      pushedDoc.containsKey("_type") shouldBe true
      pushedDoc.getString("_type").getValue shouldBe "Bird"

      registry
        .get(classOf[Animal])
        .decode(
          new BsonDocumentReader(pushedDoc),
          DecoderContext.builder().build()
        ) shouldBe Bird("Tweety", canFly = true)
    }

  it should "encode every subtype with the correct discriminator when pushed" in {
    val registry = createRegistry[Animal]()
    val animalCodec = registry.get(classOf[Animal])

    val dogDoc = new BsonDocument()
    registry.get(classOf[Dog]).encode(new BsonDocumentWriter(dogDoc), Dog("Rex", "Lab"), EncoderContext.builder().build())
    dogDoc.getString("_type").getValue shouldBe "Dog"
    animalCodec.decode(new BsonDocumentReader(dogDoc), DecoderContext.builder().build()) shouldBe Dog("Rex", "Lab")

    val catDoc = new BsonDocument()
    registry.get(classOf[Cat]).encode(new BsonDocumentWriter(catDoc), Cat("Luna", 5), EncoderContext.builder().build())
    catDoc.getString("_type").getValue shouldBe "Cat"
    animalCodec.decode(new BsonDocumentReader(catDoc), DecoderContext.builder().build()) shouldBe Cat("Luna", 5)

    val birdDoc = new BsonDocument()
    registry.get(classOf[Bird]).encode(new BsonDocumentWriter(birdDoc), Bird("Coco", false), EncoderContext.builder().build())
    birdDoc.getString("_type").getValue shouldBe "Bird"
    animalCodec.decode(new BsonDocumentReader(birdDoc), DecoderContext.builder().build()) shouldBe Bird("Coco", false)
  }

  it should "preserve custom discriminator field for pushed elements" in {
    val config = CodecConfig(discriminatorField = "_class")
    val registry = createRegistry[Animal](config)

    val pushedDoc = new BsonDocument()
    registry
      .get(classOf[Dog])
      .encode(
        new BsonDocumentWriter(pushedDoc),
        Dog("Buddy", "Golden"),
        EncoderContext.builder().build()
      )

    pushedDoc.containsKey("_class") shouldBe true
    pushedDoc.containsKey("_type") shouldBe false
    pushedDoc.getString("_class").getValue shouldBe "Dog"

    registry
      .get(classOf[Animal])
      .decode(
        new BsonDocumentReader(pushedDoc),
        DecoderContext.builder().build()
      ) shouldBe Dog("Buddy", "Golden")
  }

  // ===== Updates.addToSet: identical encoding path to push =====

  "Sealed trait Updates.addToSet simulation" should
    "encode the set element with discriminator via concrete codec" in {
      val registry = createRegistry[Animal]()

      val addedDoc = new BsonDocument()
      registry
        .get(classOf[Cat])
        .encode(
          new BsonDocumentWriter(addedDoc),
          Cat("Mochi", 3),
          EncoderContext.builder().build()
        )

      addedDoc.containsKey("_type") shouldBe true
      addedDoc.getString("_type").getValue shouldBe "Cat"
      registry
        .get(classOf[Animal])
        .decode(
          new BsonDocumentReader(addedDoc),
          DecoderContext.builder().build()
        ) shouldBe Cat("Mochi", 3)
    }

  it should "encode every subtype with discriminator as a set element" in {
    val registry = createRegistry[Animal]()
    val animalCodec = registry.get(classOf[Animal])

    val dogDoc = new BsonDocument()
    registry.get(classOf[Dog]).encode(new BsonDocumentWriter(dogDoc), Dog("Rex", "Lab"), EncoderContext.builder().build())
    dogDoc.getString("_type").getValue shouldBe "Dog"
    animalCodec.decode(new BsonDocumentReader(dogDoc), DecoderContext.builder().build()) shouldBe Dog("Rex", "Lab")

    val catDoc = new BsonDocument()
    registry.get(classOf[Cat]).encode(new BsonDocumentWriter(catDoc), Cat("Puss", 7), EncoderContext.builder().build())
    catDoc.getString("_type").getValue shouldBe "Cat"
    animalCodec.decode(new BsonDocumentReader(catDoc), DecoderContext.builder().build()) shouldBe Cat("Puss", 7)

    val birdDoc = new BsonDocument()
    registry.get(classOf[Bird]).encode(new BsonDocumentWriter(birdDoc), Bird("Polly", true), EncoderContext.builder().build())
    birdDoc.getString("_type").getValue shouldBe "Bird"
    animalCodec.decode(new BsonDocumentReader(birdDoc), DecoderContext.builder().build()) shouldBe Bird("Polly", true)
  }

  // ===== Updates.setOnInsert (upsert): same encoding path as Updates.set =====

  "Sealed trait Updates.setOnInsert simulation" should
    "encode the upserted sealed value with discriminator via concrete codec" in {
      val registry = createRegistry[Animal]()

      // Updates.setOnInsert sets a field only when the operation inserts a new document
      // (upsert). The encoding path is identical to Updates.set.
      val insertedDoc = new BsonDocument()
      registry
        .get(classOf[Dog])
        .encode(
          new BsonDocumentWriter(insertedDoc),
          Dog("Buddy", "Golden"),
          EncoderContext.builder().build()
        )

      insertedDoc.containsKey("_type") shouldBe true
      insertedDoc.getString("_type").getValue shouldBe "Dog"
      registry
        .get(classOf[Animal])
        .decode(
          new BsonDocumentReader(insertedDoc),
          DecoderContext.builder().build()
        ) shouldBe Dog("Buddy", "Golden")
    }

  it should "encode all subtypes with discriminator in the upsert context" in {
    val registry = createRegistry[Animal]()
    val animalCodec = registry.get(classOf[Animal])

    val dogDoc = new BsonDocument()
    registry.get(classOf[Dog]).encode(new BsonDocumentWriter(dogDoc), Dog("Rex", "Lab"), EncoderContext.builder().build())
    dogDoc.getString("_type").getValue shouldBe "Dog"
    animalCodec.decode(new BsonDocumentReader(dogDoc), DecoderContext.builder().build()) shouldBe Dog("Rex", "Lab")

    val catDoc = new BsonDocument()
    registry.get(classOf[Cat]).encode(new BsonDocumentWriter(catDoc), Cat("Puss", 9), EncoderContext.builder().build())
    catDoc.getString("_type").getValue shouldBe "Cat"
    animalCodec.decode(new BsonDocumentReader(catDoc), DecoderContext.builder().build()) shouldBe Cat("Puss", 9)

    val birdDoc = new BsonDocument()
    registry.get(classOf[Bird]).encode(new BsonDocumentWriter(birdDoc), Bird("Tweety", true), EncoderContext.builder().build())
    birdDoc.getString("_type").getValue shouldBe "Bird"
    animalCodec.decode(new BsonDocumentReader(birdDoc), DecoderContext.builder().build()) shouldBe Bird("Tweety", true)
  }

  // ===== bulkWrite: covers both UpdateOneModel (concrete codec) and ReplaceOneModel (outer codec) =====

  "Sealed trait bulkWrite simulation" should
    "encode UpdateOneModel values with discriminator via concrete codec" in {
      // bulkWrite with UpdateOneModel + Updates.set encodes each value via the
      // concrete subtype codec — the same path as a regular Updates.set.
      val registry = createRegistry[Animal]()
      val animalCodec = registry.get(classOf[Animal])

      val dogDoc = new BsonDocument()
      registry.get(classOf[Dog]).encode(new BsonDocumentWriter(dogDoc), Dog("Max", "Poodle"), EncoderContext.builder().build())
      dogDoc.getString("_type").getValue shouldBe "Dog"
      animalCodec.decode(new BsonDocumentReader(dogDoc), DecoderContext.builder().build()) shouldBe Dog("Max", "Poodle")

      val catDoc = new BsonDocument()
      registry.get(classOf[Cat]).encode(new BsonDocumentWriter(catDoc), Cat("Shadow", 3), EncoderContext.builder().build())
      catDoc.getString("_type").getValue shouldBe "Cat"
      animalCodec.decode(new BsonDocumentReader(catDoc), DecoderContext.builder().build()) shouldBe Cat("Shadow", 3)

      val birdDoc = new BsonDocument()
      registry.get(classOf[Bird]).encode(new BsonDocumentWriter(birdDoc), Bird("Coco", canFly = false), EncoderContext.builder().build())
      birdDoc.getString("_type").getValue shouldBe "Bird"
      animalCodec.decode(new BsonDocumentReader(birdDoc), DecoderContext.builder().build()) shouldBe Bird("Coco", canFly = false)
    }

  it should "encode ReplaceOneModel document with discriminator in the sealed field (outer-document path)" in {
    // bulkWrite with ReplaceOneModel encodes the full replacement document via
    // the outer type codec — the same path as a regular replaceOne.
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .build

    val ownerCodec = registry.get(classOf[Owner])

    Seq(
      Owner("Alice", Dog("Rex", "Labrador")),
      Owner("Bob", Cat("Whiskers", 9)),
      Owner("Carol", Bird("Tweety", canFly = true))
    ).foreach { owner =>
      val doc = new BsonDocument()
      ownerCodec.encode(new BsonDocumentWriter(doc), owner, EncoderContext.builder().build())
      doc.getDocument("pet").containsKey("_type") shouldBe true
      ownerCodec.decode(new BsonDocumentReader(doc), DecoderContext.builder().build()) shouldBe owner
    }
  }

  it should "handle mixed UpdateOneModel and ReplaceOneModel operations in a single bulk run" in {
    val baseRegistry = createBaseRegistry()
    val registry = RegistryBuilder
      .from(baseRegistry)
      .registerSealed[Animal]
      .register[Owner]
      .build

    val ownerCodec = registry.get(classOf[Owner])
    val animalCodec = registry.get(classOf[Animal])

    // ReplaceOneModel: full Owner document
    val replaceDoc = new BsonDocument()
    ownerCodec.encode(new BsonDocumentWriter(replaceDoc), Owner("Alice", Dog("Rex", "Lab")), EncoderContext.builder().build())
    replaceDoc.getDocument("pet").getString("_type").getValue shouldBe "Dog"

    // UpdateOneModel: concrete subtype value via Updates.set
    val updateDoc = new BsonDocument()
    registry.get(classOf[Bird]).encode(new BsonDocumentWriter(updateDoc), Bird("Tweety", true), EncoderContext.builder().build())
    updateDoc.getString("_type").getValue shouldBe "Bird"

    // Both decode correctly via their respective codecs
    ownerCodec.decode(new BsonDocumentReader(replaceDoc), DecoderContext.builder().build()) shouldBe
      Owner("Alice", Dog("Rex", "Lab"))
    animalCodec.decode(new BsonDocumentReader(updateDoc), DecoderContext.builder().build()) shouldBe
      Bird("Tweety", true)
  }

end SealedTraitCodecSpec
