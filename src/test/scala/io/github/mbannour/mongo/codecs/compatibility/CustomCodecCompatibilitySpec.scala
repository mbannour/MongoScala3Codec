package io.github.mbannour.mongo.codecs.compatibility

import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonDocument, BsonReader, BsonString, BsonWriter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.mongo.codecs.{CodecTestKit, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

/** A user-supplied codec for a field type the library knows nothing about. */
final case class AccountNumber(value: String)

class AccountNumberCodec extends Codec[AccountNumber]:
  override def encode(writer: BsonWriter, value: AccountNumber, context: EncoderContext): Unit =
    writer.writeString(value.value)
  override def decode(reader: BsonReader, context: DecoderContext): AccountNumber =
    AccountNumber(reader.readString())
  override def getEncoderClass: Class[AccountNumber] = classOf[AccountNumber]

/** BSON compatibility test for the boundary between a derived model codec and a user-supplied field codec. See [[UserCompatibilitySpec]]
  * for the package-level contract.
  *
  * This is the library's extension point: derivation writes the field name and then hands the value to whatever `Codec` the registry holds
  * for that class. Freezing it protects users who represent a domain type their own way - the derived wrapper must keep delegating, and
  * must not start wrapping the custom codec's output in a document of its own.
  */
class CustomCodecCompatibilitySpec extends AnyFlatSpec with Matchers:

  case class Account(number: AccountNumber, owner: String)

  private val registry = RegistryBuilder
    .from(
      CodecRegistries
        .fromRegistries(CodecRegistries.fromCodecs(new AccountNumberCodec()), CodecRegistries.fromCodecs(new org.bson.codecs.StringCodec()))
    )
    .register[Account]
    .build

  given codec: Codec[Account] = registry.get(classOf[Account])

  private val account = Account(AccountNumber("ACC-1"), "Alice")

  /** The custom codec writes a bare string, so that is exactly what lands in the field: no wrapper document, no type tag, nothing the
    * derived codec adds of its own.
    */
  private val frozenBson = new BsonDocument()
    .append("number", new BsonString("ACC-1"))
    .append("owner", new BsonString("Alice"))

  "Account codec" should "delegate the custom field to its registered codec, verbatim" in {
    CodecTestKit.assertBsonStructure(account, frozenBson)
  }

  it should "decode a hand-written document that was never produced by encode" in {
    CodecTestKit.fromBsonDocument[Account](frozenBson) shouldBe account
  }

  it should "round-trip to an equal value" in {
    CodecTestKit.roundTrip(account) shouldBe account
  }
end CustomCodecCompatibilitySpec
