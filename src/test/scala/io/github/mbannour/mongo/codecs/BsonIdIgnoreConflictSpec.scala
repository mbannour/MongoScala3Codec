package io.github.mbannour.mongo.codecs

import scala.compiletime.testing.typeCheckErrors

import org.bson.{BsonDocument, BsonObjectId, BsonString}
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.{BsonId, BsonIgnore}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** Compile-time contract for combining `@BsonId` and `@BsonIgnore`: `@BsonId` says the field is persisted as `_id`, `@BsonIgnore` says it
  * is not persisted at all. The two cannot apply to the same field, and derivation must say so rather than pick a winner.
  */
class BsonIdIgnoreConflictSpec extends AnyFlatSpec with Matchers:

  "A field annotated with both @BsonId and @BsonIgnore" should "fail to compile with an actionable diagnostic" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import org.bson.types.ObjectId
      import io.github.mbannour.bson.macros.{BsonId, BsonIgnore}
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class InvalidUser(@BsonId @BsonIgnore id: ObjectId = new ObjectId(), name: String)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.ObjectIdCodec()))
        .register[InvalidUser]
        .build
    """)

    errors should not be empty

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message should include("@BsonId")
      message should include("@BsonIgnore")
      message should include("id")
      message.toLowerCase should include("cannot")
    }
  }

  it should "fail identically when the annotations are written in the opposite order" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import org.bson.types.ObjectId
      import io.github.mbannour.bson.macros.{BsonId, BsonIgnore}
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class InvalidUser(@BsonIgnore @BsonId id: ObjectId = new ObjectId(), name: String)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.ObjectIdCodec()))
        .register[InvalidUser]
        .build
    """)

    errors should not be empty

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message should include("@BsonId")
      message should include("@BsonIgnore")
      message should include("id")
      message.toLowerCase should include("cannot")
    }
  }

  "A conflicting field that also lacks a constructor default" should "report the annotation conflict, not the missing default" in {
    val errors = typeCheckErrors("""
      import org.bson.codecs.configuration.CodecRegistries
      import org.bson.types.ObjectId
      import io.github.mbannour.bson.macros.{BsonId, BsonIgnore}
      import io.github.mbannour.mongo.codecs.RegistryBuilder
      import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

      case class InvalidUser(@BsonId @BsonIgnore id: ObjectId, name: String)

      RegistryBuilder
        .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.ObjectIdCodec()))
        .register[InvalidUser]
        .build
    """)

    errors should not be empty

    val message = errors.map(_.message).mkString("\n")

    withClue(s"diagnostic was:\n$message\n") {
      message should include("@BsonId")
      message should include("@BsonIgnore")
      message.toLowerCase should include("cannot")
      // The contradiction stands regardless of defaults, so that must not be the headline complaint.
      message should not include "requires a constructor default"
    }
  }

  case class User(@BsonId id: ObjectId, name: String, @BsonIgnore runtimeState: String = "idle")

  private val fixedId = new ObjectId("507f1f77bcf86cd799439011")

  "A model using @BsonId and @BsonIgnore on different fields" should "remain valid and keep both contracts" in {
    val registry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.ObjectIdCodec()))
      .register[User]
      .build

    given codec: Codec[User] = registry.get(classOf[User])

    val user = User(fixedId, "Alice", "running")

    val expectedBson = new BsonDocument()
      .append("_id", new BsonObjectId(fixedId))
      .append("name", new BsonString("Alice"))

    CodecTestKit.assertBsonStructure(user, expectedBson)
    CodecTestKit.fromBsonDocument[User](expectedBson) shouldBe User(fixedId, "Alice", "idle")
  }
end BsonIdIgnoreConflictSpec
