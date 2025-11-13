package io.github.mbannour.mongo.codecs

import io.github.mbannour.mongo.codecs.RegistryBuilder.*
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BsonCodecSpec extends AnyFlatSpec with Matchers:

  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  case class Person(name: String, age: Int)
  case class Address(street: String, city: String)

  "BsonCodec.fromCodec" should "create BsonCodec from MongoDB Codec" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Person]
      .build

    val mongoCodec: Codec[Person] = registry.get(classOf[Person])
    val bsonCodec = BsonCodec.fromCodec(mongoCodec)

    val person = Person("Alice", 30)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)

    bsonCodec.encode(writer, person, EncoderContext.builder().build())
    document.getString("name").getValue shouldBe "Alice"
    document.getInt32("age").getValue shouldBe 30

    val reader = new BsonDocumentReader(document)
    val decoded = bsonCodec.decode(reader, DecoderContext.builder().build())
    decoded shouldBe person
  }

  "BsonCodec.toCodec" should "convert BsonCodec to MongoDB Codec" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Person]
      .build

    val mongoCodec: Codec[Person] = registry.get(classOf[Person])
    val bsonCodec = BsonCodec.fromCodec(mongoCodec)
    val converted = bsonCodec.toCodec

    val person = Person("Bob", 25)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)

    converted.encode(writer, person, EncoderContext.builder().build())
    document.getString("name").getValue shouldBe "Bob"

    val reader = new BsonDocumentReader(document)
    val decoded = converted.decode(reader, DecoderContext.builder().build())
    decoded shouldBe person
  }

  "BsonCodec.encoderClass" should "return the correct runtime class" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Person]
      .build

    val mongoCodec: Codec[Person] = registry.get(classOf[Person])
    val bsonCodec = BsonCodec.fromCodec(mongoCodec)

    bsonCodec.encoderClass shouldBe classOf[Person]
  }

  "BsonCodec.imap" should "map codec to different type" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Person]
      .build

    val personCodec = BsonCodec.fromCodec(registry.get(classOf[Person]))

    // Map Person to String (name only)
    val nameCodec = personCodec.imap[String](_.name)(name => Person(name, 0))

    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)

    nameCodec.encode(writer, "Charlie", EncoderContext.builder().build())
    document.getString("name").getValue shouldBe "Charlie"

    val reader = new BsonDocumentReader(document)
    val decoded = nameCodec.decode(reader, DecoderContext.builder().build())
    decoded shouldBe "Charlie"
  }

  "BsonCodec.imap" should "handle complex transformations" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Person]
      .build

    val personCodec = BsonCodec.fromCodec(registry.get(classOf[Person]))

    case class SimplePerson(fullName: String)

    // Map Person to SimplePerson
    val simpleCodec = personCodec.imap[SimplePerson](p => SimplePerson(p.name))(sp => Person(sp.fullName, 0))

    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)

    simpleCodec.encode(writer, SimplePerson("David"), EncoderContext.builder().build())
    document.getString("name").getValue shouldBe "David"

    val reader = new BsonDocumentReader(document)
    val decoded = simpleCodec.decode(reader, DecoderContext.builder().build())
    decoded shouldBe SimplePerson("David")
  }

  "BsonCodec.derived" should "create codec for case class" in {
    given CodecConfig = CodecConfig()

    val bsonCodec = BsonCodec.derived[Person]

    val person = Person("Eve", 28)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)

    bsonCodec.encode(writer, person, EncoderContext.builder().build())
    document.getString("name").getValue shouldBe "Eve"
    document.getInt32("age").getValue shouldBe 28

    val reader = new BsonDocumentReader(document)
    val decoded = bsonCodec.decode(reader, DecoderContext.builder().build())
    decoded shouldBe person
  }

  "BsonCodec.derived" should "work with simple case classes" in {
    given CodecConfig = CodecConfig()

    val bsonCodec = BsonCodec.derived[Address]

    val address = Address("Main St", "NYC")
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)

    bsonCodec.encode(writer, address, EncoderContext.builder().build())
    document.getString("street").getValue shouldBe "Main St"
    document.getString("city").getValue shouldBe "NYC"

    val reader = new BsonDocumentReader(document)
    val decoded = bsonCodec.decode(reader, DecoderContext.builder().build())
    decoded shouldBe address
  }

  "BsonCodec.encoderClass" should "return correct class for derived codec" in {
    given CodecConfig = CodecConfig()

    val bsonCodec = BsonCodec.derived[Person]
    bsonCodec.encoderClass shouldBe classOf[Person]
  }

  "BsonCodec" should "support chaining imap transformations" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Person]
      .build

    val personCodec = BsonCodec.fromCodec(registry.get(classOf[Person]))

    // Chain multiple transformations
    val upperNameCodec = personCodec
      .imap[String](_.name)(name => Person(name, 0))
      .imap[String](_.toUpperCase)(_.toLowerCase)

    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)

    upperNameCodec.encode(writer, "GEORGE", EncoderContext.builder().build())
    document.getString("name").getValue shouldBe "george"

    val reader = new BsonDocumentReader(document)
    val decoded = upperNameCodec.decode(reader, DecoderContext.builder().build())
    decoded shouldBe "GEORGE"
  }

end BsonCodecSpec
