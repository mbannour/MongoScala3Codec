package io.github.mbannour.mongo.codecs

import org.bson.{BsonDocument, BsonObjectId, BsonString}
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonId
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** Focused tests for `@BsonId`: the annotated field is stored under MongoDB's `_id` name, in both directions. */
class BsonIdSpec extends AnyFlatSpec with Matchers:

  case class User(@BsonId id: ObjectId, name: String)

  private val fixedId = new ObjectId("507f1f77bcf86cd799439011")

  private val registry = RegistryBuilder
    .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.ObjectIdCodec()))
    .register[User]
    .build

  given codec: Codec[User] = registry.get(classOf[User])

  "Encoding a @BsonId field" should "write it under the _id name and not under its Scala name" in {
    val user = User(fixedId, "Alice")

    val expectedBson = new BsonDocument()
      .append("_id", new BsonObjectId(fixedId))
      .append("name", new BsonString("Alice"))

    CodecTestKit.assertBsonStructure(user, expectedBson)

    val actual = CodecTestKit.toBsonDocument(user)
    actual.containsKey("id") shouldBe false
  }

  "Decoding a document with an _id field" should "populate the @BsonId annotated Scala field" in {
    val bson = new BsonDocument()
      .append("_id", new BsonObjectId(fixedId))
      .append("name", new BsonString("Alice"))

    CodecTestKit.fromBsonDocument[User](bson) shouldBe User(fixedId, "Alice")
  }

  "A @BsonId field" should "round-trip to an equal value" in {
    val user = User(fixedId, "Alice")

    CodecTestKit.roundTrip(user) shouldBe user
  }

  case class OrdinaryUser(id: String, name: String)

  "An unannotated field named id" should "keep its existing BSON name and not be remapped to _id" in {
    val ordinaryRegistry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
      .register[OrdinaryUser]
      .build

    given ordinaryCodec: Codec[OrdinaryUser] = ordinaryRegistry.get(classOf[OrdinaryUser])

    val user = OrdinaryUser("u-1", "Alice")

    val expectedBson = new BsonDocument()
      .append("id", new BsonString("u-1"))
      .append("name", new BsonString("Alice"))

    CodecTestKit.assertBsonStructure(user, expectedBson)
    CodecTestKit.roundTrip(user) shouldBe user
  }
end BsonIdSpec
