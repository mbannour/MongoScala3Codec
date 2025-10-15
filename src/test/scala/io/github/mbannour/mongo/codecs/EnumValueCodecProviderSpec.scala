package io.github.mbannour.mongo.codecs

import org.bson.codecs.{BsonValueCodecProvider}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EnumValueCodecProviderSpec extends AnyFlatSpec with Matchers:

  // Define test enums
  enum Priority derives CanEqual:
    case Low, Medium, High

    def toInt: Int = this match
      case Low    => 1
      case Medium => 2
      case High   => 3

  object Priority:
    def fromInt(value: Int): Priority = value match
      case 1 => Low
      case 2 => Medium
      case 3 => High
      case _ => throw new IllegalArgumentException(s"Invalid priority value: $value")

  enum Status derives CanEqual:
    case Active, Inactive, Pending

    def toStringValue: String = this match
      case Active   => "ACTIVE"
      case Inactive => "INACTIVE"
      case Pending  => "PENDING"

  object Status:
    def fromStringValue(value: String): Status = value match
      case "ACTIVE"   => Active
      case "INACTIVE" => Inactive
      case "PENDING"  => Pending
      case _          => throw new IllegalArgumentException(s"Invalid status value: $value")

  enum Flag derives CanEqual:
    case Enabled, Disabled

    def toBoolean: Boolean = this match
      case Enabled  => true
      case Disabled => false

  object Flag:
    def fromBoolean(value: Boolean): Flag = value match
      case true  => Enabled
      case false => Disabled

  private def createBaseRegistry(): CodecRegistry =
    CodecRegistries.fromProviders(new BsonValueCodecProvider())

  "EnumValueCodecProvider" should "create provider with correct type handling" in {
    val baseRegistry = createBaseRegistry()
    // Create a simple mock codec for testing
    given org.bson.codecs.Codec[Int] = new org.bson.codecs.Codec[Int]:
      def encode(writer: org.bson.BsonWriter, value: Int, encoderContext: org.bson.codecs.EncoderContext): Unit =
        writer.writeInt32(value)
      def decode(reader: org.bson.BsonReader, decoderContext: org.bson.codecs.DecoderContext): Int =
        reader.readInt32()
      def getEncoderClass: Class[Int] = classOf[Int]

    val priorityProvider = EnumValueCodecProvider[Priority, Int](_.toInt, Priority.fromInt)

    // Test provider creation - it should not be null
    priorityProvider should not be (null)

    // Test that it returns null for non-matching types (proper provider behavior)
    val codec = priorityProvider.get(classOf[String], baseRegistry)
    codec shouldBe null
  }

  it should "work with simple enum transformation functions" in {
    // Test the transformation functions work correctly
    Priority.High.toInt shouldBe 3
    Priority.fromInt(3) shouldBe Priority.High

    Status.Active.toStringValue shouldBe "ACTIVE"
    Status.fromStringValue("ACTIVE") shouldBe Status.Active

    Flag.Enabled.toBoolean shouldBe true
    Flag.fromBoolean(true) shouldBe Flag.Enabled
  }

  it should "handle conversion errors gracefully" in {
    // Test error handling in transformation functions
    assertThrows[IllegalArgumentException] {
      Priority.fromInt(999) // Invalid priority value
    }

    assertThrows[IllegalArgumentException] {
      Status.fromStringValue("INVALID") // Invalid status value
    }
  }

  it should "test enum transformation functions work correctly" in {
    // Test the transformation functions work correctly
    Priority.High.toInt `shouldBe` 3
    Priority.fromInt(3) `shouldBe` Priority.High

    Status.Active.toStringValue `shouldBe` "ACTIVE"
    Status.fromStringValue("ACTIVE") `shouldBe` Status.Active

    Flag.Enabled.toBoolean `shouldBe` true
    Flag.fromBoolean(true) `shouldBe` Flag.Enabled
  }
end EnumValueCodecProviderSpec
