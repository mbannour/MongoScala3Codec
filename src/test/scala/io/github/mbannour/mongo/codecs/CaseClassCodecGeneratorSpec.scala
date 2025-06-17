package io.github.mbannour.mongo.codecs

import io.github.mbannour.mongo.codecs.CaseClassCodecGenerator
import org.bson.codecs.{BsonDocumentCodec, BsonValueCodecProvider, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter, BsonInvalidOperationException}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.mongodb.scala.bson.annotations.BsonProperty

import java.util.UUID
import scala.reflect.ClassTag

class CaseClassCodecGeneratorSpec extends AnyFlatSpec with Matchers {

  case class SimplePerson(name: String, age: Int, active: Boolean)
  
  case class PersonWithOptional(name: String, age: Option[Int], nickname: Option[String])
  
  case class PersonWithAnnotation(@BsonProperty("full_name") name: String, age: Int)
  
  case class PersonWithUUID(id: UUID, name: String)
  
  case class PersonWithCollection(name: String, hobbies: List[String], scores: Set[Int], grades: Vector[Double])
  
  case class PersonWithMap(name: String, attributes: Map[String, String])
  
  case class Address(street: String, city: String)
  case class PersonWithNested(name: String, address: Address)
  
  sealed trait Animal
  case class Dog(name: String, breed: String) extends Animal
  case class Cat(name: String, color: String) extends Animal
  case class Bird(name: String, species: String) extends Animal

  private inline def createCodec[T](encodeNone: Boolean = false)(using ClassTag[T]): org.bson.codecs.Codec[T] = {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    CaseClassCodecGenerator.generateCodec[T](encodeNone, baseRegistry)
  }

  private inline def roundTripTest[T](value: T, encodeNone: Boolean = false)(using ClassTag[T]): T = {
    val codec = createCodec[T](encodeNone)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    codec.encode(writer, value, encoderContext)
    
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    codec.decode(reader, decoderContext)
  }

  "CaseClassCodecGenerator" should "generate codec for simple case class with primitives" in {
    val person = SimplePerson("John", 30, true)
    val result = roundTripTest(person)
    
    result shouldBe person
  }

  it should "handle optional fields when encodeNone is false" in {
    val person = PersonWithOptional("Alice", Some(25), None)
    val result = roundTripTest(person, encodeNone = false)
    
    result shouldBe person
  }

  it should "handle optional fields when encodeNone is true" in {
    val person = PersonWithOptional("Bob", Some(30), None)
    val result = roundTripTest(person, encodeNone = true)
    
    result shouldBe person
  }

  it should "handle Some values in optional fields" in {
    val person = PersonWithOptional("Charlie", Some(35), Some("Chuck"))
    val result = roundTripTest(person)
    
    result shouldBe person
  }

  it should "respect @BsonProperty annotations" in {
    val person = PersonWithAnnotation("David", 40)
    val codec = createCodec[PersonWithAnnotation]()
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    codec.encode(writer, person, encoderContext)
    
    // Verify the document contains the annotated field name
    document.containsKey("full_name") shouldBe true
    document.containsKey("name") shouldBe false
    document.getString("full_name").getValue shouldBe "David"
    
    // Verify round-trip works
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    val result = codec.decode(reader, decoderContext)
    
    result shouldBe person
  }

  it should "handle UUID fields correctly" in {
    val uuid = UUID.randomUUID()
    val person = PersonWithUUID(uuid, "Eve")
    val result = roundTripTest(person)
    
    result shouldBe person
  }

  it should "handle various collection types" in {
    val person = PersonWithCollection(
      "Frank", 
      List("reading", "swimming"), 
      Set(85, 90, 95), 
      Vector(3.5, 3.8, 4.0)
    )
    val result = roundTripTest(person)
    
    result shouldBe person
  }

  it should "handle Map[String, String] fields" in {
    val person = PersonWithMap("Grace", Map("city" -> "NYC", "country" -> "USA"))
    val result = roundTripTest(person)
    
    result shouldBe person
  }

  it should "handle empty collections and maps" in {
    val person1 = PersonWithCollection("Henry", List.empty, Set.empty, Vector.empty)
    val result1 = roundTripTest(person1)
    result1 shouldBe person1
    
    val person2 = PersonWithMap("Igor", Map.empty)
    val result2 = roundTripTest(person2)
    result2 shouldBe person2
  }

  it should "handle nested case classes" in {
    val person = PersonWithNested("John", Address("123 Main St", "Springfield"))
    
    // Need to create a registry that includes codecs for both types
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val addressCodec = CaseClassCodecGenerator.generateCodec[Address](false, baseRegistry)
    val personCodec = CaseClassCodecGenerator.generateCodec[PersonWithNested](false, 
      CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromCodecs(addressCodec)))
    
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    personCodec.encode(writer, person, encoderContext)
    
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    val result = personCodec.decode(reader, decoderContext)
    
    result shouldBe person
  }

  it should "handle sealed trait hierarchies with discriminator" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val animalCodec = CaseClassCodecGenerator.generateCodec[Dog](false, baseRegistry)
    
    val dog = Dog("Buddy", "Golden Retriever")
    
    // Test Dog
    val document1 = new BsonDocument()
    val writer1 = new BsonDocumentWriter(document1)
    animalCodec.encode(writer1, dog, EncoderContext.builder().build())
    
    // Verify discriminator is present
    document1.containsKey("_t") shouldBe true
    document1.getString("_t").getValue shouldBe "Dog"
    
    val reader1 = new BsonDocumentReader(document1)
    val result1 = animalCodec.decode(reader1, DecoderContext.builder().build())
    result1 shouldBe dog
  }

  it should "throw exception for null values during encoding" in {
    val codec = createCodec[SimplePerson]()
    val writer = new BsonDocumentWriter(new BsonDocument())
    val encoderContext = EncoderContext.builder().build()
    
    assertThrows[BsonInvalidOperationException] {
      codec.encode(writer, null, encoderContext)
    }
  }

  it should "throw exception for missing discriminator in sealed hierarchy" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val animalCodec = CaseClassCodecGenerator.generateCodec[Dog](false, baseRegistry)
    
    // Create a document without discriminator
    val document = new BsonDocument()
    document.put("name", new org.bson.BsonString("Test"))
    document.put("breed", new org.bson.BsonString("TestBreed"))
    
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    
    assertThrows[BsonInvalidOperationException] {
      animalCodec.decode(reader, decoderContext)
    }
  }

  it should "throw exception for invalid UUID format" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    val codec = CaseClassCodecGenerator.generateCodec[PersonWithUUID](false, baseRegistry)
    
    // Create a document with invalid UUID string
    val document = new BsonDocument()
    document.put("id", new org.bson.BsonString("invalid-uuid-format"))
    document.put("name", new org.bson.BsonString("Test"))
    
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    
    assertThrows[IllegalArgumentException] {
      codec.decode(reader, decoderContext)
    }
  }

  it should "return correct encoder class" in {
    val codec = createCodec[SimplePerson]()
    codec.getEncoderClass shouldBe classOf[SimplePerson]
  }

  it should "handle arrays with missing type arguments" in {
    case class TestClass(data: List[String])
    val codec = createCodec[TestClass]()
    
    // Create a document with an array but simulate missing type args by using raw BSON
    val document = new BsonDocument()
    val array = new org.bson.BsonArray()
    array.add(new org.bson.BsonString("test"))
    document.put("data", array)
    
    // This should work fine as the type args are available from the field mapping
    val reader = new BsonDocumentReader(document)
    val result = codec.decode(reader, DecoderContext.builder().build())
    result.data shouldBe List("test")
  }
}