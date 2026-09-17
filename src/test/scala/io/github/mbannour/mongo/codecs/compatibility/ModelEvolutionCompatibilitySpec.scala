package io.github.mbannour.mongo.codecs.compatibility

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonInvalidOperationException}
import org.mongodb.scala.bson.annotations.BsonProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.github.mbannour.bson.macros.{BsonId, BsonIgnore}
import io.github.mbannour.mongo.codecs.{CodecConfig, CodecTestKit, EnumValueCodecProvider, RegistryBuilder}
import io.github.mbannour.mongo.codecs.RegistryBuilder$package.RegistryBuilder.*

// The v2 hierarchy: EvoBird was added to a trait that already had EvoDog and EvoCat.
sealed trait EvoBeast
case class EvoDog(name: String, age: Int) extends EvoBeast
case class EvoCat(name: String) extends EvoBeast
case class EvoBird(wingspan: Int) extends EvoBeast

// The v2 hierarchy after a subtype was deleted: documents written as "EvoTrimmedDog" are still in the collection.
sealed trait EvoTrimmedBeast
case class EvoTrimmedCat(name: String) extends EvoTrimmedBeast

// v1 was: Red, Green, Blue. v2 appends a case.
enum EvoColour:
  case Red, Green, Blue, Teal

// v1 was: Red, Green, Blue. v2 renames Green to Verde.
enum EvoColourRenamed:
  case Red, Verde, Blue

// v1 was: New, Active, Closed. v2 inserts Pending at index 1, shifting every later ordinal.
enum EvoStatus:
  case New, Pending, Active, Closed

case class EvoColourHolder(colour: EvoColour)
case class EvoRenamedHolder(colour: EvoColourRenamed)
case class EvoStatusHolder(status: EvoStatus)

// v1 was: Node(value, next). v2 adds a defaulted field to a recursive model.
case class EvoNode(value: String, next: Option[EvoNode], metadata: String = "")

/** The executable schema-evolution contract: what happens to BSON already sitting in MongoDB when the Scala model moves on.
  *
  * The sibling specs in this package freeze the wire format of an *unchanged* model. This one asks the production question those cannot: a
  * collection outlives the code that filled it, so a reader meets documents written by every version that ever ran. Each test below is a
  * historical document written by hand - never produced by the current codec, because a fixture the codec generated could only prove the
  * codec agrees with itself - decoded by the model as it stands today.
  *
  * Three separate ideas, kept apart on purpose:
  *
  *   - '''backward-reading''': the new codec reads BSON written by the old model. This is the direction that decides whether a deploy needs
  *     a data migration, and it is where most of this spec sits.
  *   - '''forward-reading''': the old codec reads BSON written by the new model. It rests on unknown-field tolerance, frozen in
  *     [[SchemaEvolutionCompatibilitySpec]].
  *   - '''wire compatibility''': the representation of an unchanged model does not drift. That is the rest of this package.
  *
  * A distinction worth stating once: the library promises not to change its own established representation within 1.x. It cannot promise
  * that an arbitrary change to *your* model is compatible with *your* stored data. This spec is the evidence for which of those changes are
  * safe, and no test here is a proposal to make an unsafe one work - there is no alias table, no version metadata and no migration
  * machinery anywhere in it.
  */
