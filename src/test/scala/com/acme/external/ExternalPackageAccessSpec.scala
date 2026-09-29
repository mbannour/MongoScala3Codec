package com.acme.external

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.CodecTestKit
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Square(side: Double) extends Shape

/** Derivation exercised from a package outside `io.github.mbannour`.
  *
  * Every other test in this suite lives under `io.github.mbannour`, where the library's `private[mbannour]` macro helpers are accessible for
  * free. Real users are not, so a macro that splices a reference to one of those helpers would compile here and fail for them. This spec is
  * the only place that difference is observable.
  */
class ExternalPackageAccessSpec extends AnyFlatSpec with Matchers:

  private val primitives = CodecRegistries.fromCodecs(new org.bson.codecs.DoubleCodec())

  private val registry: CodecRegistry =
    io.github.mbannour.mongo.codecs.RegistryBuilder.from(primitives).registerSealed[Shape].build

  "A codec derived outside io.github.mbannour" should "encode and decode a sealed hierarchy" in {
    val codec = registry.get(classOf[Shape])
    val kit = CodecTestKit(codec)
    val doc = kit.encode(Circle(1.5))

    doc.getString("_type").getValue shouldBe "Circle"
    kit.decode(doc) shouldBe Circle(1.5)
  }
end ExternalPackageAccessSpec
