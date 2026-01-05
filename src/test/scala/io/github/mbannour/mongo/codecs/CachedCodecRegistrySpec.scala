package io.github.mbannour.mongo.codecs

import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder.*

class CachedCodecRegistrySpec extends AnyFlatSpec with Matchers:

  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  case class User(name: String, age: Int)
  case class Product(id: Int, name: String)

  "CachedCodecRegistry" should "cache codec lookups" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    // First lookup
    val codec1 = registry.get(classOf[User])
    codec1 should not be null

    // Second lookup should return cached codec
    val codec2 = registry.get(classOf[User])
    codec2 shouldBe theSameInstanceAs(codec1)
  }

  it should "handle multiple types" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .register[Product]
      .build

    val userCodec = registry.get(classOf[User])
    val productCodec = registry.get(classOf[Product])

    userCodec should not be null
    productCodec should not be null
    userCodec should not be theSameInstanceAs(productCodec)
  }

  it should "throw exception for unregistered types" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .build

    an[org.bson.codecs.configuration.CodecConfigurationException] should be thrownBy {
      registry.get(classOf[Product])
    }
  }

  it should "cache multiple codec lookups independently" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .register[Product]
      .build

    val userCodec1 = registry.get(classOf[User])
    val productCodec1 = registry.get(classOf[Product])
    val userCodec2 = registry.get(classOf[User])
    val productCodec2 = registry.get(classOf[Product])

    userCodec1 shouldBe theSameInstanceAs(userCodec2)
    productCodec1 shouldBe theSameInstanceAs(productCodec2)
  }

  it should "work with builder pattern" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[User]
      .register[Product]
      .build

    registry.get(classOf[User]) should not be null
    registry.get(classOf[Product]) should not be null
  }

end CachedCodecRegistrySpec
