package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import org.bson.types.ObjectId
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import RegistryBuilder.{*, given}

/** Integration tests for sealed trait codec evolution scenarios.
  *
  * These tests document what happens when a sealed trait hierarchy changes while old documents already exist in MongoDB.
  * The discriminator field (_type) is the key to polymorphic decoding, so any change to discriminator values is a
  * breaking change.
  *
  * Limitation: `DiscriminatorStrategy.Custom` is defined in `CodecConfig` but is NOT currently applied by the codec
  * generator. The discriminator is always the simple class name determined at compile time. There is no built-in
  * migration tool for decoding documents that use an old discriminator value from a renamed subtype.
  *
  * Scenarios covered:
  *   - Unknown discriminator: a subtype was renamed and old documents have the old _type value — decoding fails.
  *   - Option[SealedTrait]: optional sealed trait fields encode and decode correctly.
  *   - Map[String, SealedTrait]: sealed trait as map values.
  */
class SealedTraitEvolutionIntegrationSpec
    extends AnyFlatSpec
    with ForAllTestContainer
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll:

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  override val container: MongoDBContainer = MongoDBContainer("mongo:6.0.19")

  private lazy val mongoUri: String =
    s"mongodb://${container.containerIpAddress}:${container.mappedPort(27017)}"

  private def db: MongoDatabase =
    MongoClient(mongoUri).getDatabase("sealed_evolution_test")

  // ── Models ───────────────────────────────────────────────────────────────────

  // Current sealed trait: "PushNotification" was previously called "AppNotification"
  sealed trait Notification
  case class EmailNotification(email: String, subject: String) extends Notification
  case class SmsNotification(phone: String, message: String)   extends Notification
  case class PushNotification(deviceToken: String, title: String) extends Notification

  // Envelope that holds a Notification
  case class Alert(_id: ObjectId, recipient: String, notification: Notification)

  // Envelope with an optional notification
  case class OptionalAlert(_id: ObjectId, recipient: String, notification: Option[Notification])

  // Config holding a map of named notification channels
  case class NotificationConfig(_id: ObjectId, channels: Map[String, Notification])

  // ── Tests: unknown discriminator ─────────────────────────────────────────────

  "Sealed trait evolution: unknown discriminator value" should
    "fail to decode a document whose _type was written under the old subtype name" in {
      assert(container.container.isRunning)

      val oid = new ObjectId()

      // Simulate an old document with _type: "AppNotification" (the previous class name)
      val rawColl = db.getCollection("alerts_unknown_discriminator")
      rawColl
        .insertOne(
          Document(
            "_id"          -> oid,
            "recipient"    -> "user@example.com",
            "notification" -> Document(
              "_type"       -> "AppNotification", // old discriminator — no longer registered
              "deviceToken" -> "abc123",
              "title"       -> "Hello"
            )
          )
        )
        .toFuture()
        .futureValue

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .registerSealed[Notification]
        .register[Alert]
        .build

      val coll = db.withCodecRegistry(registry).getCollection[Alert]("alerts_unknown_discriminator")

      // Decoding must fail — no subtype matches _type: "AppNotification"
      val ex = coll.find(Filters.equal("_id", oid)).first().toFuture().failed.futureValue
      ex shouldBe a[Exception]

      db.drop().toFuture().futureValue
    }

  // ── Tests: Option[SealedTrait] ────────────────────────────────────────────────

  "Option[SealedTrait]: present value" should "encode and decode correctly" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .registerSealed[Notification]
      .register[OptionalAlert]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[OptionalAlert]("opt_alerts_present")

    val alert = OptionalAlert(
      new ObjectId(),
      "alice@example.com",
      Some(EmailNotification("alice@example.com", "Hello"))
    )
    coll.insertOne(alert).toFuture().futureValue

    val result = coll.find(Filters.equal("_id", alert._id)).first().toFuture().futureValue

    result.recipient shouldBe "alice@example.com"
    result.notification shouldBe defined
    result.notification.get shouldBe a[EmailNotification]
    result.notification.get.asInstanceOf[EmailNotification].subject shouldBe "Hello"

    database.drop().toFuture().futureValue
  }

  "Option[SealedTrait]: absent value (None)" should "encode and decode as None" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .ignoreNone
      .registerSealed[Notification]
      .register[OptionalAlert]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[OptionalAlert]("opt_alerts_absent")

    val alert = OptionalAlert(new ObjectId(), "bob@example.com", None)
    coll.insertOne(alert).toFuture().futureValue

    val result = coll.find(Filters.equal("_id", alert._id)).first().toFuture().futureValue

    result.recipient shouldBe "bob@example.com"
    result.notification shouldBe None

    database.drop().toFuture().futureValue
  }

  "Option[SealedTrait]: multiple subtypes" should
    "round-trip each subtype correctly when held inside an Option" in {
      assert(container.container.isRunning)

      val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
        .registerSealed[Notification]
        .register[OptionalAlert]
        .build

      val database = db.withCodecRegistry(registry)
      val coll     = database.getCollection[OptionalAlert]("opt_alerts_subtypes")

      val alerts = Seq(
        OptionalAlert(new ObjectId(), "u1", Some(EmailNotification("u1@e.com", "Sub1"))),
        OptionalAlert(new ObjectId(), "u2", Some(SmsNotification("+1234", "Hi"))),
        OptionalAlert(new ObjectId(), "u3", Some(PushNotification("tok1", "Push!")))
      )
      coll.insertMany(alerts).toFuture().futureValue

      val results = coll.find().toFuture().futureValue
      results should have size 3

      val byRecipient = results.map(a => a.recipient -> a.notification.get).toMap
      byRecipient("u1") shouldBe a[EmailNotification]
      byRecipient("u2") shouldBe a[SmsNotification]
      byRecipient("u3") shouldBe a[PushNotification]

      database.drop().toFuture().futureValue
    }

  // ── Tests: Map[String, SealedTrait] ──────────────────────────────────────────

  "Map[String, SealedTrait]: encode and decode" should "preserve all entries and their subtypes" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .registerSealed[Notification]
      .register[NotificationConfig]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[NotificationConfig]("notification_configs")

    val config = NotificationConfig(
      new ObjectId(),
      channels = Map(
        "email" -> EmailNotification("team@company.com", "Daily digest"),
        "sms"   -> SmsNotification("+19995551234", "Alert"),
        "push"  -> PushNotification("globalToken", "Broadcast")
      )
    )
    coll.insertOne(config).toFuture().futureValue

    val result = coll.find(Filters.equal("_id", config._id)).first().toFuture().futureValue

    result.channels should have size 3
    result.channels("email") shouldBe a[EmailNotification]
    result.channels("sms") shouldBe a[SmsNotification]
    result.channels("push") shouldBe a[PushNotification]
    result.channels("email").asInstanceOf[EmailNotification].subject shouldBe "Daily digest"

    database.drop().toFuture().futureValue
  }

  "Map[String, SealedTrait]: empty map" should "round-trip as an empty map" in {
    assert(container.container.isRunning)

    val registry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder
      .registerSealed[Notification]
      .register[NotificationConfig]
      .build

    val database = db.withCodecRegistry(registry)
    val coll     = database.getCollection[NotificationConfig]("notification_configs_empty")

    val config = NotificationConfig(new ObjectId(), channels = Map.empty)
    coll.insertOne(config).toFuture().futureValue

    val result = coll.find(Filters.equal("_id", config._id)).first().toFuture().futureValue
    result.channels shouldBe empty

    database.drop().toFuture().futureValue
  }

  override def afterAll(): Unit =
    container.stop()

end SealedTraitEvolutionIntegrationSpec
