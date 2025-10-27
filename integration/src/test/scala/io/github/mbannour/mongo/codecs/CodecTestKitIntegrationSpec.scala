package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import io.github.mbannour.mongo.codecs.models.*
import org.mongodb.scala.*
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.Codec
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonString, BsonInt32, BsonDouble, BsonBoolean, BsonArray, BsonObjectId}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import RegistryBuilder.{*, given}

class CodecTestKitIntegrationSpec extends AnyFlatSpec with ForAllTestContainer with Matchers with ScalaFutures with BeforeAndAfterAll:

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  override val container: MongoDBContainer = MongoDBContainer("mongo:6.0.19")

  private lazy val mongoUri: String =
    s"mongodb://${container.containerIpAddress}:${container.mappedPort(27017)}"

  // Test models
  case class SimpleUser(_id: ObjectId, name: String, age: Int)

  case class UserWithEmail(_id: ObjectId, name: String, email: Option[String])

  case class Product(_id: ObjectId, name: String, price: Double, inStock: Boolean)

  case class Order(_id: ObjectId, items: List[String], total: Double)

  case class Profile(_id: ObjectId, username: String, tags: Set[String])

  "CodecTestKit.roundTrip" should "encode and decode simple case classes correctly" in {
    assert(container.container.isRunning, "The MongoDB container is not running!")

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[SimpleUser]
      .build

    given codec: Codec[SimpleUser] = registry.get(classOf[SimpleUser])

    val user = SimpleUser(new ObjectId(), "Alice", 30)

    // Test round-trip
    val result = CodecTestKit.roundTrip(user)
    result shouldBe user
    result.name shouldBe "Alice"
    result.age shouldBe 30
  }

  "CodecTestKit.assertCodecSymmetry" should "verify codec symmetry for various types" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .registerAll[(SimpleUser,Product, Order,Profile)]
      .build

    given userCodec: Codec[SimpleUser] = registry.get(classOf[SimpleUser])
    given productCodec: Codec[Product] = registry.get(classOf[Product])
    given orderCodec: Codec[Order] = registry.get(classOf[Order])
    given profileCodec: Codec[Profile] = registry.get(classOf[Profile])

    // Test simple case class
    val user = SimpleUser(new ObjectId(), "Bob", 25)
    CodecTestKit.assertCodecSymmetry(user)

    // Test with Double and Boolean
    val product = Product(new ObjectId(), "Laptop", 999.99, true)
    CodecTestKit.assertCodecSymmetry(product)

    // Test with List
    val order = Order(new ObjectId(), List("item1", "item2", "item3"), 150.75)
    CodecTestKit.assertCodecSymmetry(order)

    // Test with Set
    val profile = Profile(new ObjectId(), "charlie", Set("scala", "mongodb", "testing"))
    CodecTestKit.assertCodecSymmetry(profile)
  }

  "CodecTestKit.toBsonDocument" should "convert case classes to BsonDocument correctly" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[SimpleUser]
      .build

    given codec: Codec[SimpleUser] = registry.get(classOf[SimpleUser])

    val user = SimpleUser(new ObjectId(), "Dave", 35)
    val bsonDoc = CodecTestKit.toBsonDocument(user)

    // Verify BSON structure
    bsonDoc.containsKey("_id") shouldBe true
    bsonDoc.getString("name").getValue shouldBe "Dave"
    bsonDoc.getInt32("age").getValue shouldBe 35

    // Verify we can parse it as JSON
    val jsonString = bsonDoc.toJson()
    jsonString should include("Dave")
    jsonString should include("35")
  }

  "CodecTestKit.fromBsonDocument" should "decode BsonDocument to case class correctly" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[SimpleUser]
      .build

    given codec: Codec[SimpleUser] = registry.get(classOf[SimpleUser])

    val objectId = new ObjectId()
    val bsonDoc = new BsonDocument()
      .append("_id", new BsonObjectId(objectId))
      .append("name", new BsonString("Eve"))
      .append("age", new BsonInt32(28))

    val user = CodecTestKit.fromBsonDocument[SimpleUser](bsonDoc)

    user._id shouldBe objectId
    user.name shouldBe "Eve"
    user.age shouldBe 28
  }

  "CodecTestKit.assertBsonStructure" should "validate BSON structure matches expectations" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[Product]
      .build

    given codec: Codec[Product] = registry.get(classOf[Product])

    val product = Product(new ObjectId(), "Keyboard", 59.99, true)

    val expectedBson = new BsonDocument()
      .append("_id", new BsonObjectId(product._id))
      .append("name", new BsonString("Keyboard"))
      .append("price", new BsonDouble(59.99))
      .append("inStock", new BsonBoolean(true))

    CodecTestKit.assertBsonStructure(product, expectedBson)
  }

  "CodecTestKit with NoneHandling.Encode" should "encode None as null in BSON" in {
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Encode)

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[UserWithEmail]
      .build

    given codec: Codec[UserWithEmail] = registry.get(classOf[UserWithEmail])

    val userWithoutEmail = UserWithEmail(new ObjectId(), "Frank", None)
    val bson = CodecTestKit.toBsonDocument(userWithoutEmail)

    // With NoneHandling.Encode, the field should be present as null
    bson.containsKey("email") shouldBe true
    bson.get("email").isNull shouldBe true

    // Verify symmetry still works
    CodecTestKit.assertCodecSymmetry(userWithoutEmail)
  }

  "CodecTestKit with NoneHandling.Ignore" should "omit None fields from BSON" in {
    given config: CodecConfig = CodecConfig(noneHandling = NoneHandling.Ignore)

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(CodecConfig(noneHandling = NoneHandling.Ignore))
      .register[UserWithEmail]
      .build

    given codec: Codec[UserWithEmail] = registry.get(classOf[UserWithEmail])

    val userWithoutEmail = UserWithEmail(new ObjectId(), "Grace", None)
    val bson = CodecTestKit.toBsonDocument(userWithoutEmail)

    // With NoneHandling.Ignore, the field should be omitted from BSON encoding
    bson.containsKey("email") shouldBe false

    // Test with Some value
    val userWithEmail = UserWithEmail(new ObjectId(), "Helen", Some("helen@example.com"))
    val bsonWithEmail = CodecTestKit.toBsonDocument(userWithEmail)

    bsonWithEmail.containsKey("email") shouldBe true
    bsonWithEmail.getString("email").getValue shouldBe "helen@example.com"

    val roundTripped = CodecTestKit.roundTrip(userWithoutEmail)
    roundTripped shouldBe userWithoutEmail

    CodecTestKit.assertCodecSymmetry(userWithEmail)
  }

  "CodecTestKit with collections" should "handle Lists, Sets, and complex structures" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[Order]
      .register[Profile]
      .build

    given orderCodec: Codec[Order] = registry.get(classOf[Order])
    given profileCodec: Codec[Profile] = registry.get(classOf[Profile])

    // Test with List
    val order = Order(new ObjectId(), List("apple", "banana", "cherry"), 25.50)
    val orderBson = CodecTestKit.toBsonDocument(order)

    orderBson.getArray("items").size() shouldBe 3
    orderBson.getDouble("total").getValue shouldBe 25.50
    CodecTestKit.assertCodecSymmetry(order)

    // Test with Set
    val profile = Profile(new ObjectId(), "dev123", Set("java", "scala", "kotlin"))
    val profileBson = CodecTestKit.toBsonDocument(profile)

    profileBson.getArray("tags").size() shouldBe 3
    CodecTestKit.assertCodecSymmetry(profile)

    // Test with empty collections
    val emptyOrder = Order(new ObjectId(), List.empty, 0.0)
    CodecTestKit.assertCodecSymmetry(emptyOrder)
  }

  "CodecTestKit.testRegistry" should "create minimal registry for isolated testing" in {
    given config: CodecConfig = CodecConfig()

    val fullRegistry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[SimpleUser]
      .register[Product]
      .build

    val userCodec = fullRegistry.get(classOf[SimpleUser])
    val productCodec = fullRegistry.get(classOf[Product])

    // Create minimal test registry
    val testReg = CodecTestKit.testRegistry(userCodec, productCodec)

    // Verify codecs are available
    testReg.get(classOf[SimpleUser]) should not be null
    testReg.get(classOf[Product]) should not be null
  }

  "CodecTestKit with MongoDB integration" should "verify codecs work with real database operations" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[SimpleUser]
      .build

    given codec: Codec[SimpleUser] = registry.get(classOf[SimpleUser])

    val database = MongoClient(mongoUri)
      .getDatabase("test_codec_kit")
      .withCodecRegistry(registry)

    val collection: MongoCollection[SimpleUser] = database.getCollection("users")

    val user = SimpleUser(new ObjectId(), "Integration Test User", 42)

    // First verify with CodecTestKit
    CodecTestKit.assertCodecSymmetry(user)

    // Then verify with real MongoDB
    collection.insertOne(user).toFuture().futureValue
    val retrieved = collection.find().first().toFuture().futureValue

    retrieved shouldBe user

    // Verify the BSON structure from MongoDB matches CodecTestKit
    val testKitBson = CodecTestKit.toBsonDocument(user)
    testKitBson.getString("name").getValue shouldBe retrieved.name
    testKitBson.getInt32("age").getValue shouldBe retrieved.age

    database.drop().toFuture().futureValue
  }

  "CodecTestKit edge cases" should "handle empty strings, boundary values, and special characters" in {
    given config: CodecConfig = CodecConfig()

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[SimpleUser]
      .build

    given codec: Codec[SimpleUser] = registry.get(classOf[SimpleUser])

    // Test empty string
    val userWithEmptyName = SimpleUser(new ObjectId(), "", 0)
    CodecTestKit.assertCodecSymmetry(userWithEmptyName)

    // Test special characters
    val userWithSpecialChars = SimpleUser(new ObjectId(), "User-123_@!#$%^&*()", Int.MaxValue)
    CodecTestKit.assertCodecSymmetry(userWithSpecialChars)

    // Test Unicode
    val userWithUnicode = SimpleUser(new ObjectId(), "用户名", 25)
    CodecTestKit.assertCodecSymmetry(userWithUnicode)

    // Test boundary values
    val userWithMinAge = SimpleUser(new ObjectId(), "Min Age", Int.MinValue)
    CodecTestKit.assertCodecSymmetry(userWithMinAge)
  }

  "CodecTestKit with nested case classes" should "handle complex nested structures from integration models" in {
    given config: CodecConfig = CodecConfig()

    case class Department(_id: ObjectId, name: String, budget: Double)
    case class Employee(_id: ObjectId, name: String, department: Option[Department])
    case class Company(_id: ObjectId, name: String, employees: List[Employee])

    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .register[Department]
      .register[Employee]
      .register[Company]
      .build

    given deptCodec: Codec[Department] = registry.get(classOf[Department])
    given empCodec: Codec[Employee] = registry.get(classOf[Employee])
    given companyCodec: Codec[Company] = registry.get(classOf[Company])

    val dept = Department(new ObjectId(), "Engineering", 1000000.0)
    val emp1 = Employee(new ObjectId(), "Alice", Some(dept))
    val emp2 = Employee(new ObjectId(), "Bob", None)
    val company = Company(new ObjectId(), "TechCorp", List(emp1, emp2))

    // Test each level
    CodecTestKit.assertCodecSymmetry(dept)
    CodecTestKit.assertCodecSymmetry(emp1)
    CodecTestKit.assertCodecSymmetry(emp2)
    CodecTestKit.assertCodecSymmetry(company)

    // Verify BSON structure of nested object
    val companyBson = CodecTestKit.toBsonDocument(company)
    companyBson.getString("name").getValue shouldBe "TechCorp"
    companyBson.getArray("employees").size() shouldBe 2
  }
end CodecTestKitIntegrationSpec
