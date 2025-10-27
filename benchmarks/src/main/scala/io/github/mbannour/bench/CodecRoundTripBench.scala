package io.github.mbannour.bench

import io.github.mbannour.mongo.codecs.*
import io.github.mbannour.mongo.codecs.RegistryBuilder.*
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit
import scala.util.Random
import scala.compiletime.uninitialized

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class CodecRoundTripBench:

  // Models
  case class FlatUser(_id: ObjectId, name: String, age: Int, active: Boolean, score: Double)

  case class Address(street: String, city: String, zipCode: Int)
  case class NestedUser(_id: ObjectId, name: String, address: Option[Address])

  sealed trait Shape
  case class Circle(_id: ObjectId, radius: Double, shapeType: String = "Circle") extends Shape
  case class Rectangle(_id: ObjectId, width: Double, height: Double, shapeType: String = "Rectangle") extends Shape

  case class LargeCollections(
      _id: ObjectId,
      tags: List[String],
      scores: Vector[Int],
      attributes: Map[String, String]
  )

  // JMH parameter for collection size
  @Param(Array("0", "10", "1000"))
  var n: Int = uninitialized

  // State
  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  private var flatUser: FlatUser = uninitialized
  private var nestedUser: NestedUser = uninitialized
  private var circle: Circle = uninitialized
  private var rectangle: Rectangle = uninitialized
  private var big: LargeCollections = uninitialized

  private var flatCodec: Codec[FlatUser] = uninitialized
  private var nestedCodec: Codec[NestedUser] = uninitialized
  private var circleCodec: Codec[Circle] = uninitialized
  private var rectangleCodec: Codec[Rectangle] = uninitialized
  private var bigCodec: Codec[LargeCollections] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    val reg = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Address]
      .register[FlatUser]
      .register[NestedUser]
      .register[Circle]
      .register[Rectangle]
      .register[LargeCollections]
      .build

    flatCodec = reg.get(classOf[FlatUser])
    nestedCodec = reg.get(classOf[NestedUser])
    circleCodec = reg.get(classOf[Circle])
    rectangleCodec = reg.get(classOf[Rectangle])
    bigCodec = reg.get(classOf[LargeCollections])

    val rnd = new Random(42L)

    flatUser = FlatUser(
      _id = new ObjectId(),
      name = s"user-${rnd.nextInt(100000)}",
      age = 20 + rnd.nextInt(40),
      active = rnd.nextBoolean(),
      score = rnd.nextDouble() * 100.0
    )

    nestedUser = NestedUser(
      _id = new ObjectId(),
      name = s"nested-${rnd.nextInt(100000)}",
      address = if rnd.nextBoolean() then Some(Address("123 Main St", "Springfield", 12345)) else None
    )

    circle = Circle(new ObjectId(), radius = 3.14)
    rectangle = Rectangle(new ObjectId(), width = 4.0, height = 5.0)

    val tags = List.fill(n)(s"tag-${rnd.nextInt(1000)}")
    val scores = Vector.fill(n)(rnd.nextInt(10000))
    val attrs = (0 until n).iterator.map(i => s"k$i" -> s"v${rnd.nextInt(1000)}").toMap
    big = LargeCollections(new ObjectId(), tags, scores, attrs)

  // Benchmarks
  @Benchmark def roundTripFlat(bh: Blackhole): Unit =
    given Codec[FlatUser] = flatCodec
    bh.consume(CodecTestKit.roundTrip(flatUser))

  @Benchmark def roundTripNested(bh: Blackhole): Unit =
    given Codec[NestedUser] = nestedCodec
    bh.consume(CodecTestKit.roundTrip(nestedUser))

  @Benchmark def roundTripCircle(bh: Blackhole): Unit =
    given Codec[Circle] = circleCodec
    bh.consume(CodecTestKit.roundTrip(circle))

  @Benchmark def roundTripRectangle(bh: Blackhole): Unit =
    given Codec[Rectangle] = rectangleCodec
    bh.consume(CodecTestKit.roundTrip(rectangle))

  @Benchmark def roundTripLargeCollections(bh: Blackhole): Unit =
    given Codec[LargeCollections] = bigCodec
    bh.consume(CodecTestKit.roundTrip(big))
