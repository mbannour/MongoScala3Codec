package io.github.mbannour.bson.macros

import scala.jdk.CollectionConverters._

import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.{BsonValueCodecProvider, EncoderContext}
import org.bson.{BsonDocument, BsonDocumentWriter}
import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CaseClassBsonWriterSpec extends AnyFlatSpec with Matchers:

  case class SimplePerson(name: String, age: Int, height: Double, active: Boolean, id: Long)

  case class PersonWithOptional(name: String, age: Option[Int], nickname: Option[String])

  case class PersonWithAnnotation(@BsonProperty("full_name") name: String, age: Int)

  case class PersonWithMap(name: String, attributes: Map[String, String])

  case class PersonWithList(name: String, hobbies: List[String])

  "CaseClassBsonWriter" should "write simple primitive fields correctly" in {
    val person = SimplePerson("John", 30, 5.9, true, 123456789L)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val registry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val encoderContext = EncoderContext.builder().build()

    writer.writeStartDocument()
    CaseClassBsonWriter.writeCaseClassData(
      person.getClass.getSimpleName,
      writer,
      person,
      encoderContext,
      false,
      registry
    )
    writer.writeEndDocument()

    document.getString("name").getValue shouldBe "John"
    document.getInt32("age").getValue shouldBe 30
    document.getDouble("height").getValue shouldBe 5.9
    document.getBoolean("active").getValue shouldBe true
    document.getInt64("id").getValue shouldBe 123456789L
  }

  it should "handle optional fields when encodeNone is false" in {
    val person = PersonWithOptional("Alice", Some(25), None)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val registry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val encoderContext = EncoderContext.builder().build()

    writer.writeStartDocument()
    CaseClassBsonWriter.writeCaseClassData(
      person.getClass.getSimpleName,
      writer,
      person,
      encoderContext,
      false,
      registry
    )
    writer.writeEndDocument()

    document.getString("name").getValue shouldBe "Alice"
    document.getInt32("age").getValue shouldBe 25
    document.containsKey("nickname") shouldBe false
  }

  it should "handle optional fields when encodeNone is true" in {
    val person = PersonWithOptional("Bob", Some(30), None)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val registry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val encoderContext = EncoderContext.builder().build()

    writer.writeStartDocument()
    CaseClassBsonWriter.writeCaseClassData(
      person.getClass.getSimpleName,
      writer,
      person,
      encoderContext,
      true,
      registry
    )
    writer.writeEndDocument()

    document.getString("name").getValue shouldBe "Bob"
    document.getInt32("age").getValue shouldBe 30
    document.isNull("nickname") shouldBe true
  }

  it should "handle Some values in optional fields" in {
    val person = PersonWithOptional("Charlie", Some(35), Some("Chuck"))
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val registry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val encoderContext = EncoderContext.builder().build()

    writer.writeStartDocument()
    CaseClassBsonWriter.writeCaseClassData(
      person.getClass.getSimpleName,
      writer,
      person,
      encoderContext,
      false,
      registry
    )
    writer.writeEndDocument()

    document.getString("name").getValue shouldBe "Charlie"
    document.getInt32("age").getValue shouldBe 35
    document.getString("nickname").getValue shouldBe "Chuck"
  }

  it should "respect @BsonProperty annotations" in {
    val person = PersonWithAnnotation("David", 40)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val registry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val encoderContext = EncoderContext.builder().build()

    writer.writeStartDocument()
    CaseClassBsonWriter.writeCaseClassData(
      person.getClass.getSimpleName,
      writer,
      person,
      encoderContext,
      false,
      registry
    )
    writer.writeEndDocument()

    document.getString("full_name").getValue shouldBe "David"
    document.getInt32("age").getValue shouldBe 40
    document.containsKey("name") shouldBe false
  }

  it should "write Map[String, String] fields as BSON documents" in {
    val person = PersonWithMap("Eve", Map("city" -> "NYC", "country" -> "USA"))
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val registry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val encoderContext = EncoderContext.builder().build()

    writer.writeStartDocument()
    CaseClassBsonWriter.writeCaseClassData(
      person.getClass.getSimpleName,
      writer,
      person,
      encoderContext,
      false,
      registry
    )
    writer.writeEndDocument()

    document.getString("name").getValue shouldBe "Eve"
    val attributes = document.getDocument("attributes")
    attributes.getString("city").getValue shouldBe "NYC"
    attributes.getString("country").getValue shouldBe "USA"
  }

  it should "write empty maps correctly" in {
    val person = PersonWithMap("Frank", Map.empty[String, String])
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val registry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val encoderContext = EncoderContext.builder().build()

    writer.writeStartDocument()
    CaseClassBsonWriter.writeCaseClassData(
      person.getClass.getSimpleName,
      writer,
      person,
      encoderContext,
      false,
      registry
    )
    writer.writeEndDocument()

    document.getString("name").getValue shouldBe "Frank"
    val attributes = document.getDocument("attributes")
    attributes.isEmpty shouldBe true
  }

  it should "write Iterable fields as BSON arrays" in {
    val person = PersonWithList("Grace", List("reading", "swimming", "coding"))
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val registry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val encoderContext = EncoderContext.builder().build()

    writer.writeStartDocument()
    CaseClassBsonWriter.writeCaseClassData(
      person.getClass.getSimpleName,
      writer,
      person,
      encoderContext,
      false,
      registry
    )
    writer.writeEndDocument()

    document.getString("name").getValue shouldBe "Grace"
    val hobbies = document.getArray("hobbies")
    val hobbyList = hobbies.getValues.asScala.map(_.asString().getValue).toList
    hobbyList shouldBe List("reading", "swimming", "coding")
  }

  it should "write empty iterables correctly" in {
    val person = PersonWithList("Henry", List.empty[String])
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val registry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val encoderContext = EncoderContext.builder().build()

    writer.writeStartDocument()
    CaseClassBsonWriter.writeCaseClassData(
      person.getClass.getSimpleName,
      writer,
      person,
      encoderContext,
      false,
      registry
    )
    writer.writeEndDocument()

    document.getString("name").getValue shouldBe "Henry"
    val hobbies = document.getArray("hobbies")
    hobbies.isEmpty shouldBe true
  }
end CaseClassBsonWriterSpec
