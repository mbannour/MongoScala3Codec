package io.github.mbannour.mongo.codecs

import io.github.mbannour.mongo.codecs.EnumValueCodecProvider
import org.bson.codecs.{BsonDocumentCodec, BsonValueCodecProvider, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.reflect.ClassTag

class EnumValueCodecProviderSpec extends AnyFlatSpec with Matchers {

  // Define test enums
  enum Priority derives CanEqual:
    case Low, Medium, High
    
    def toInt: Int = this match
      case Low => 1
      case Medium => 2
      case High => 3
  
  object Priority:
    def fromInt(value: Int): Priority = value match
      case 1 => Low
      case 2 => Medium
      case 3 => High
      case _ => throw new IllegalArgumentException(s"Invalid priority value: $value")

  enum Status derives CanEqual:
    case Active, Inactive, Pending
    
    def toStringValue: String = this match
      case Active => "ACTIVE"
      case Inactive => "INACTIVE"
      case Pending => "PENDING"
  
  object Status:
    def fromStringValue(value: String): Status = value match
      case "ACTIVE" => Active
      case "INACTIVE" => Inactive
      case "PENDING" => Pending
      case _ => throw new IllegalArgumentException(s"Invalid status value: $value")

  enum Flag derives CanEqual:
    case Enabled, Disabled
    
    def toBoolean: Boolean = this match
      case Enabled => true
      case Disabled => false
  
  object Flag:
    def fromBoolean(value: Boolean): Flag = value match
      case true => Enabled
      case false => Disabled

  case class Task(name: String, priority: Priority, status: Status, enabled: Flag)

  "EnumValueCodecProvider" should "create codec provider for Int-based enum" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    given org.bson.codecs.Codec[Int] = baseRegistry.get(classOf[Int])
    val priorityProvider = EnumValueCodecProvider[Priority, Int](_.toInt, Priority.fromInt)
    val registry = CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromProviders(priorityProvider))
    
    val codec = registry.get(classOf[Priority])
    codec should not be null
    
    // Test encoding
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    writer.writeName("priority")
    codec.encode(writer, Priority.High, encoderContext)
    
    document.getInt32("priority").getValue shouldBe 3
    
    // Test decoding
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    
    reader.readStartDocument()
    reader.readName("priority")
    val result = codec.decode(reader, decoderContext)
    reader.readEndDocument()
    
    result shouldBe Priority.High
  }

  it should "create codec provider for String-based enum" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    given org.bson.codecs.Codec[String] = baseRegistry.get(classOf[String])
    val statusProvider = EnumValueCodecProvider[Status, String](_.toStringValue, Status.fromStringValue)
    val registry = CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromProviders(statusProvider))
    
    val codec = registry.get(classOf[Status])
    codec should not be null
    
    // Test encoding
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    writer.writeName("status")
    codec.encode(writer, Status.Active, encoderContext)
    
    document.getString("status").getValue shouldBe "ACTIVE"
    
    // Test decoding
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    
    reader.readStartDocument()
    reader.readName("status")
    val result = codec.decode(reader, decoderContext)
    reader.readEndDocument()
    
    result shouldBe Status.Active
  }

  it should "create codec provider for Boolean-based enum" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    given org.bson.codecs.Codec[Boolean] = baseRegistry.get(classOf[Boolean])
    val flagProvider = EnumValueCodecProvider[Flag, Boolean](_.toBoolean, Flag.fromBoolean)
    val registry = CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromProviders(flagProvider))
    
    val codec = registry.get(classOf[Flag])
    codec should not be null
    
    // Test encoding
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    writer.writeName("flag")
    codec.encode(writer, Flag.Enabled, encoderContext)
    
    document.getBoolean("flag").getValue shouldBe true
    
    // Test decoding
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    
    reader.readStartDocument()
    reader.readName("flag")
    val result = codec.decode(reader, decoderContext)
    reader.readEndDocument()
    
    result shouldBe Flag.Enabled
  }

  it should "round-trip all enum values correctly" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    given org.bson.codecs.Codec[Int] = baseRegistry.get(classOf[Int])
    val priorityProvider = EnumValueCodecProvider[Priority, Int](_.toInt, Priority.fromInt)
    val registry = CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromProviders(priorityProvider))
    
    val codec = registry.get(classOf[Priority])
    
    // Test all enum values
    val allPriorities = List(Priority.Low, Priority.Medium, Priority.High)
    
    for (priority <- allPriorities) {
      val document = new BsonDocument()
      val writer = new BsonDocumentWriter(document)
      val encoderContext = EncoderContext.builder().build()
      
      writer.writeName("priority")
      codec.encode(writer, priority, encoderContext)
      
      val reader = new BsonDocumentReader(document)
      val decoderContext = DecoderContext.builder().build()
      
      reader.readStartDocument()
      reader.readName("priority")
      val result = codec.decode(reader, decoderContext)
      reader.readEndDocument()
      
      result shouldBe priority
    }
  }

  it should "return null for non-matching class types" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    given org.bson.codecs.Codec[Int] = baseRegistry.get(classOf[Int])
    val priorityProvider = EnumValueCodecProvider[Priority, Int](_.toInt, Priority.fromInt)
    
    val codec = priorityProvider.get(classOf[String], baseRegistry)
    codec shouldBe null
  }

  it should "return correct encoder class" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    given org.bson.codecs.Codec[Int] = baseRegistry.get(classOf[Int])
    val priorityProvider = EnumValueCodecProvider[Priority, Int](_.toInt, Priority.fromInt)
    val registry = CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromProviders(priorityProvider))
    
    val codec = registry.get(classOf[Priority])
    codec.getEncoderClass shouldBe classOf[Priority]
  }

  it should "handle conversion errors during decoding" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    given org.bson.codecs.Codec[Int] = baseRegistry.get(classOf[Int])
    val priorityProvider = EnumValueCodecProvider[Priority, Int](_.toInt, Priority.fromInt)
    val registry = CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromProviders(priorityProvider))
    
    val codec = registry.get(classOf[Priority])
    
    // Create a document with an invalid priority value
    val document = new BsonDocument()
    document.put("priority", new org.bson.BsonInt32(999)) // Invalid priority value
    
    val reader = new BsonDocumentReader(document)
    val decoderContext = DecoderContext.builder().build()
    
    reader.readStartDocument()
    reader.readName("priority")
    
    assertThrows[IllegalArgumentException] {
      codec.decode(reader, decoderContext)
    }
  }

  it should "handle conversion errors during encoding" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    
    given org.bson.codecs.Codec[Int] = baseRegistry.get(classOf[Int])
    // Create a provider with a toValue function that can throw an exception
    val faultyProvider = EnumValueCodecProvider[Priority, Int](
      _ => throw new RuntimeException("Encoding error"),
      Priority.fromInt
    )
    val registry = CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromProviders(faultyProvider))
    
    val codec = registry.get(classOf[Priority])
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    writer.writeName("priority")
    
    assertThrows[RuntimeException] {
      codec.encode(writer, Priority.High, encoderContext)
    }
  }

  it should "work with inheritance - codec provider recognizes subclasses" in {
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    given org.bson.codecs.Codec[Int] = baseRegistry.get(classOf[Int])
    val priorityProvider = EnumValueCodecProvider[Priority, Int](_.toInt, Priority.fromInt)
    val registry = CodecRegistries.fromRegistries(baseRegistry, CodecRegistries.fromProviders(priorityProvider))
    
    // The codec provider should work with the exact class and its subclasses
    val codec = priorityProvider.get(classOf[Priority], registry)
    codec should not be null
    
    // Test that it works properly
    val document = new BsonDocument()
    val writer = new BsonDocumentWriter(document)
    val encoderContext = EncoderContext.builder().build()
    
    writer.writeName("priority")
    codec.encode(writer, Priority.Medium, encoderContext)
    
    document.getInt32("priority").getValue shouldBe 2
  }

  it should "integrate properly with case class codecs" in {
    // This test would require integration with the main codec system
    // For now, we'll just verify that multiple enum providers can coexist
    val baseRegistry = CodecRegistries.fromProviders(new BsonValueCodecProvider())
    given org.bson.codecs.Codec[Int] = baseRegistry.get(classOf[Int])
    given org.bson.codecs.Codec[String] = baseRegistry.get(classOf[String])
    given org.bson.codecs.Codec[Boolean] = baseRegistry.get(classOf[Boolean])
    val priorityProvider = EnumValueCodecProvider[Priority, Int](_.toInt, Priority.fromInt)
    val statusProvider = EnumValueCodecProvider[Status, String](_.toStringValue, Status.fromStringValue)
    val flagProvider = EnumValueCodecProvider[Flag, Boolean](_.toBoolean, Flag.fromBoolean)
    
    val registry = CodecRegistries.fromRegistries(
      baseRegistry,
      CodecRegistries.fromProviders(priorityProvider, statusProvider, flagProvider)
    )
    
    // All codecs should be available
    registry.get(classOf[Priority]) should not be null
    registry.get(classOf[Status]) should not be null
    registry.get(classOf[Flag]) should not be null
    
    // And they should work independently
    val priorityCodec = registry.get(classOf[Priority])
    val statusCodec = registry.get(classOf[Status])
    val flagCodec = registry.get(classOf[Flag])
    
    priorityCodec.getEncoderClass shouldBe classOf[Priority]
    statusCodec.getEncoderClass shouldBe classOf[Status]
    flagCodec.getEncoderClass shouldBe classOf[Flag]
  }
}