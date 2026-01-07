package io.github.mbannour.mongo.codecs

import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonInvalidOperationException}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.RegistryBuilder.*

/** Negative test cases to ensure proper error handling and validation.
  *
  * These tests verify that the library:
  *   - Handles invalid data gracefully
  *   - Provides clear error messages
  *   - Fails fast on incorrect usage
  *   - Doesn't crash with unexpected inputs
  */
class NegativeTestSpec extends AnyFlatSpec with Matchers:

  // Use the default MongoDB codec registry which includes ObjectId codec
  val defaultBsonRegistry = CodecRegistries.fromRegistries(
    CodecRegistries.fromProviders(new org.bson.codecs.BsonValueCodecProvider()),
    CodecRegistries.fromProviders(new org.bson.codecs.ValueCodecProvider()),
    CodecRegistries.fromProviders(new org.bson.codecs.DocumentCodecProvider()),
    CodecRegistries.fromProviders(new org.bson.codecs.IterableCodecProvider()),
    CodecRegistries.fromProviders(new org.bson.codecs.MapCodecProvider())
  )

  // Test models - must be defined at class level for macro expansion
  case class User(_id: ObjectId, name: String, age: Int)
  case class UserWithOption(_id: ObjectId, name: String, email: Option[String])
  case class Address(street: String, city: String, zipCode: Int)
  case class Person(_id: ObjectId, name: String, address: Address)
  case class Boundaries(
      _id: ObjectId,
      maxInt: Int,
      minInt: Int,
      maxLong: Long,
      minLong: Long
  )
  case class WithCollections(
      _id: ObjectId,
      tags: List[String],
      scores: Vector[Int]
  )

  sealed trait Status
  case class Active() extends Status
  case class Inactive() extends Status

  case class Task(_id: ObjectId, name: String, status: Status)

  // ========== Null Handling Tests ==========

  "Codec" should "throw exception when encoding null root value" in {
    val registry = defaultBsonRegistry.newBuilder.register[User].build
    val codec = registry.get(classOf[User])

    val exception = intercept[BsonInvalidOperationException] {
      val writer = new org.bson.BsonDocumentWriter(new BsonDocument())
      codec.encode(writer, null, org.bson.codecs.EncoderContext.builder().build())
    }

    exception.getMessage should include("null value")
    exception.getMessage should include("BSON codecs do not support null root values")
  }

  it should "handle None in optional fields with ignoreNone policy" in {
    val registry = defaultBsonRegistry.newBuilder.ignoreNone
      .register[UserWithOption]
      .build

    val codec = registry.get(classOf[UserWithOption])
    val user = UserWithOption(new ObjectId(), "Alice", None)

    val doc = new BsonDocument()
    val writer = new org.bson.BsonDocumentWriter(doc)
    codec.encode(writer, user, org.bson.codecs.EncoderContext.builder().build())

    // Email field should not be present in BSON
    doc.containsKey("email") shouldBe false
  }

  it should "encode None as null with encodeNone policy" in {
    val registry = defaultBsonRegistry.newBuilder.encodeNone
      .register[UserWithOption]
      .build

    val codec = registry.get(classOf[UserWithOption])
    val user = UserWithOption(new ObjectId(), "Bob", None)

    val doc = new BsonDocument()
    val writer = new org.bson.BsonDocumentWriter(doc)
    codec.encode(writer, user, org.bson.codecs.EncoderContext.builder().build())

    // Email field should be present as null
    doc.containsKey("email") shouldBe true
    doc.get("email").isNull shouldBe true
  }

  // ========== Type Mismatch Tests ==========

  it should "throw exception when decoding invalid type for Int field" in {
    val registry = defaultBsonRegistry.newBuilder.register[User].build
    val codec = registry.get(classOf[User])

    // Create BSON with string where Int is expected
    val doc = new BsonDocument()
    doc.put("_id", new org.bson.BsonObjectId(new ObjectId()))
    doc.put("name", new org.bson.BsonString("Charlie"))
    doc.put("age", new org.bson.BsonString("not-a-number")) // Wrong type!

    val reader = new org.bson.BsonDocumentReader(doc)
    val exception = intercept[BsonInvalidOperationException] {
      codec.decode(reader, DecoderContext.builder().build())
    }

    exception.getMessage should include("Invalid numeric type")
  }

  // ========== Missing Fields Tests ==========

  it should "throw exception when required field is missing" in {
    val registry = defaultBsonRegistry.newBuilder.register[User].build
    val codec = registry.get(classOf[User])

    // Create BSON missing 'age' field
    val doc = new BsonDocument()
    doc.put("_id", new org.bson.BsonObjectId(new ObjectId()))
    doc.put("name", new org.bson.BsonString("David"))
    // Missing: age

    val reader = new org.bson.BsonDocumentReader(doc)
    val exception = intercept[RuntimeException] {
      codec.decode(reader, DecoderContext.builder().build())
    }

    exception.getMessage should include("Missing field")
  }

  // ========== Nested Structure Tests ==========

  it should "throw exception when nested case class has invalid data" in {
    val registry = defaultBsonRegistry.newBuilder
      .register[Address]
      .register[Person]
      .build

    val codec = registry.get(classOf[Person])

    // Create BSON with invalid nested address
    val doc = new BsonDocument()
    doc.put("_id", new org.bson.BsonObjectId(new ObjectId()))
    doc.put("name", new org.bson.BsonString("Eve"))

    val addressDoc = new BsonDocument()
    addressDoc.put("street", new org.bson.BsonString("123 Main St"))
    addressDoc.put("city", new org.bson.BsonString("NYC"))
    addressDoc.put("zipCode", new org.bson.BsonString("not-a-zipcode")) // Wrong type!
    doc.put("address", addressDoc)

    val reader = new org.bson.BsonDocumentReader(doc)
    val exception = intercept[BsonInvalidOperationException] {
      codec.decode(reader, DecoderContext.builder().build())
    }

    exception.getMessage should include("Invalid numeric type")
  }

  // ========== Sealed Trait Error Tests ==========
  // NOTE: Sealed trait error tests are in SealedTraitIntegrationSpec
  // They require full MongoDB integration testing due to macro scope limitations

  // ========== Registry Configuration Tests ==========

  it should "throw exception when codec is not registered" in {
    val registry = defaultBsonRegistry.newBuilder.build // Nothing registered!

    val exception = intercept[org.bson.codecs.configuration.CodecConfigurationException] {
      registry.get(classOf[User])
    }

    exception.getMessage should include("Can't find a codec for")
  }

  // Sealed trait registration tests are in SealedTraitIntegrationSpec

  // ========== Empty/Boundary Value Tests ==========

  it should "handle empty string fields correctly" in {
    val registry = defaultBsonRegistry.newBuilder.register[User].build
    val codec = registry.get(classOf[User])

    val user = User(new ObjectId(), "", 25) // Empty name

    val doc = new BsonDocument()
    val writer = new org.bson.BsonDocumentWriter(doc)
    codec.encode(writer, user, org.bson.codecs.EncoderContext.builder().build())

    doc.getString("name").getValue shouldBe ""
  }

  it should "handle boundary values for numeric types" in {
    val registry = defaultBsonRegistry.newBuilder.register[Boundaries].build
    val codec = registry.get(classOf[Boundaries])

    val boundaries = Boundaries(
      new ObjectId(),
      Int.MaxValue,
      Int.MinValue,
      Long.MaxValue,
      Long.MinValue
    )

    val doc = new BsonDocument()
    val writer = new org.bson.BsonDocumentWriter(doc)
    codec.encode(writer, boundaries, org.bson.codecs.EncoderContext.builder().build())

    doc.getInt32("maxInt").getValue shouldBe Int.MaxValue
    doc.getInt32("minInt").getValue shouldBe Int.MinValue
    doc.getInt64("maxLong").getValue shouldBe Long.MaxValue
    doc.getInt64("minLong").getValue shouldBe Long.MinValue
  }

  // ========== Collection Edge Cases ==========

  it should "handle empty collections correctly" in {
    val registry = defaultBsonRegistry.newBuilder.register[WithCollections].build
    val codec = registry.get(classOf[WithCollections])

    val obj = WithCollections(new ObjectId(), List.empty, Vector.empty)

    val doc = new BsonDocument()
    val writer = new org.bson.BsonDocumentWriter(doc)
    codec.encode(writer, obj, org.bson.codecs.EncoderContext.builder().build())

    doc.getArray("tags").size() shouldBe 0
    doc.getArray("scores").size() shouldBe 0
  }

end NegativeTestSpec
