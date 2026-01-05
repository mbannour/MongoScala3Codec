package io.github.mbannour.mongo.codecs

import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder.*

class AdditionalRegistryBuilderSpec extends AnyFlatSpec with Matchers:

  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  case class SimpleUser(name: String, age: Int)
  case class Product(id: Int, title: String, price: Double)
  case class Order(id: Int, userId: Int, productId: Int)

  "RegistryBuilder.from" should "create builder from existing registry" in {
    val builder = RegistryBuilder.from(defaultBsonRegistry)
    val registry = builder.register[SimpleUser].build

    registry.get(classOf[SimpleUser]) should not be null
  }

  "RegistryBuilder" should "support building from existing registry" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[SimpleUser]
      .build

    registry.get(classOf[SimpleUser]) should not be null
  }

  "RegistryBuilder.configure" should "allow configuration updates" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .configure(_.withIgnoreNone)
      .register[SimpleUser]
      .build

    registry should not be null
  }

  it should "support multiple configuration updates" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .configure(_.withIgnoreNone.withDiscriminatorField("_class"))
      .register[SimpleUser]
      .build

    registry should not be null
  }

  "RegistryBuilder.ignoreNone" should "set NoneHandling to Ignore" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .ignoreNone
      .register[SimpleUser]
      .build

    registry should not be null
  }

  "RegistryBuilder.encodeNone" should "set NoneHandling to Encode" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .encodeNone
      .register[SimpleUser]
      .build

    registry should not be null
  }

  "RegistryBuilder.registerAll" should "register multiple types" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .registerAll[(SimpleUser, Product, Order)]
      .build

    registry.get(classOf[SimpleUser]) should not be null
    registry.get(classOf[Product]) should not be null
    registry.get(classOf[Order]) should not be null
  }

  "RegistryBuilder.register" should "register single type" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[SimpleUser]
      .build

    registry.get(classOf[SimpleUser]) should not be null
  }

  it should "support chaining multiple registers" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[SimpleUser]
      .register[Product]
      .register[Order]
      .build

    registry.get(classOf[SimpleUser]) should not be null
    registry.get(classOf[Product]) should not be null
    registry.get(classOf[Order]) should not be null
  }

  "RegistryBuilder" should "work with ignoreNone configuration" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .ignoreNone
      .registerAll[(SimpleUser, Product)]
      .build

    registry.get(classOf[SimpleUser]) should not be null
    registry.get(classOf[Product]) should not be null
  }

  it should "work with encodeNone configuration" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .encodeNone
      .registerAll[(SimpleUser, Product)]
      .build

    registry.get(classOf[SimpleUser]) should not be null
    registry.get(classOf[Product]) should not be null
  }

  it should "support complex builder chains" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .configure(_.withIgnoreNone.withDiscriminatorField("_type"))
      .register[SimpleUser]
      .registerAll[(Product, Order)]
      .build

    registry.get(classOf[SimpleUser]) should not be null
    registry.get(classOf[Product]) should not be null
    registry.get(classOf[Order]) should not be null
  }

end AdditionalRegistryBuilderSpec
