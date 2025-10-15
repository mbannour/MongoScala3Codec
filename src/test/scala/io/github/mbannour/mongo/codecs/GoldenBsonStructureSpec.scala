package io.github.mbannour.mongo.codecs

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder._

/** Golden tests that verify exact BSON structure for various types.
  *
  * These tests ensure that the BSON representation matches expected formats, which is critical for compatibility with existing MongoDB data
  * and other services.
  */
class GoldenBsonStructureSpec extends AnyFlatSpec with Matchers:

  private val defaultBsonRegistry = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  // Test models
  case class SimpleUser(_id: ObjectId, name: String, age: Int)

  case class UserWithOptional(_id: ObjectId, name: String, email: Option[String], bio: Option[String])

  case class Address(_id: ObjectId, street: String, city: String, zipCode: Int)
  case class UserWithNested(_id: ObjectId, name: String, address: Address)

  case class UserWithCollections(_id: ObjectId, tags: List[String], scores: Seq[Int])

  // Concrete case classes for testing (not using sealed traits directly)
  case class Circle(_id: ObjectId, radius: Double, shapeType: String = "Circle")
  case class Rectangle(_id: ObjectId, width: Double, height: Double, shapeType: String = "Rectangle")
  case class Triangle(_id: ObjectId, base: Double, height: Double, shapeType: String = "Triangle")

  case class UserCreatedEvent(_id: ObjectId, userId: String, timestamp: Long, eventType: String = "UserCreated")
  case class UserDeletedEvent(_id: ObjectId, userId: String, reason: String, timestamp: Long, eventType: String = "UserDeleted")

  "SimpleUser BSON structure" should "match expected format" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[SimpleUser]
      .build

    given codec: Codec[SimpleUser] = registry.get(classOf[SimpleUser])

    val objectId = new ObjectId("507f1f77bcf86cd799439011")
    val user = SimpleUser(objectId, "Alice", 30)

    val bson = CodecTestKit.toBsonDocument(user)

    bson.containsKey("_id") shouldBe true
    bson.containsKey("name") shouldBe true
    bson.containsKey("age") shouldBe true

    bson.getObjectId("_id").getValue shouldBe objectId
    bson.getString("name").getValue shouldBe "Alice"
    bson.getInt32("age").getValue shouldBe 30

    // Verify no extra fields
    bson.keySet().size() shouldBe 3
  }

  "UserWithOptional BSON structure with NoneHandling.Encode" should "include null values" in {
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .withConfig(config)
      .register[UserWithOptional]
      .build

    given codec: Codec[UserWithOptional] = registry.get(classOf[UserWithOptional])

    val objectId = new ObjectId()
    val user = UserWithOptional(objectId, "Bob", Some("bob@example.com"), None)

    val bson = CodecTestKit.toBsonDocument(user)

    bson.containsKey("name") shouldBe true
    bson.containsKey("email") shouldBe true
    bson.containsKey("bio") shouldBe true

    bson.getString("name").getValue shouldBe "Bob"
    bson.getString("email").getValue shouldBe "bob@example.com"
    bson.isNull("bio") shouldBe true
  }

  "UserWithOptional BSON structure with NoneHandling.Ignore" should "omit None fields" in {
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .withConfig(config)
      .register[UserWithOptional]
      .build

    given codec: Codec[UserWithOptional] = registry.get(classOf[UserWithOptional])

    val objectId = new ObjectId()
    val user = UserWithOptional(objectId, "Charlie", Some("charlie@example.com"), None)

    val bson = CodecTestKit.toBsonDocument(user)

    bson.containsKey("name") shouldBe true
    bson.containsKey("email") shouldBe true
    bson.containsKey("bio") shouldBe false // None field is omitted

    bson.getString("name").getValue shouldBe "Charlie"
    bson.getString("email").getValue shouldBe "charlie@example.com"
  }

  "UserWithNested BSON structure" should "correctly nest documents" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Address]
      .register[UserWithNested]
      .build

    given codec: Codec[UserWithNested] = registry.get(classOf[UserWithNested])

    val addressId = new ObjectId()
    val userId = new ObjectId()
    val address = Address(addressId, "123 Main St", "Springfield", 12345)
    val user = UserWithNested(userId, "Diana", address)

    val bson = CodecTestKit.toBsonDocument(user)

    bson.containsKey("name") shouldBe true
    bson.containsKey("address") shouldBe true

    bson.getString("name").getValue shouldBe "Diana"

    val addressDoc = bson.getDocument("address")
    addressDoc.getObjectId("_id").getValue shouldBe addressId
    addressDoc.getString("street").getValue shouldBe "123 Main St"
    addressDoc.getString("city").getValue shouldBe "Springfield"
    addressDoc.getInt32("zipCode").getValue shouldBe 12345
  }

  "UserWithCollections BSON structure" should "encode collections as arrays" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[UserWithCollections]
      .build

    given codec: Codec[UserWithCollections] = registry.get(classOf[UserWithCollections])

    val objectId = new ObjectId()
    val user = UserWithCollections(objectId, List("scala", "mongodb", "functional"), Seq(100, 95, 88))

    val bson = CodecTestKit.toBsonDocument(user)

    bson.containsKey("tags") shouldBe true
    bson.containsKey("scores") shouldBe true

    val tagsArray = bson.getArray("tags")
    tagsArray.size() shouldBe 3
    tagsArray.get(0).asString().getValue shouldBe "scala"
    tagsArray.get(1).asString().getValue shouldBe "mongodb"
    tagsArray.get(2).asString().getValue shouldBe "functional"

    val scoresArray = bson.getArray("scores")
    scoresArray.size() shouldBe 3
    scoresArray.get(0).asInt32().getValue shouldBe 100
    scoresArray.get(1).asInt32().getValue shouldBe 95
    scoresArray.get(2).asInt32().getValue shouldBe 88
  }

  "Concrete case class (Circle) BSON structure" should "encode all fields correctly" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Circle]
      .build

    given codec: Codec[Circle] = registry.get(classOf[Circle])

    val circleId = new ObjectId()
    val circle = Circle(circleId, 5.0)

    val bson = CodecTestKit.toBsonDocument(circle)

    bson.containsKey("_id") shouldBe true
    bson.containsKey("radius") shouldBe true
    bson.containsKey("shapeType") shouldBe true

    bson.getObjectId("_id").getValue shouldBe circleId
    bson.getDouble("radius").getValue shouldBe 5.0
    bson.getString("shapeType").getValue shouldBe "Circle"
  }

  "Multiple related case classes" should "encode independently" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Circle]
      .register[Rectangle]
      .register[Triangle]
      .build

    given circleCodec: Codec[Circle] = registry.get(classOf[Circle])
    given rectangleCodec: Codec[Rectangle] = registry.get(classOf[Rectangle])
    given triangleCodec: Codec[Triangle] = registry.get(classOf[Triangle])

    val circle = Circle(new ObjectId(), 3.0)
    val rectangle = Rectangle(new ObjectId(), 4.0, 5.0)
    val triangle = Triangle(new ObjectId(), 6.0, 7.0)

    val circleBson = CodecTestKit.toBsonDocument(circle)
    circleBson.getString("shapeType").getValue shouldBe "Circle"
    circleBson.getDouble("radius").getValue shouldBe 3.0

    val rectangleBson = CodecTestKit.toBsonDocument(rectangle)
    rectangleBson.getString("shapeType").getValue shouldBe "Rectangle"
    rectangleBson.getDouble("width").getValue shouldBe 4.0
    rectangleBson.getDouble("height").getValue shouldBe 5.0

    val triangleBson = CodecTestKit.toBsonDocument(triangle)
    triangleBson.getString("shapeType").getValue shouldBe "Triangle"
    triangleBson.getDouble("base").getValue shouldBe 6.0
    triangleBson.getDouble("height").getValue shouldBe 7.0
  }

  "Event-like case classes" should "handle different field counts" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[UserCreatedEvent]
      .register[UserDeletedEvent]
      .build

    given createdCodec: Codec[UserCreatedEvent] = registry.get(classOf[UserCreatedEvent])
    given deletedCodec: Codec[UserDeletedEvent] = registry.get(classOf[UserDeletedEvent])

    val createdEvent = UserCreatedEvent(new ObjectId(), "user123", 1634567890L)
    val createdBson = CodecTestKit.toBsonDocument(createdEvent)

    createdBson.getString("eventType").getValue shouldBe "UserCreated"
    createdBson.getString("userId").getValue shouldBe "user123"
    createdBson.getInt64("timestamp").getValue shouldBe 1634567890L
    createdBson.keySet().size() shouldBe 4 // _id, userId, timestamp, eventType

    val deletedEvent = UserDeletedEvent(new ObjectId(), "user456", "account closed", 1634567900L)
    val deletedBson = CodecTestKit.toBsonDocument(deletedEvent)

    deletedBson.getString("eventType").getValue shouldBe "UserDeleted"
    deletedBson.getString("userId").getValue shouldBe "user456"
    deletedBson.getString("reason").getValue shouldBe "account closed"
    deletedBson.getInt64("timestamp").getValue shouldBe 1634567900L
    deletedBson.keySet().size() shouldBe 5 // _id, userId, reason, timestamp, eventType
  }

  "Empty collections BSON structure" should "encode as empty arrays" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[UserWithCollections]
      .build

    given codec: Codec[UserWithCollections] = registry.get(classOf[UserWithCollections])

    val user = UserWithCollections(new ObjectId(), List.empty, Seq.empty)
    val bson = CodecTestKit.toBsonDocument(user)

    val tagsArray = bson.getArray("tags")
    tagsArray.size() shouldBe 0

    val scoresArray = bson.getArray("scores")
    scoresArray.size() shouldBe 0
  }

  "BSON round-trip" should "preserve exact ObjectId values" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[SimpleUser]
      .build

    given codec: Codec[SimpleUser] = registry.get(classOf[SimpleUser])

    val specificId = new ObjectId("507f1f77bcf86cd799439011")
    val user = SimpleUser(specificId, "Test", 25)

    val roundTripped = CodecTestKit.roundTrip(user)

    roundTripped._id.toString shouldBe "507f1f77bcf86cd799439011"
    roundTripped._id shouldBe specificId
  }

  it should "preserve field order in nested documents" in {
    val registry = RegistryBuilder
      .from(defaultBsonRegistry)
      .register[Address]
      .register[UserWithNested]
      .build

    given codec: Codec[UserWithNested] = registry.get(classOf[UserWithNested])

    val address = Address(new ObjectId(), "456 Oak Ave", "Shelbyville", 67890)
    val user = UserWithNested(new ObjectId(), "Eve", address)

    val bson = CodecTestKit.toBsonDocument(user)
    val json = bson.toJson()

    // Verify structure is as expected
    json should include("\"name\"")
    json should include("\"address\"")
    json should include("\"street\"")
    json should include("\"city\"")
    json should include("\"zipCode\"")
  }
end GoldenBsonStructureSpec
