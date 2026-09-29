package io.github.mbannour.mongo.codecs

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

case class Contact(name: String, nickname: Option[String])

/** A `given CodecConfig` in scope configures the builder.
  *
  * Before 1.0 it did not: the builder resolved its configuration from a hardcoded default, so a declared `given` was silently dropped and
  * `None` was written as `null` anyway. The configuration only applied when passed explicitly through `withConfig`. These tests assert the
  * stored document under each route, because the difference was invisible in the builder's own API.
  */
class GivenCodecConfigSpec extends AnyFlatSpec with Matchers:

  private val primitives = CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec())

  private def encodeContact(registry: CodecRegistry): org.bson.BsonDocument =
    CodecTestKit(registry.get(classOf[Contact])).encode(Contact("Ada", None))

  "A bare given CodecConfig" should "configure a builder created with newBuilder" in {
    given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    encodeContact(primitives.newBuilder.register[Contact].build).containsKey("nickname") shouldBe false
  }

  it should "configure a builder created with RegistryBuilder.from" in {
    given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    encodeContact(RegistryBuilder.from(primitives).register[Contact].build).containsKey("nickname") shouldBe false
  }

  "No given CodecConfig in scope" should "leave the documented default in place" in {
    val doc = encodeContact(primitives.newBuilder.register[Contact].build)

    doc.containsKey("nickname") shouldBe true
    doc.isNull("nickname") shouldBe true
  }

  "An explicit configuration" should "still override an in-scope given" in {
    given CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    val doc = encodeContact(
      primitives.newBuilder.configure(_.withEncodeNone).register[Contact].build
    )

    doc.isNull("nickname") shouldBe true
  }
end GivenCodecConfigSpec
