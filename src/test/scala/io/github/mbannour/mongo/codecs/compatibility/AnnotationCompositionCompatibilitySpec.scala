package io.github.mbannour.mongo.codecs.compatibility

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonObjectId, BsonString}
import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.{BsonId, BsonIgnore}
import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** BSON compatibility test for the three field annotations applied together, on different fields of one model. See
  * [[UserCompatibilitySpec]] for the package-level contract.
  *
  * Each annotation is frozen on its own elsewhere in this package. This spec exists because they all rewrite the same thing - the set of
  * keys in the document - and a single model exercising all three at once is what would catch one of them being applied in the wrong order,
  * or being dropped when another is present.
  *
  * Invalid *combinations* on a single field are rejected while compiling and belong to the diagnostic specs, not here.
  */
class AnnotationCompositionCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class Profile(
      @BsonId id: ObjectId,
      @BsonProperty("full_name") name: String,
      @BsonIgnore cachedAvatar: String = "none",
      email: String
  )

  private val registry = RegistryBuilder
    .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.ObjectIdCodec()))
    .register[Profile]
    .build

  given codec: Codec[Profile] = registry.get(classOf[Profile])

  private val fixedId = new ObjectId("507f1f77bcf86cd799439011")

  private val profile = Profile(fixedId, "Alice", "cached-blob", "alice@example.com")

  /** Each annotation acts on its own field and leaves the others alone: `id` becomes `_id`, `name` becomes `full_name`, `cachedAvatar`
    * disappears, and the unannotated `email` keeps its Scala name.
    */
  private val frozenBson = new BsonDocument()
    .append("_id", new BsonObjectId(fixedId))
    .append("full_name", new BsonString("Alice"))
    .append("email", new BsonString("alice@example.com"))

  "Profile codec" should "apply every annotation to its own field and leave the rest untouched" in {
    CodecTestKit.assertBsonStructure(profile, frozenBson)
  }

  it should "write neither the Scala names that were remapped nor the ignored field" in {
    val encoded = CodecTestKit.toBsonDocument(profile)

    encoded.containsKey("id") shouldBe false
    encoded.containsKey("name") shouldBe false
    encoded.containsKey("cachedAvatar") shouldBe false
    encoded.keySet().size() shouldBe 3
  }

  it should "decode a hand-written document that was never produced by encode" in {
    CodecTestKit.fromBsonDocument[Profile](frozenBson) shouldBe
      Profile(fixedId, "Alice", "none", "alice@example.com")
  }

  it should "round-trip, with the ignored field reset to its constructor default" in {
    CodecTestKit.roundTrip(profile) shouldBe Profile(fixedId, "Alice", "none", "alice@example.com")
  }
end AnnotationCompositionCompatibilitySpec
