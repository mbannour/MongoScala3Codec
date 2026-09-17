package io.github.mbannour.mongo.codecs

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.types.ObjectId
import org.bson.{BsonReader, BsonWriter}
import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import io.github.mbannour.bson.macros.{BsonId, BsonIgnore}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

sealed trait PropAnimal
case class PropDog(name: String, age: Int) extends PropAnimal
case class PropCat(name: String) extends PropAnimal

enum PropColour:
  case Red, Green, Blue

case class PropSwatch(name: String, colour: PropColour)

case class PropNode(value: String, next: Option[PropNode])

final case class PropTicket(code: String)

class PropTicketCodec extends Codec[PropTicket]:
  override def encode(writer: BsonWriter, value: PropTicket, context: EncoderContext): Unit = writer.writeString(value.code)
  override def decode(reader: BsonReader, context: DecoderContext): PropTicket = PropTicket(reader.readString())
  override def getEncoderClass: Class[PropTicket] = classOf[PropTicket]

case class PropOrder(ticket: PropTicket, buyer: String)

/** The round-trip law over many generated values, as the complement to the golden suite.
  *
  * The goldens in `compatibility/` answer "does this value produce exactly this document?" and remain the authority on the wire format.
  * These properties answer a different question - "across many values, does the model survive the trip?" - and deliberately assert nothing
  * about which document was written.
  *
  * The law tested here is `decode(encode(value)) == value`, and it is claimed only for the *lossless* mappings below. It is not a universal
  * property of every `Codec[T]`: `@BsonIgnore` drops a field on purpose, and a lossy user codec may too. Those are stated separately rather
  * than bent to fit.
  *
  * Generators exercise boundaries on purpose. The existing `PropertyBasedCodecSpec` draws from `Gen.alphaNumStr.suchThat(_.nonEmpty)` and
  * `Gen.choose(0, 120)`, which can never produce an empty string, a non-ASCII character or a negative number - exactly the values most
  * likely to break an encoder.
  */
class CodecPropertySpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  /** ScalaTest's default is ten evaluations per property, which is too few to reach ten alternating edge cases with any confidence. Two
    * hundred is still a fraction of a second per property for in-memory codecs, and makes it near-certain that every edge is visited.
    *
    * Raising it costs nothing in diagnosability: a failure prints the generated value that broke the property and the run's `Init Seed`,
    * and that exact run replays with
    *
    * {{{
    * sbt "testOnly *CodecPropertySpec -- -S <seed>"
    * }}}
    *
    * ScalaTest's generator-driven `forAll` does not shrink, so the value it prints is the one that failed. The generators here draw from
    * small explicit pools rather than the whole of `Int` or `String`, so that value is already about as small as a shrinker would make it.
    */
  override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 200)

  private val primitives: CodecRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  /** Strings that have caused trouble in document stores: empty, non-ASCII, and characters that matter to MongoDB's own query syntax. They
    * are stored here as *values*, where they are ordinary text - field names are compile-time schema and are never generated.
    */
  private val edgeString: Gen[String] = Gen.oneOf(
    Gen.const(""),
    Gen.const("Alice"),
    Gen.const("  padded  "),
    Gen.const("héllo — 世界 🎉"),
    Gen.const("she said \"hi\""),
    Gen.const("C:\\path\\to"),
    Gen.const("$value"),
    Gen.const("field.name"),
    Gen.const("a\nb\tc"),
    Gen.alphaNumStr
  )

  /** Random `Int` generation practically never produces the boundaries, so they are drawn explicitly. */
  private val edgeInt: Gen[Int] = Gen.oneOf(
    Gen.const(Int.MinValue),
    Gen.const(-1),
    Gen.const(0),
    Gen.const(1),
    Gen.const(Int.MaxValue),
    Gen.choose(Int.MinValue, Int.MaxValue)
  )

  /** The single shape every property below takes. Test-only and private: the public API gained nothing in this task. */
  private def roundTrips[T](codec: Codec[T], values: Gen[T]): Unit =
    val kit = CodecTestKit(codec)
    forAll(values)(value => kit.assertRoundTrip(value))

  case class Person(name: String, age: Int, active: Boolean)

  private val persons: Gen[Person] =
    for
      name <- edgeString
      age <- edgeInt
      active <- Gen.oneOf(true, false)
    yield Person(name, age, active)

  "A product codec" should "round-trip every generated value" in {
    val codec = RegistryBuilder.from(primitives).register[Person].build.get(classOf[Person])

    roundTrips(codec, persons)
  }

  /** Round trip alone could survive a codec that dropped a field *and* restored it from a default. These models have no defaults, so a
    * dropped field fails the round trip - but the field set is asserted anyway, because it is the invariant that would catch the mistake
    * directly. It is a structural check, not a wire golden: no value is asserted, and the goldens remain the authority on those.
    */
  it should "write exactly the declared fields, whatever the generated values" in {
    val kit = CodecTestKit(RegistryBuilder.from(primitives).register[Person].build.get(classOf[Person]))

    forAll(persons) { person =>
      import scala.jdk.CollectionConverters.*
      kit.encode(person).keySet().asScala.toSet shouldBe Set("name", "age", "active")
    }
  }

  case class Address(city: String, zip: Int)
  case class Resident(name: String, address: Address)

  "A nested product codec" should "round-trip every generated value" in {
    val registry = RegistryBuilder.from(primitives).register[Address].register[Resident].build

    val residents =
      for
        name <- edgeString
        city <- edgeString
        zip <- edgeInt
      yield Resident(name, Address(city, zip))

    roundTrips(registry.get(classOf[Resident]), residents)
  }

  case class Contact(name: String, email: Option[String])

  "An Option codec" should "round-trip None, an empty Some, and ordinary values alike" in {
    val codec = RegistryBuilder.from(primitives).register[Contact].build.get(classOf[Contact])

    val contacts =
      for
        name <- edgeString
        email <- Gen.oneOf(Gen.const(None), Gen.const(Some("")), edgeString.map(Some(_)))
      yield Contact(name, email)

    roundTrips(codec, contacts)
  }

  case class Basket(owner: String, tags: List[String])

  "A collection codec" should "round-trip lists from empty to several elements" in {
    val codec = RegistryBuilder.from(primitives).register[Basket].build.get(classOf[Basket])

    val baskets =
      for
        owner <- edgeString
        size <- Gen.choose(0, 5)
        tags <- Gen.listOfN(size, edgeString)
      yield Basket(owner, tags)

    roundTrips(codec, baskets)
  }

  case class Subscriber(@BsonProperty("email_address") email: String, age: Int)

  "An annotated codec" should "round-trip every generated value, the renamed field included" in {
    val codec = RegistryBuilder.from(primitives).register[Subscriber].build.get(classOf[Subscriber])

    val subscribers =
      for
        email <- edgeString
        age <- edgeInt
      yield Subscriber(email, age)

    roundTrips(codec, subscribers)
  }

  case class Entity(@BsonId id: ObjectId, name: String)

  "A @BsonId codec" should "round-trip every generated value" in {
    val codec = RegistryBuilder.from(primitives).register[Entity].build.get(classOf[Entity])

    /** `new ObjectId()` always yields a current timestamp and an increasing counter, which is a narrow slice of the twelve bytes an `_id`
      * can hold. Generating the bytes directly reaches the ends of the range, where a sign-extension or truncation bug would live.
      */
    val objectIds: Gen[ObjectId] = Gen.oneOf(
      Gen.const(new ObjectId(Array.fill[Byte](12)(0))),
      Gen.const(new ObjectId(Array.fill[Byte](12)(-1))),
      Gen.delay(Gen.const(new ObjectId())),
      Gen.listOfN(12, Gen.choose(Byte.MinValue, Byte.MaxValue)).map(bytes => new ObjectId(bytes.toArray))
    )

    val entities =
      for
        id <- objectIds
        name <- edgeString
      yield Entity(id, name)

    roundTrips(codec, entities)
  }

  /** The highest-value property here: it drives the root codec, so every generated value exercises discriminator dispatch - writing the
    * subtype's value and choosing the right constructor on the way back.
    */
  "A sealed codec" should "round-trip generated values of every subtype through the root" in {
    val kit = CodecTestKit(RegistryBuilder.from(primitives).registerSealed[PropAnimal].build.get(classOf[PropAnimal]))

    val dogs = for n <- edgeString; a <- edgeInt yield PropDog(n, a)
    val cats = for n <- edgeString yield PropCat(n)
    val animals: Gen[PropAnimal] = Gen.oneOf(dogs, cats)

    // A property over an ADT is worth only the subtypes it happens to draw, so the run records what it saw and the assertion below
    // turns "every subtype" from an intention into something the test can fail on.
    val visited = scala.collection.mutable.Set.empty[Class[?]]

    forAll(animals) { animal =>
      visited += animal.getClass
      kit.assertRoundTrip(animal)
    }

    visited.toSet shouldBe Set(classOf[PropDog], classOf[PropCat])
  }

  /** An enum has finitely many cases, so iterating them proves more than sampling would. */
  "A string enum codec" should "round-trip every case of the enum" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromRegistries(CodecRegistries.fromProviders(EnumValueCodecProvider.forStringEnum[PropColour]), primitives))
      .register[PropSwatch]
      .build

    val kit = CodecTestKit(registry.get(classOf[PropSwatch]))

    PropColour.values.foreach(colour => kit.assertRoundTrip(PropSwatch("wall", colour)))
  }

  "An ordinal enum codec" should "round-trip every case of the enum" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromRegistries(CodecRegistries.fromProviders(EnumValueCodecProvider.forOrdinalEnum[PropColour]), primitives))
      .register[PropSwatch]
      .build

    val kit = CodecTestKit(registry.get(classOf[PropSwatch]))

    PropColour.values.foreach(colour => kit.assertRoundTrip(PropSwatch("wall", colour)))
  }

  /** Depth is bounded by construction: the generator recurses on `depth - 1` and stops at zero, so no generated value can be unbounded and
    * none is cyclic. Five levels is enough to exercise nesting; this is a correctness property, not a stack-depth benchmark.
    */
  "A recursive codec" should "round-trip bounded trees" in {
    val codec = RegistryBuilder.from(primitives).register[PropNode].build.get(classOf[PropNode])

    def nodeOfDepth(depth: Int): Gen[PropNode] =
      if depth <= 0 then edgeString.map(PropNode(_, None))
      else
        for
          value <- edgeString
          next <- Gen.option(nodeOfDepth(depth - 1))
        yield PropNode(value, next)

    roundTrips(codec, Gen.choose(0, 5).flatMap(nodeOfDepth))
  }

  "A model with a hand-written field codec" should "round-trip every generated value" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromRegistries(CodecRegistries.fromCodecs(new PropTicketCodec()), primitives))
      .register[PropOrder]
      .build

    val orders =
      for
        code <- edgeString
        buyer <- edgeString
      yield PropOrder(PropTicket(code), buyer)

    roundTrips(registry.get(classOf[PropOrder]), orders)
  }

  case class Session(name: String, @BsonIgnore scratch: String = "idle")

  /** `@BsonIgnore` means the field is not stored, so `decode(encode(v)) == v` is false by design and its failure would be a property bug,
    * not a codec bug. The law that does hold is stated instead: the value comes back with the ignored field at its constructor default. No
    * normalization machinery for one case.
    */
  "An intentionally lossy codec" should "round-trip to the value with its ignored field reset, not to the original" in {
    val kit = CodecTestKit(RegistryBuilder.from(primitives).register[Session].build.get(classOf[Session]))

    forAll(edgeString, edgeString) { (name, scratch) =>
      kit.decode(kit.encode(Session(name, scratch))) shouldBe Session(name, "idle")
    }
  }

  case class Counter(ticks: Long)

  /** `Int` boundaries are exercised by every model above; `Long` is a different BSON type (Int64 rather than Int32), and the values past
    * `Int.MaxValue` are the ones that would expose a narrowing on the way out or back.
    */
  "A Long codec" should "round-trip the Int64 boundaries, including values no Int32 can hold" in {
    val codec = RegistryBuilder.from(primitives).register[Counter].build.get(classOf[Counter])

    val edgeLong: Gen[Long] = Gen.oneOf(
      Gen.const(Long.MinValue),
      Gen.const(Int.MinValue.toLong - 1L),
      Gen.const(-1L),
      Gen.const(0L),
      Gen.const(1L),
      Gen.const(Int.MaxValue.toLong + 1L),
      Gen.const(Long.MaxValue),
      Gen.choose(Long.MinValue, Long.MaxValue)
    )

    roundTrips(codec, edgeLong.map(Counter(_)))
  }

  case class Reading(value: Double)

  /** `Double.NaN != Double.NaN`, so a naive round-trip assertion would report a failure for a value the codec preserved perfectly.
    * Characterized here with the right predicate, and kept out of the generators above so it cannot manufacture a false codec failure.
    */
  "A Double codec" should "preserve NaN, which ordinary equality cannot express" in {
    val kit = CodecTestKit(RegistryBuilder.from(primitives).register[Reading].build.get(classOf[Reading]))

    kit.decode(kit.encode(Reading(Double.NaN))).value.isNaN shouldBe true
  }

  it should "preserve infinities and the sign of negative zero" in {
    val kit = CodecTestKit(RegistryBuilder.from(primitives).register[Reading].build.get(classOf[Reading]))

    kit.decode(kit.encode(Reading(Double.PositiveInfinity))).value shouldBe Double.PositiveInfinity
    kit.decode(kit.encode(Reading(Double.NegativeInfinity))).value shouldBe Double.NegativeInfinity

    // -0.0 == 0.0 is true, so the sign has to be read off the bits to mean anything.
    val negativeZero = kit.decode(kit.encode(Reading(-0.0))).value
    java.lang.Double.doubleToRawLongBits(negativeZero) shouldBe java.lang.Double.doubleToRawLongBits(-0.0)
  }

  it should "round-trip ordinary finite values" in {
    val codec = RegistryBuilder.from(primitives).register[Reading].build.get(classOf[Reading])

    val finite = Gen.oneOf(
      Gen.const(0.0),
      Gen.const(Double.MinPositiveValue),
      Gen.const(Double.MaxValue),
      Gen.const(-Double.MaxValue),
      Gen.choose(-1.0e9, 1.0e9)
    )

    roundTrips(codec, finite.map(Reading(_)))
  }
end CodecPropertySpec
