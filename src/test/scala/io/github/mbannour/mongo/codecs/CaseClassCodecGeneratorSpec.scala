package io.github.mbannour.mongo.codecs

import org.bson.codecs.{BsonValueCodecProvider, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.{BsonDocument, BsonDocumentWriter, BsonInvalidOperationException}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.mongodb.scala.bson.annotations.BsonProperty

import scala.reflect.ClassTag

class CaseClassCodecGeneratorSpec extends AnyFlatSpec with Matchers {

  case class SimplePerson(name: String, age: Int, active: Boolean)
  
  case class PersonWithOptional(name: String, age: Option[Int], nickname: Option[String])
  
  case class PersonWithAnnotation(@BsonProperty("full_name") name: String, age: Int)

  private def createBaseRegistry(): CodecRegistry = {
    CodecRegistries.fromProviders(new BsonValueCodecProvider())
  }

  private inline def createCodec[T](config: CodecConfig = CodecConfig())(using ClassTag[T]): org.bson.codecs.Codec[T] = {
    val baseRegistry = createBaseRegistry()
    CaseClassCodecGenerator.generateCodec[T](config, baseRegistry)
  }

  private inline def encodeToDocument[T](value: T, config: CodecConfig = CodecConfig())(using ClassTag[T]): BsonDocument = {
    val codec = createCodec[T](config)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    codec.encode(writer, value, encoderContext)
    document
  }

  "CaseClassCodecGenerator" should "generate codec with correct encoder class" in {
    val codec = createCodec[SimplePerson]()
    codec.getEncoderClass shouldBe classOf[SimplePerson]
  }

  it should "encode simple case class to BSON document correctly" in {
    val person = SimplePerson("John", 30, true)
    val document = encodeToDocument(person)
    
    document.getString("name").getValue shouldBe "John"
    document.getInt32("age").getValue shouldBe 30
    document.getBoolean("active").getValue shouldBe true
  }

  it should "handle optional fields when encodeNone is false" in {
    val person = PersonWithOptional("Alice", Some(25), None)
    val document = encodeToDocument(person, CodecConfig(noneHandling = NoneHandling.Ignore))
    
    document.getString("name").getValue shouldBe "Alice"
    document.getInt32("age").getValue shouldBe 25
    document.containsKey("nickname") shouldBe false // None should be omitted
  }

  it should "handle optional fields when encodeNone is true" in {
    val person = PersonWithOptional("Bob", Some(30), None)
    val document = encodeToDocument(person, CodecConfig(noneHandling = NoneHandling.Encode))
    
    document.getString("name").getValue shouldBe "Bob"
    document.getInt32("age").getValue shouldBe 30
    document.containsKey("nickname") shouldBe true // Should include None as null
    document.isNull("nickname") shouldBe true
  }

  it should "handle Some values in optional fields" in {
    val person = PersonWithOptional("Charlie", Some(35), Some("Chuck"))
    val document = encodeToDocument(person)
    
    document.getString("name").getValue shouldBe "Charlie"
    document.getInt32("age").getValue shouldBe 35
    document.getString("nickname").getValue shouldBe "Chuck"
  }

  it should "respect @BsonProperty annotations" in {
    val person = PersonWithAnnotation("David", 40)
    val document = encodeToDocument(person)
    
    // Should use the annotated field name
    document.containsKey("full_name") shouldBe true
    document.containsKey("name") shouldBe false
    document.getString("full_name").getValue shouldBe "David"
    document.getInt32("age").getValue shouldBe 40
  }

  it should "throw exception for null values during encoding" in {
    val codec = createCodec[SimplePerson]()
    val writer = new BsonDocumentWriter(new BsonDocument())
    val encoderContext = EncoderContext.builder().build()
    
    assertThrows[BsonInvalidOperationException] {
      codec.encode(writer, null, encoderContext)
    }
  }
}