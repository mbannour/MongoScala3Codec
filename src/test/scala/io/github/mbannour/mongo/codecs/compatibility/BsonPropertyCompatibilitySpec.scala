package io.github.mbannour.mongo.codecs.compatibility

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.{BsonDocument, BsonInt32, BsonString}
import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** BSON compatibility test for `@BsonProperty`. See [[UserCompatibilitySpec]] for the package-level contract.
  *
  * The annotation decouples the Scala field name from the stored key, which is the whole point of it: a field can be renamed in Scala
  * without rewriting documents, and a document key can be chosen to match an existing collection. Both directions of that mapping are
  * frozen here, including the negative half - the Scala name must never appear in the document.
  */
class BsonPropertyCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class Contact(@BsonProperty("email_address") email: String, name: String)

  private val registry = RegistryBuilder
    .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
    .register[Contact]
    .build

  given codec: Codec[Contact] = registry.get(classOf[Contact])

  private val contact = Contact("alice@example.com", "Alice")

  private val frozenBson = new BsonDocument()
    .append("email_address", new BsonString("alice@example.com"))
    .append("name", new BsonString("Alice"))

  "Contact codec" should "encode the annotated field under its BSON name" in {
    CodecTestKit.assertBsonStructure(contact, frozenBson)
  }

  it should "never write the Scala field name" in {
    CodecTestKit.toBsonDocument(contact).containsKey("email") shouldBe false
  }

  it should "decode a hand-written document that was never produced by encode" in {
    CodecTestKit.fromBsonDocument[Contact](frozenBson) shouldBe contact
  }

  it should "round-trip to an equal value" in {
    CodecTestKit.roundTrip(contact) shouldBe contact
  }

  /** Renaming a stored key is a data migration, not a code change the codec can absorb. Freezing this keeps that cost visible: adding
    * `@BsonProperty` to a field with documents already written under the Scala name makes those documents unreadable until they are
    * migrated.
    */
  it should "not fall back to the Scala field name when reading an older document" in {
    val documentWrittenBeforeTheAnnotation = new BsonDocument()
      .append("email", new BsonString("alice@example.com"))
      .append("name", new BsonString("Alice"))

    val error = intercept[RuntimeException] {
      CodecTestKit.fromBsonDocument[Contact](documentWrittenBeforeTheAnnotation)
    }

    error.getMessage should include("email_address")
  }

  case class Reading(@BsonProperty("v") value: Int, @BsonProperty("t") tag: String)

  "Multiple annotated fields" should "each use their own BSON name" in {
    val readingRegistry = RegistryBuilder
      .from(CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec(), new org.bson.codecs.IntegerCodec()))
      .register[Reading]
      .build

    given readingCodec: Codec[Reading] = readingRegistry.get(classOf[Reading])

    val reading = Reading(42, "temp")

    val expected = new BsonDocument()
      .append("v", new BsonInt32(42))
      .append("t", new BsonString("temp"))

    CodecTestKit.assertBsonStructure(reading, expected)
    CodecTestKit.fromBsonDocument[Reading](expected) shouldBe reading
    CodecTestKit.roundTrip(reading) shouldBe reading
  }
end BsonPropertyCompatibilitySpec
