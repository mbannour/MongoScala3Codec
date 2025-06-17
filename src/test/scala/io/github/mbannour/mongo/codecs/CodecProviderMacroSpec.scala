package io.github.mbannour.mongo.codecs

import io.github.mbannour.mongo.codecs.CodecProviderMacro
import org.bson.codecs.{BsonDocumentCodec, BsonValueCodecProvider, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.mongodb.scala.bson.annotations.BsonProperty

import scala.reflect.ClassTag

class CodecProviderMacroSpec extends AnyFlatSpec with Matchers {

  case class SimplePerson(name: String, age: Int)
  
  case class PersonWithOptional(name: String, age: Option[Int], nickname: Option[String])
  
  case class PersonWithAnnotation(@BsonProperty("full_name") name: String, age: Int)
  
  sealed trait Animal
  case class Dog(name: String, breed: String) extends Animal
  case class Cat(name: String, color: String) extends Animal

  case class Address(street: String, city: String)
  case class PersonWithAddress(name: String, address: Address)

  private def createBaseRegistry(): CodecRegistry = {
    CodecRegistries.fromProviders(new BsonValueCodecProvider())
  }

  private def roundTripTest[T](value: T, provider: CodecProvider, baseRegistry: CodecRegistry)(using ClassTag[T]): T = {
    val registry = CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromProviders(provider))
    val codec = registry.get(summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
    
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    codec.encode(writer, value, encoderContext)
    
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    codec.decode(reader, decoderContext)
  }

  "CodecProviderMacro.createCodecProviderIgnoreNone" should "create a working codec provider that ignores None values" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[PersonWithOptional]
    
    val person = PersonWithOptional("Alice", Some(25), None)
    val result = roundTripTest(person, provider, createBaseRegistry())
    
    result shouldBe person
  }

  it should "omit None fields from the BSON document" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[PersonWithOptional]
    val registry = CodecRegistries.fromRegistries(createBaseRegistry(), CodecRegistries.fromProviders(provider))
    val codec = registry.get(classOf[PersonWithOptional])
    
    val person = PersonWithOptional("Bob", Some(30), None)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    codec.encode(writer, person, encoderContext)
    
    document.getString("name").getValue shouldBe "Bob"
    document.getInt32("age").getValue shouldBe 30
    document.containsKey("nickname") shouldBe false
  }

  "CodecProviderMacro.createCodecProviderEncodeNone" should "create a working codec provider that encodes None as null" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderEncodeNone[PersonWithOptional]
    
    val person = PersonWithOptional("Charlie", Some(35), None)
    val result = roundTripTest(person, provider, createBaseRegistry())
    
    result shouldBe person
  }

  it should "include None fields as null in the BSON document" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderEncodeNone[PersonWithOptional]
    val registry = CodecRegistries.fromRegistries(createBaseRegistry(), CodecRegistries.fromProviders(provider))
    val codec = registry.get(classOf[PersonWithOptional])
    
    val person = PersonWithOptional("David", Some(40), None)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    codec.encode(writer, person, encoderContext)
    
    document.getString("name").getValue shouldBe "David"
    document.getInt32("age").getValue shouldBe 40
    document.containsKey("nickname") shouldBe true
    document.isNull("nickname") shouldBe true
  }

  it should "work with simple case classes without optional fields" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[SimplePerson]
    
    val person = SimplePerson("Eve", 25)
    val result = roundTripTest(person, provider, createBaseRegistry())
    
    result shouldBe person
  }

  it should "handle @BsonProperty annotations" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[PersonWithAnnotation]
    val registry = CodecRegistries.fromRegistries(createBaseRegistry(), CodecRegistries.fromProviders(provider))
    val codec = registry.get(classOf[PersonWithAnnotation])
    
    val person = PersonWithAnnotation("Frank", 45)
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    codec.encode(writer, person, encoderContext)
    
    document.containsKey("full_name") shouldBe true
    document.containsKey("name") shouldBe false
    document.getString("full_name").getValue shouldBe "Frank"
    document.getInt32("age").getValue shouldBe 45
    
    // Test round-trip
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    val result = codec.decode(reader, decoderContext)
    
    result shouldBe person
  }

  it should "work with sealed trait hierarchies" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[Dog]
    
    val dog = Dog("Buddy", "Golden Retriever")
    
    val dogResult = roundTripTest(dog, provider, createBaseRegistry())
    
    dogResult shouldBe dog
  }

  it should "include discriminator for sealed hierarchies" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[Dog]
    val registry = CodecRegistries.fromRegistries(createBaseRegistry(), CodecRegistries.fromProviders(provider))
    val codec = registry.get(classOf[Dog])
    
    val dog = Dog("Rex", "German Shepherd")
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    codec.encode(writer, dog, encoderContext)
    
    document.containsKey("_t") shouldBe true
    document.getString("_t").getValue shouldBe "Dog"
    document.getString("name").getValue shouldBe "Rex"
    document.getString("breed").getValue shouldBe "German Shepherd"
  }

  it should "return null for non-matching class types" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[SimplePerson]
    
    val codec = provider.get(classOf[String], createBaseRegistry())
    codec shouldBe null
  }

  it should "return correct codec for matching class types" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[SimplePerson]
    
    val codec = provider.get(classOf[SimplePerson], createBaseRegistry())
    codec should not be null
    codec.getEncoderClass shouldBe classOf[SimplePerson]
  }

  it should "handle inheritance with isAssignableFrom" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[Dog]
    
    // Should work for the dog class
    val dogCodec = provider.get(classOf[Dog], createBaseRegistry())
    dogCodec should not be null
  }

  it should "work with nested case classes when properly registered" in {
    given CodecRegistry = createBaseRegistry()
    val addressProvider = CodecProviderMacro.createCodecProviderIgnoreNone[Address]
    val personProvider = CodecProviderMacro.createCodecProviderIgnoreNone[PersonWithAddress]
    
    val baseRegistry = createBaseRegistry()
    val registry = CodecRegistries.fromRegistries(
      baseRegistry,
      CodecRegistries.fromProviders(addressProvider, personProvider)
    )
    
    val codec = registry.get(classOf[PersonWithAddress])
    codec should not be null
    
    val person = PersonWithAddress("Grace", Address("123 Oak St", "Portland"))
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    codec.encode(writer, person, encoderContext)
    
    document.getString("name").getValue shouldBe "Grace"
    val addressDoc = document.getDocument("address")
    addressDoc.getString("street").getValue shouldBe "123 Oak St"
    addressDoc.getString("city").getValue shouldBe "Portland"
    
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    val result = codec.decode(reader, decoderContext)
    
    result shouldBe person
  }

  it should "handle Some values in optional fields correctly" in {
    given CodecRegistry = createBaseRegistry()
    val provider = CodecProviderMacro.createCodecProviderIgnoreNone[PersonWithOptional]
    
    val person = PersonWithOptional("Henry", Some(50), Some("Hank"))
    val result = roundTripTest(person, provider, createBaseRegistry())
    
    result shouldBe person
  }

  it should "handle mixed Some and None values" in {
    given CodecRegistry = createBaseRegistry()
    val ignoreProvider = CodecProviderMacro.createCodecProviderIgnoreNone[PersonWithOptional]
    val encodeProvider = CodecProviderMacro.createCodecProviderEncodeNone[PersonWithOptional]
    
    val person = PersonWithOptional("Igor", None, Some("Iggy"))
    
    val ignoreResult = roundTripTest(person, ignoreProvider, createBaseRegistry())
    val encodeResult = roundTripTest(person, encodeProvider, createBaseRegistry())
    
    ignoreResult shouldBe person
    encodeResult shouldBe person
  }

  it should "work with multiple providers in the same registry" in {
    given CodecRegistry = createBaseRegistry()
    val simpleProvider = CodecProviderMacro.createCodecProviderIgnoreNone[SimplePerson]
    val optionalProvider = CodecProviderMacro.createCodecProviderEncodeNone[PersonWithOptional]
    
    val baseRegistry = createBaseRegistry()
    val registry = CodecRegistries.fromRegistries(
      baseRegistry,
      CodecRegistries.fromProviders(simpleProvider, optionalProvider)
    )
    
    val simpleCodec = registry.get(classOf[SimplePerson])
    val optionalCodec = registry.get(classOf[PersonWithOptional])
    
    simpleCodec should not be null
    optionalCodec should not be null
    
    simpleCodec.getEncoderClass shouldBe classOf[SimplePerson]
    optionalCodec.getEncoderClass shouldBe classOf[PersonWithOptional]
  }
}