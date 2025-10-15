package io.github.mbannour.mongo.codecs

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import RegistryBuilder.*

/** Property-based tests for a simple ADT (modeled as concrete case classes with a discriminator field).
  *
  * We register each concrete case class and verify round-trip symmetry using ScalaCheck generators.
  */
class PropertyBasedAdtSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  // Simple ADT via concrete types with manual discriminator field
  sealed trait Shape
  case class Circle(_id: ObjectId, radius: Double, shapeType: String = "Circle") extends Shape
  case class Rectangle(_id: ObjectId, width: Double, height: Double, shapeType: String = "Rectangle") extends Shape

  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  given Arbitrary[ObjectId] = Arbitrary(Gen.const(new ObjectId()))

  given Arbitrary[Circle] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    r <- Gen.choose(0.0, 10_000.0)
  yield Circle(id, r))

  given Arbitrary[Rectangle] = Arbitrary(for
    id <- Arbitrary.arbitrary[ObjectId]
    w <- Gen.choose(0.0, 10_000.0)
    h <- Gen.choose(0.0, 10_000.0)
  yield Rectangle(id, w, h))

  private val registry = RegistryBuilder
    .from(defaultBsonRegistry)
    .register[Circle]
    .register[Rectangle]
    .build

  "Circle codec" should "round-trip for arbitrary values" in {
    given Codec[Circle] = registry.get(classOf[Circle])
    forAll { (c: Circle) =>
      val rt = CodecTestKit.roundTrip(c)
      rt shouldBe c
      rt.shapeType shouldBe "Circle"
    }
  }

  "Rectangle codec" should "round-trip for arbitrary values" in {
    given Codec[Rectangle] = registry.get(classOf[Rectangle])
    forAll { (r: Rectangle) =>
      val rt = CodecTestKit.roundTrip(r)
      rt shouldBe r
      rt.shapeType shouldBe "Rectangle"
    }
  }
end PropertyBasedAdtSpec