class ModelEvolutionCompatibilitySpec extends AnyFlatSpec with Matchers:

  private val primitives = CodecRegistries.fromCodecs(
    new org.bson.codecs.StringCodec(),
    new org.bson.codecs.IntegerCodec(),
    new org.bson.codecs.LongCodec(),
    new org.bson.codecs.DoubleCodec(),
    new org.bson.codecs.BooleanCodec(),
    new org.bson.codecs.ObjectIdCodec()
  )

  private def codecFor[T](using ct: scala.reflect.ClassTag[T])(registry: org.bson.codecs.configuration.CodecRegistry): Codec[T] =
    registry.get(ct.runtimeClass.asInstanceOf[Class[T]])

  // ===========================================================================
  // Adding a field
  // ===========================================================================

  case class UserAddedDefault(name: String, active: Boolean = true)

  /** The migration everyone reaches for first, and the one the rest of this section is measured against. */
  "Adding a field with a constructor default" should "read historical BSON, filling the field from the default" in {
    // Written by v1: case class User(name: String)
    val historical = BsonDocument.parse("""{"name": "Alice"}""")

    given Codec[UserAddedDefault] = RegistryBuilder.from(primitives).register[UserAddedDefault].build.get(classOf[UserAddedDefault])

    CodecTestKit.fromBsonDocument[UserAddedDefault](historical) shouldBe UserAddedDefault("Alice", active = true)
  }

  case class ContactAddedOption(name: String, email: Option[String])
  case class ContactAddedOptionDefaulted(name: String, email: Option[String] = None)

  /** An absent key already means `None`, so an added `Option` needs no default to read old documents. Both spellings are asserted because
    * the two mechanisms are different - absence handled by the Option decoder, and absence handled by the constructor default - and a user
    * choosing between them deserves to know they behave alike here.
    */
  "Adding an Option field without a default" should "read historical BSON as None" in {
    val historical = BsonDocument.parse("""{"name": "Alice"}""")

    given Codec[ContactAddedOption] = RegistryBuilder.from(primitives).register[ContactAddedOption].build.get(classOf[ContactAddedOption])

    CodecTestKit.fromBsonDocument[ContactAddedOption](historical) shouldBe ContactAddedOption("Alice", None)
  }

  "Adding an Option field with an explicit = None default" should "read historical BSON as None as well" in {
    val historical = BsonDocument.parse("""{"name": "Alice"}""")

    given Codec[ContactAddedOptionDefaulted] =
      RegistryBuilder.from(primitives).register[ContactAddedOptionDefaulted].build.get(classOf[ContactAddedOptionDefaulted])

    CodecTestKit.fromBsonDocument[ContactAddedOptionDefaulted](historical) shouldBe ContactAddedOptionDefaulted("Alice", None)
  }

  case class UserAddedRequired(name: String, age: Int)

  /** No default and no stored value leaves the decoder nothing to construct from, so it refuses rather than inventing a zero. Only the
    * failure and the named field are asserted; the message text is not a contract.
    */
  "Adding a required field with no default" should "refuse to read historical BSON, naming the field" in {
    val historical = BsonDocument.parse("""{"name": "Alice"}""")

    given Codec[UserAddedRequired] = RegistryBuilder.from(primitives).register[UserAddedRequired].build.get(classOf[UserAddedRequired])

    val error = intercept[RuntimeException](CodecTestKit.fromBsonDocument[UserAddedRequired](historical))

    error.getMessage should include("age")
  }

  case class UserAddedCollectionDefault(name: String, tags: List[String] = Nil)

  "Adding a collection field with a default" should "read historical BSON as the default, not as null" in {
    val historical = BsonDocument.parse("""{"name": "Alice"}""")

    given Codec[UserAddedCollectionDefault] =
      RegistryBuilder.from(primitives).register[UserAddedCollectionDefault].build.get(classOf[UserAddedCollectionDefault])

    CodecTestKit.fromBsonDocument[UserAddedCollectionDefault](historical) shouldBe UserAddedCollectionDefault("Alice", Nil)
  }

  // ===========================================================================
  // Removing a field
  // ===========================================================================

  case class UserWithoutLegacyCode(name: String)

  /** Dropping a field from the model leaves its values in every stored document. They are simply not read - the same tolerance that makes a
    * rolling deploy possible, seen from the other side.
    */
  "Removing a field from the model" should "still read historical BSON that carries it" in {
    // Written by v1: case class User(name: String, legacyCode: String)
    val historical = BsonDocument.parse("""{"name": "Alice", "legacyCode": "ABC"}""")

    given Codec[UserWithoutLegacyCode] =
      RegistryBuilder.from(primitives).register[UserWithoutLegacyCode].build.get(classOf[UserWithoutLegacyCode])

    CodecTestKit.fromBsonDocument[UserWithoutLegacyCode](historical) shouldBe UserWithoutLegacyCode("Alice")
  }

  it should "stop writing the removed field, so a rewrite drops the historical value" in {
    given Codec[UserWithoutLegacyCode] =
      RegistryBuilder.from(primitives).register[UserWithoutLegacyCode].build.get(classOf[UserWithoutLegacyCode])

    import scala.jdk.CollectionConverters.*
    CodecTestKit.toBsonDocument(UserWithoutLegacyCode("Alice")).keySet().asScala.toSet shouldBe Set("name")
  }

  // ===========================================================================
  // Renaming
  // ===========================================================================

  case class SubscriberRenamedInScala(@BsonProperty("email") emailAddress: String)

  /** The one refactor that is free: the Scala name moves, the persisted name is pinned by the annotation, and stored documents never
    * notice. Both directions are asserted, because a rename that reads old documents but writes a new key would split the collection in
    * two.
    */
  "Renaming a Scala field while pinning the BSON name with @BsonProperty" should "read historical BSON" in {
    // Written by v1: case class Subscriber(email: String)
    val historical = BsonDocument.parse("""{"email": "a@example.com"}""")

    given Codec[SubscriberRenamedInScala] =
      RegistryBuilder.from(primitives).register[SubscriberRenamedInScala].build.get(classOf[SubscriberRenamedInScala])

    CodecTestKit.fromBsonDocument[SubscriberRenamedInScala](historical) shouldBe SubscriberRenamedInScala("a@example.com")
  }

  it should "keep writing the historical BSON name" in {
    given Codec[SubscriberRenamedInScala] =
      RegistryBuilder.from(primitives).register[SubscriberRenamedInScala].build.get(classOf[SubscriberRenamedInScala])

    CodecTestKit.assertBsonStructure(
      SubscriberRenamedInScala("a@example.com"),
      BsonDocument.parse("""{"email": "a@example.com"}""")
    )
  }

  case class SubscriberRenamedInBson(@BsonProperty("email_address") emailAddress: String)
  case class SubscriberRenamedInBsonOptional(@BsonProperty("email_address") emailAddress: Option[String])

  /** Changing the persisted name is a data migration, not a refactor: the library holds no alias table, so the old key is simply an unknown
    * field and the new one is missing.
    */
  "Renaming the persisted BSON name" should "not read historical BSON stored under the old name" in {
    val historical = BsonDocument.parse("""{"email": "a@example.com"}""")

    given Codec[SubscriberRenamedInBson] =
      RegistryBuilder.from(primitives).register[SubscriberRenamedInBson].build.get(classOf[SubscriberRenamedInBson])

    val error = intercept[RuntimeException](CodecTestKit.fromBsonDocument[SubscriberRenamedInBson](historical))

    error.getMessage should include("email_address")
  }

  /** The same rename on an `Option` field is worse than a failure: absence means `None`, so the historical value is silently dropped and
    * the first the application hears of it is an empty field.
    */
  it should "silently read as None when the renamed field is optional, losing the historical value" in {
    val historical = BsonDocument.parse("""{"email": "a@example.com"}""")

    given Codec[SubscriberRenamedInBsonOptional] =
      RegistryBuilder.from(primitives).register[SubscriberRenamedInBsonOptional].build.get(classOf[SubscriberRenamedInBsonOptional])

    CodecTestKit.fromBsonDocument[SubscriberRenamedInBsonOptional](historical) shouldBe SubscriberRenamedInBsonOptional(None)
  }

  // ===========================================================================
  // @BsonId and @BsonIgnore
  // ===========================================================================

  case class EntityWithBsonId(@BsonId id: ObjectId, name: String)
  case class EntityWithPlainId(id: ObjectId, name: String)

  private val fixedId = new ObjectId("507f1f77bcf86cd799439011")

  "A @BsonId field" should "read historical BSON stored under _id" in {
    val historical = BsonDocument.parse(s"""{"_id": {"$$oid": "$fixedId"}, "name": "Alice"}""")

    given Codec[EntityWithBsonId] = RegistryBuilder.from(primitives).register[EntityWithBsonId].build.get(classOf[EntityWithBsonId])

    CodecTestKit.fromBsonDocument[EntityWithBsonId](historical) shouldBe EntityWithBsonId(fixedId, "Alice")
  }

  /** Dropping the annotation moves the field from `_id` to `id`, which makes it a persisted-name change - the same breaking shape as any
    * other BSON rename, and easy to make by accident because the Scala type is untouched.
    */
  it should "stop reading _id documents once the annotation is removed" in {
    val historical = BsonDocument.parse(s"""{"_id": {"$$oid": "$fixedId"}, "name": "Alice"}""")

    given Codec[EntityWithPlainId] = RegistryBuilder.from(primitives).register[EntityWithPlainId].build.get(classOf[EntityWithPlainId])

    val error = intercept[RuntimeException](CodecTestKit.fromBsonDocument[EntityWithPlainId](historical))

    error.getMessage should include("id")
  }

  case class SessionWithNewIgnored(name: String, @BsonIgnore runtimeState: String = "idle")
  case class SessionWithNowIgnored(name: String, @BsonIgnore legacyState: String = "idle")

  "Adding a @BsonIgnore field with a default" should "read historical BSON that could not have contained it" in {
    val historical = BsonDocument.parse("""{"name": "Alice"}""")

    given Codec[SessionWithNewIgnored] =
      RegistryBuilder.from(primitives).register[SessionWithNewIgnored].build.get(classOf[SessionWithNewIgnored])

    CodecTestKit.fromBsonDocument[SessionWithNewIgnored](historical) shouldBe SessionWithNewIgnored("Alice", "idle")
  }

  /** Characterized, not endorsed, and previously untested: `@BsonIgnore` suppresses the *write*, but the decoder still populates the field
    * from the document when the key happens to be there. The existing `@BsonIgnore` specs only ever decode documents that lack the key, so
    * they agree with this without covering it.
    *
    * Two consequences follow. For evolution, a field that stops being persisted keeps its historical value until the document is rewritten
    * \- so the loss is silent and deferred rather than immediate. More broadly, a field the author marked as never persisted can still be
    * sourced from stored data, which is worth deciding deliberately rather than inheriting.
    */
  "Marking a persisted field @BsonIgnore" should "still read the historical value, although it is no longer written" in {
    // Written by v1, when legacyState was an ordinary persisted field.
    val historical = BsonDocument.parse("""{"name": "Alice", "legacyState": "warm"}""")

    given Codec[SessionWithNowIgnored] =
      RegistryBuilder.from(primitives).register[SessionWithNowIgnored].build.get(classOf[SessionWithNowIgnored])

    // The constructor default is used only when the key is absent, as [[BsonIgnoreCompatibilitySpec]] shows.
    CodecTestKit.fromBsonDocument[SessionWithNowIgnored](historical) shouldBe SessionWithNowIgnored("Alice", "warm")
  }

  it should "stop persisting the field, so the next write erases the historical value for good" in {
    given Codec[SessionWithNowIgnored] =
      RegistryBuilder.from(primitives).register[SessionWithNowIgnored].build.get(classOf[SessionWithNowIgnored])

    import scala.jdk.CollectionConverters.*
    CodecTestKit.toBsonDocument(SessionWithNowIgnored("Alice", "warm")).keySet().asScala.toSet shouldBe Set("name")
  }

  // ===========================================================================
  // Changing a field's type
  // ===========================================================================

  case class UserAgeAsString(name: String, age: String)
  case class CounterAsLong(ticks: Long)
  case class CounterAsInt(ticks: Int)

  "Changing a field from Int to String" should "not read historical BSON holding an Int32" in {
    val historical = BsonDocument.parse("""{"name": "Alice", "age": 37}""")

    given Codec[UserAgeAsString] = RegistryBuilder.from(primitives).register[UserAgeAsString].build.get(classOf[UserAgeAsString])

    intercept[BsonInvalidOperationException](CodecTestKit.fromBsonDocument[UserAgeAsString](historical))
  }

  /** The driver's number codecs convert between BSON numeric types, but only where the value survives the trip. Both halves are asserted
    * together so neither can be quoted on its own: this is not "numeric changes are safe", it is "numeric changes are safe for values that
    * fit".
    */
  "Widening a field from Int to Long" should "read historical BSON holding an Int32" in {
    val historical = BsonDocument.parse("""{"ticks": 5}""")

    given Codec[CounterAsLong] = RegistryBuilder.from(primitives).register[CounterAsLong].build.get(classOf[CounterAsLong])

    CodecTestKit.fromBsonDocument[CounterAsLong](historical) shouldBe CounterAsLong(5L)
  }

  "Narrowing a field from Long to Int" should "read historical Int64 values that fit in an Int" in {
    val historical = BsonDocument.parse("""{"ticks": {"$numberLong": "5"}}""")

    given Codec[CounterAsInt] = RegistryBuilder.from(primitives).register[CounterAsInt].build.get(classOf[CounterAsInt])

    CodecTestKit.fromBsonDocument[CounterAsInt](historical) shouldBe CounterAsInt(5)
  }

  it should "refuse historical Int64 values that do not fit, rather than truncating them" in {
    val historical = BsonDocument.parse("""{"ticks": {"$numberLong": "2147483648"}}""")

    given Codec[CounterAsInt] = RegistryBuilder.from(primitives).register[CounterAsInt].build.get(classOf[CounterAsInt])

    val error = intercept[BsonInvalidOperationException](CodecTestKit.fromBsonDocument[CounterAsInt](historical))

    error.getMessage should include("without losing precision")
  }

  case class NameWithDefault(name: String = "unknown")

  /** Characterized, not endorsed. An explicit stored `null` is not the same as an absent key: absence is what triggers a constructor
    * default, while a null is a value, and it reaches a non-Option field as a Scala `null`. This matters most on the migration where a
    * field stops being an `Option`, because `NoneHandling.Encode` - the default - wrote exactly these nulls.
    */
  "An explicit stored null on a non-Option field" should "decode as null rather than applying the constructor default" in {
    val historical = BsonDocument.parse("""{"name": null}""")

    given Codec[NameWithDefault] = RegistryBuilder.from(primitives).register[NameWithDefault].build.get(classOf[NameWithDefault])

    CodecTestKit.fromBsonDocument[NameWithDefault](historical).name shouldBe null
  }

  it should "decode as zero on a numeric field" in {
    val historical = BsonDocument.parse("""{"ticks": null}""")

    given Codec[CounterAsInt] = RegistryBuilder.from(primitives).register[CounterAsInt].build.get(classOf[CounterAsInt])

    CodecTestKit.fromBsonDocument[CounterAsInt](historical) shouldBe CounterAsInt(0)
  }

  // ===========================================================================
  // Nested models
  // ===========================================================================

  case class AddressWithDefaultedZip(city: String, zip: String = "unknown")
  case class ResidentWithDefaultedZip(name: String, address: AddressWithDefaultedZip)

  case class AddressWithRequiredZip(city: String, zip: String)
  case class ResidentWithRequiredZip(name: String, address: AddressWithRequiredZip)

  "Adding a defaulted field to a nested model" should "read historical nested BSON" in {
    // Written by v1: Address(city)
    val historical = BsonDocument.parse("""{"name": "Alice", "address": {"city": "Munich"}}""")

    val registry = RegistryBuilder.from(primitives).register[AddressWithDefaultedZip].register[ResidentWithDefaultedZip].build
    given Codec[ResidentWithDefaultedZip] = registry.get(classOf[ResidentWithDefaultedZip])

    CodecTestKit.fromBsonDocument[ResidentWithDefaultedZip](historical) shouldBe
      ResidentWithDefaultedZip("Alice", AddressWithDefaultedZip("Munich", "unknown"))
  }

  "Adding a required field to a nested model" should "refuse to read historical nested BSON, naming the nested field" in {
    val historical = BsonDocument.parse("""{"name": "Alice", "address": {"city": "Munich"}}""")

    val registry = RegistryBuilder.from(primitives).register[AddressWithRequiredZip].register[ResidentWithRequiredZip].build
    given Codec[ResidentWithRequiredZip] = registry.get(classOf[ResidentWithRequiredZip])

    val error = intercept[RuntimeException](CodecTestKit.fromBsonDocument[ResidentWithRequiredZip](historical))

    error.getMessage should include("zip")
  }

  // ===========================================================================
  // Collections
  // ===========================================================================

  case class BasketOfStrings(tags: List[String])
  case class BasketOfInts(tags: List[Int])

  "An unchanged collection field" should "read historical BSON arrays" in {
    val historical = BsonDocument.parse("""{"tags": ["scala", "mongodb"]}""")

    given Codec[BasketOfStrings] = RegistryBuilder.from(primitives).register[BasketOfStrings].build.get(classOf[BasketOfStrings])

    CodecTestKit.fromBsonDocument[BasketOfStrings](historical) shouldBe BasketOfStrings(List("scala", "mongodb"))
  }

  "Changing a collection's element type" should "not read historical arrays of the old element type" in {
    val historical = BsonDocument.parse("""{"tags": ["scala", "mongodb"]}""")

    given Codec[BasketOfInts] = RegistryBuilder.from(primitives).register[BasketOfInts].build.get(classOf[BasketOfInts])

    intercept[BsonInvalidOperationException](CodecTestKit.fromBsonDocument[BasketOfInts](historical))
  }

  // ===========================================================================
  // Sealed ADTs and discriminators
  // ===========================================================================

  /** A discriminator value identifies one subtype, so adding a sibling cannot reinterpret it. This is what makes an ADT extensible in
    * production while its documents stay readable.
    */
  "Adding a subtype to a sealed hierarchy" should "still read historical BSON of the existing subtypes" in {
    // Written before EvoBird existed.
    val historicalDog = BsonDocument.parse("""{"_type": "EvoDog", "name": "Rex", "age": 3}""")
    val historicalCat = BsonDocument.parse("""{"_type": "EvoCat", "name": "Tom"}""")

    given Codec[EvoBeast] = RegistryBuilder.from(primitives).registerSealed[EvoBeast].build.get(classOf[EvoBeast])

    CodecTestKit.fromBsonDocument[EvoBeast](historicalDog) shouldBe EvoDog("Rex", 3)
    CodecTestKit.fromBsonDocument[EvoBeast](historicalCat) shouldBe EvoCat("Tom")
  }

  /** Failing loudly is the right outcome: there is no subtype left to hold the document, and the alternative would be decoding it into
    * whatever else happens to fit.
    */
  "Removing a subtype from a sealed hierarchy" should "not read historical BSON written under the removed discriminator" in {
    val historical = BsonDocument.parse("""{"_type": "EvoTrimmedDog", "name": "Rex", "age": 3}""")

    given Codec[EvoTrimmedBeast] = RegistryBuilder.from(primitives).registerSealed[EvoTrimmedBeast].build.get(classOf[EvoTrimmedBeast])

    val error = intercept[BsonInvalidOperationException](CodecTestKit.fromBsonDocument[EvoTrimmedBeast](historical))

    error.getMessage should include("EvoTrimmedDog")
  }

  /** Renaming the discriminator *field* relocates the only key that says what a document is, so every historical document becomes
    * unidentifiable at once - a heavier change than renaming any single value. ([[DiscriminatorValueCompatibilitySpec]] covers changing a
    * discriminator *value*, which is equally breaking.)
    */
  "Changing the discriminator field name" should "not read historical BSON written under the old field" in {
    val historical = BsonDocument.parse("""{"_type": "EvoCat", "name": "Tom"}""")

    given Codec[EvoBeast] = RegistryBuilder
      .from(primitives)
      .withConfig(CodecConfig(discriminatorField = "_class"))
      .registerSealed[EvoBeast]
      .build
      .get(classOf[EvoBeast])

    val error = intercept[BsonInvalidOperationException](CodecTestKit.fromBsonDocument[EvoBeast](historical))

    error.getMessage should include("_class")
  }

  // ===========================================================================
  // Enums
  // ===========================================================================

  private def stringEnumRegistry[E <: reflect.Enum, H](provider: org.bson.codecs.configuration.CodecProvider, holder: Class[H])(
      build: RegistryBuilder => RegistryBuilder
  ): Codec[H] =
    build(RegistryBuilder.from(CodecRegistries.fromRegistries(CodecRegistries.fromProviders(provider), primitives))).build.get(holder)

  "Adding a case to a string enum" should "still read historical BSON holding an existing case name" in {
    // Written when the enum was Red, Green, Blue - before Teal was appended.
    val historical = BsonDocument.parse("""{"colour": "Green"}""")

    given Codec[EvoColourHolder] =
      stringEnumRegistry(EnumValueCodecProvider.forStringEnum[EvoColour], classOf[EvoColourHolder])(_.register[EvoColourHolder])

    CodecTestKit.fromBsonDocument[EvoColourHolder](historical) shouldBe EvoColourHolder(EvoColour.Green)
  }

  /** A string enum persists case names, so a rename is a rename of stored data. It fails rather than guessing, which is the outcome to want
    * \- but it does mean the rename needs a migration.
    */
  "Renaming a case of a string enum" should "not read historical BSON holding the old case name" in {
    val historical = BsonDocument.parse("""{"colour": "Green"}""")

    given Codec[EvoRenamedHolder] =
      stringEnumRegistry(EnumValueCodecProvider.forStringEnum[EvoColourRenamed], classOf[EvoRenamedHolder])(_.register[EvoRenamedHolder])

    val error = intercept[RuntimeException](CodecTestKit.fromBsonDocument[EvoRenamedHolder](historical))

    error.getMessage should include("Green")
  }

  /** The most dangerous result in this spec, and the only one that neither fails nor preserves meaning.
    *
    * An ordinal enum persists positions, so the case *order* becomes part of the stored schema. Inserting a case shifts every later
    * ordinal, and a document that meant `Active` now decodes - successfully, silently, with no error anywhere - as `Pending`. Nothing in
    * the document distinguishes the two readings, so no decoder could detect it.
    */
  "Inserting a case into an ordinal enum" should "silently reinterpret historical ordinals as the wrong case" in {
    // Written when the enum was New, Active, Closed: ordinal 1 meant Active.
    val historical = BsonDocument.parse("""{"status": 1}""")

    given Codec[EvoStatusHolder] =
      stringEnumRegistry(EnumValueCodecProvider.forOrdinalEnum[EvoStatus], classOf[EvoStatusHolder])(_.register[EvoStatusHolder])

    // v2 inserted Pending at index 1, so ordinal 1 now means Pending. The decode succeeds; the meaning changed.
    CodecTestKit.fromBsonDocument[EvoStatusHolder](historical) shouldBe EvoStatusHolder(EvoStatus.Pending)
    EvoStatus.values(1) should not be EvoStatus.Active
  }

  /** The only ordinal drift that is detectable: an ordinal past the end of the enum has no case to land on. Shortening an enum therefore
    * fails loudly, while reordering one does not.
    */
  "An ordinal beyond the end of the enum" should "fail rather than decode" in {
    val historical = BsonDocument.parse("""{"status": 99}""")

    given Codec[EvoStatusHolder] =
      stringEnumRegistry(EnumValueCodecProvider.forOrdinalEnum[EvoStatus], classOf[EvoStatusHolder])(_.register[EvoStatusHolder])

    val error = intercept[RuntimeException](CodecTestKit.fromBsonDocument[EvoStatusHolder](historical))

    error.getMessage should include("99")
  }

  /** Switching representation is itself a migration: the two codecs do not read each other's documents. */
  "Switching a string enum to an ordinal enum" should "not read historical BSON holding case names" in {
    val historical = BsonDocument.parse("""{"status": "Active"}""")

    given Codec[EvoStatusHolder] =
      stringEnumRegistry(EnumValueCodecProvider.forOrdinalEnum[EvoStatus], classOf[EvoStatusHolder])(_.register[EvoStatusHolder])

    intercept[BsonInvalidOperationException](CodecTestKit.fromBsonDocument[EvoStatusHolder](historical))
  }

  // ===========================================================================
  // Recursive models
  // ===========================================================================

  /** Defaults are applied wherever the model is constructed, including at every level of a recursive decode - so a recursive model evolves
    * like any other.
    */
  "Adding a defaulted field to a recursive model" should "read historical BSON at every level of nesting" in {
    // Written by v1: Node(value, next), with no metadata anywhere.
    val historical = BsonDocument.parse("""{"value": "a", "next": {"value": "b", "next": {"value": "c"}}}""")

    given Codec[EvoNode] = RegistryBuilder.from(primitives).register[EvoNode].build.get(classOf[EvoNode])

    CodecTestKit.fromBsonDocument[EvoNode](historical) shouldBe
      EvoNode("a", Some(EvoNode("b", Some(EvoNode("c", None, "")), "")), "")
  }

  // ===========================================================================
  // The custom-codec boundary
  // ===========================================================================

  case class AccountWithAddedDefault(number: AccountNumber, owner: String, tier: String = "standard")

  private val accountRegistry = RegistryBuilder
    .from(CodecRegistries.fromRegistries(CodecRegistries.fromCodecs(new AccountNumberCodec()), primitives))
    .register[AccountWithAddedDefault]
    .build

  /** The library's half of the bargain holds across evolution: it keeps writing and reading the field name and delegates the value
    * untouched, so an added defaulted field beside a custom-codec field behaves as it would anywhere else.
    */
  "A model with a custom field codec" should "evolve on the library's side like any other model" in {
    // Written by v1: Account(number, owner), before tier existed.
    val historical = BsonDocument.parse("""{"number": "ACC-1", "owner": "Alice"}""")

    given Codec[AccountWithAddedDefault] = accountRegistry.get(classOf[AccountWithAddedDefault])

    CodecTestKit.fromBsonDocument[AccountWithAddedDefault](historical) shouldBe
      AccountWithAddedDefault(AccountNumber("ACC-1"), "Alice", "standard")
  }

  /** What the library cannot promise: the representation *inside* a custom codec is the user's own contract. If that codec's stored shape
    * changes, the failure surfaces from the user's code, and no derivation-level guarantee applies to it.
    */
  it should "leave the stored shape of the custom field entirely to that codec" in {
    // A value the user's codec never wrote: its own representation changed between versions.
    val historical = BsonDocument.parse("""{"number": 1, "owner": "Alice"}""")

    given Codec[AccountWithAddedDefault] = accountRegistry.get(classOf[AccountWithAddedDefault])

    intercept[BsonInvalidOperationException](CodecTestKit.fromBsonDocument[AccountWithAddedDefault](historical))
  }
end ModelEvolutionCompatibilitySpec
