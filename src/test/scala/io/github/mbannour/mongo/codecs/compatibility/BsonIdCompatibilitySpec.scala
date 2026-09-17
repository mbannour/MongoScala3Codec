package io.github.mbannour.mongo.codecs.compatibility

import org.bson.{BsonDocument, BsonObjectId, BsonString}
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.BsonId
import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** BSON compatibility test for `@BsonId`. See [[UserCompatibilitySpec]] for the package-level contract.
  *
  * A `@BsonId` field is stored under MongoDB's reserved `_id` name. A future macro refactor must not revert it to the Scala field name, and
  * must not start remapping unannotated fields named `id`.
  */
class BsonIdCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class User(@BsonId id: ObjectId, name: String)
  case class OrdinaryUser(id: String, name: String)

  private val fixedId = new ObjectId("507f1f77bcf86cd799439011")

  "User codec with a @BsonId field" should "encode to the frozen BSON representation using _id" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.ObjectIdCodec()))
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(fixedId, "Alice")

    val expectedBson = new BsonDocument()
      .append("_id", new BsonObjectId(fixedId))
      .append("name", new BsonString("Alice"))

    CodecTestKit.assertBsonStructure(user, expectedBson)
    CodecTestKit.fromBsonDocument[User](expectedBson) shouldBe user
    CodecTestKit.roundTrip(user) shouldBe user
  }

  "OrdinaryUser codec with an unannotated field named id" should "keep the frozen representation using id" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
      .register[OrdinaryUser]
      .build

    given codec: Codec[OrdinaryUser] = registry.get(classOf[OrdinaryUser])

    val user = OrdinaryUser("u-1", "Alice")

    val expectedBson = new BsonDocument()
      .append("id", new BsonString("u-1"))
      .append("name", new BsonString("Alice"))

    CodecTestKit.assertBsonStructure(user, expectedBson)
    CodecTestKit.fromBsonDocument[OrdinaryUser](expectedBson) shouldBe user
    CodecTestKit.roundTrip(user) shouldBe user
  }
end BsonIdCompatibilitySpec
