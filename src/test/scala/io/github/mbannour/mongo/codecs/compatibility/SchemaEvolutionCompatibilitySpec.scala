package io.github.mbannour.mongo.codecs.compatibility

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.{BsonDocument, BsonInt32, BsonString}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** Decode-side compatibility: what happens when a stored document and the current case class disagree. See [[UserCompatibilitySpec]] for
  * the package-level contract.
  *
  * A collection accumulates documents written by every version of the code that ever ran against it, so the decoder meets documents with
  * fields the model has dropped and documents missing fields the model has gained. These tests state which of those a reader survives,
  * because that is what decides whether a rolling deploy is safe.
  */
class SchemaEvolutionCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class Person(name: String)
  case class PersonWithAge(name: String, age: Int)

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec()
  )

  private val personRegistry = RegistryBuilder.from(primitives).register[Person].build
  given personCodec: Codec[Person] = personRegistry.get(classOf[Person])

  /** The forward-compatible half: a reader running old code can read documents written by new code. Without this, deploying a model change
    * would require every reader to be upgraded first.
    */
  "A document carrying a field the model does not declare" should "decode, ignoring the unknown field" in {
    val writtenByANewerVersion = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("nickname", new BsonString("Al"))

    CodecTestKit.fromBsonDocument[Person](writtenByANewerVersion) shouldBe Person("Alice")
  }

  it should "ignore the unknown field wherever it appears in the document" in {
    val unknownFieldFirst = new BsonDocument()
      .append("nickname", new BsonString("Al"))
      .append("name", new BsonString("Alice"))

    CodecTestKit.fromBsonDocument[Person](unknownFieldFirst) shouldBe Person("Alice")
  }

  it should "ignore an unknown field that is itself a document, not just a scalar" in {
    val withNestedUnknown = new BsonDocument()
      .append("name", new BsonString("Alice"))
      .append("audit", new BsonDocument().append("version", new BsonInt32(2)))

    CodecTestKit.fromBsonDocument[Person](withNestedUnknown) shouldBe Person("Alice")
  }

  private val agedRegistry = RegistryBuilder.from(primitives).register[PersonWithAge].build
  given agedCodec: Codec[PersonWithAge] = agedRegistry.get(classOf[PersonWithAge])

  /** The other direction is not forgiving, and deliberately so: a required field with no default has no value the decoder could invent.
    * Adding a field to a model with documents already stored therefore needs either a constructor default (see
    * [[ConstructorDefaultCompatibilitySpec]]) or a migration.
    *
    * Only the failure and the named field are asserted, not the exception type's full message text.
    */
  "A document missing a required field with no default" should "fail to decode, naming the missing field" in {
    val writtenBeforeAgeExisted = new BsonDocument()
      .append("name", new BsonString("Alice"))

    val error = intercept[RuntimeException] {
      CodecTestKit.fromBsonDocument[PersonWithAge](writtenBeforeAgeExisted)
    }

    error.getMessage should include("age")
  }
end SchemaEvolutionCompatibilitySpec
