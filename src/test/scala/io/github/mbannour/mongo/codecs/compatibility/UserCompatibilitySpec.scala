package io.github.mbannour.mongo.codecs.compatibility

import org.bson.{BsonBoolean, BsonDocument, BsonInt32, BsonString}
import org.bson.codecs.configuration.CodecRegistries
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** BSON compatibility ("golden") tests.
  *
  * Each spec in this package freezes the exact BSON representation produced by the current codec for one supported model shape. A test
  * failure here means the wire format changed and must be treated as a compatibility break, not "fixed" by updating the expected BSON.
  *
  * Further compatibility specs (nested case classes, defaults, ADTs/discriminators, Either, recursive models, annotations) belong in this
  * same package.
  *
  * This spec and [[AnnotationCompositionCompatibilitySpec]] are written through the published `CodecTestKit`, which is how a user would
  * write them. The rest of the package deliberately drives the codec directly: if every compatibility test went through the kit, a bug in
  * the kit could hide a bug in a codec, and these are the tests that are supposed to notice.
  */
class UserCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class User(name: String, age: Int, active: Boolean)

  "User codec" should "encode to the frozen BSON representation and round-trip to an equal value" in {
    val baseRegistry = CodecRegistries.fromCodecs(
      new org.bson.codecs.StringCodec(),
      new org.bson.codecs.IntegerCodec(),
      new org.bson.codecs.BooleanCodec()
    )

    val registry = RegistryBuilder
      .from(baseRegistry)
      .register[User]
      .build

    val kit = CodecTestKit(registry.get(classOf[User]))

    val user = User("Alice", 30, true)

    val expectedBson = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("age", new BsonInt32(30))
      .append("active", new BsonBoolean(true))

    kit.assertBson(user, expectedBson)
    kit.assertDecode(expectedBson, user)
    kit.assertRoundTrip(user)
  }
end UserCompatibilitySpec
