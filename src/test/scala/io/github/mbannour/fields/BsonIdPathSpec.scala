package io.github.mbannour.fields

import org.bson.types.ObjectId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.{BsonId, BsonIgnore}

/** A `@BsonId` field is stored as `_id`, so the compile-time field path must resolve to `_id` too. Otherwise filters and updates built from
  * a path would silently target a field the codec never writes.
  */
class BsonIdPathSpec extends AnyFlatSpec with Matchers:

  case class User(@BsonId id: ObjectId, name: String)

  "MongoPath.of" should "resolve a @BsonId field to _id" in {
    MongoPath.of[User](_.id) shouldBe "_id"
  }

  it should "leave unannotated fields untouched" in {
    MongoPath.of[User](_.name) shouldBe "name"
  }

  case class UserWithIgnored(@BsonId id: ObjectId, name: String, @BsonIgnore runtimeState: String = "idle")

  it should "still resolve @BsonId to _id when the model also carries a @BsonIgnore field" in {
    MongoPath.of[UserWithIgnored](_.id) shouldBe "_id"
    MongoPath.of[UserWithIgnored](_.name) shouldBe "name"
  }
end BsonIdPathSpec
