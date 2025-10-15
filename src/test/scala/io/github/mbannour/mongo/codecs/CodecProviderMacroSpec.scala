package io.github.mbannour.mongo.codecs

import org.bson.codecs.{BsonValueCodecProvider, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.{BsonDocument, BsonDocumentWriter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.mongodb.scala.bson.annotations.BsonProperty

import scala.reflect.ClassTag

class CodecProviderMacroSpec extends AnyFlatSpec with Matchers:

  case class SimplePerson(name: String, age: Int)

  case class PersonWithOptional(name: String, age: Option[Int], nickname: Option[String])

  case class PersonWithAnnotation(@BsonProperty("full_name") name: String, age: Int)

  private def createBaseRegistry(): CodecRegistry =
    CodecRegistries.fromProviders(new BsonValueCodecProvider())

  "CodecProviderMacro.createCodecProviderIgnoreNone" should "omit None fields from the BSON document" in {
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

  "CodecProviderMacro.createCodecProviderEncodeNone" should "include None fields as null in the BSON document" in {
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
end CodecProviderMacroSpec
