package io.github.mbannour.mongo.codecs

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import io.github.mbannour.fields.MongoPath
import io.github.mbannour.mongo.codecs.RegistryBuilder.* 
import org.bson.types.ObjectId
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.*
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}


class SealedTraitIntegrationSpec
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

  private def createDatabaseWithRegistry(registry: CodecRegistry): MongoDatabase =
    MongoClient(mongoUri)
      .getDatabase("sealed_trait_test_db")
      .withCodecRegistry(registry)
  
  sealed trait PaymentStatus
  case class Pending() extends PaymentStatus
  case class Processing(transactionId: String) extends PaymentStatus
  case class Completed(amount: Double, timestamp: Long) extends PaymentStatus
  case class Failed(reason: String) extends PaymentStatus

  case class Payment(
      _id: ObjectId,
      orderId: String,
      customerEmail: String,
      status: PaymentStatus
  )

  sealed trait ShippingAddress
  case class DomesticAddress(street: String, city: String, state: String, zipCode: Int) extends ShippingAddress
  case class InternationalAddress(street: String, city: String, country: String, postalCode: String) extends ShippingAddress
  case class PickupLocation(storeName: String, storeId: String) extends ShippingAddress

  case class Shipment(
      _id: ObjectId,
      trackingNumber: String,
      destination: ShippingAddress,
      alternateAddress: Option[ShippingAddress]
  )

  sealed trait OrderEvent
  case class OrderCreated(timestamp: Long, userId: String) extends OrderEvent
  case class OrderPaid(timestamp: Long, amount: Double) extends OrderEvent
  case class OrderShipped(timestamp: Long, carrier: String) extends OrderEvent
  case class OrderDelivered(timestamp: Long, signature: String) extends OrderEvent
  case class OrderCancelled(timestamp: Long, reason: String) extends OrderEvent

  case class OrderHistory(
      _id: ObjectId,
      orderId: String,
      events: List[OrderEvent]
  )
  
  "Sealed trait codec" should "insert and retrieve simple sealed trait documents" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerSealed[PaymentStatus]
      .register[Payment]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Payment] = database.getCollection("payments")

    val payment = Payment(
      _id = new ObjectId(),
      orderId = "ORDER-001",
      customerEmail = "user@example.com",
      status = Completed(99.99, System.currentTimeMillis())
    )

    collection.insertOne(payment).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Payment](_._id), payment._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe payment
    retrieved.status shouldBe a[Completed]
    retrieved.status.asInstanceOf[Completed].amount shouldBe 99.99

    database.drop().toFuture().futureValue
  }

  it should "handle all sealed trait subtypes" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerSealed[PaymentStatus]
      .register[Payment]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Payment] = database.getCollection("all_statuses")

    val payments = List(
      Payment(new ObjectId(), "ORD-1", "user1@test.com", Pending()),
      Payment(new ObjectId(), "ORD-2", "user2@test.com", Processing("TXN-123")),
      Payment(new ObjectId(), "ORD-3", "user3@test.com", Completed(150.0, 1000000L)),
      Payment(new ObjectId(), "ORD-4", "user4@test.com", Failed("Card declined"))
    )

    collection.insertMany(payments).toFuture().futureValue

    val retrieved = collection.find().toFuture().futureValue

    retrieved should have size 4

    val statusTypes = retrieved.map(_.status.getClass.getSimpleName).toSet
    statusTypes should contain allOf ("Pending", "Processing", "Completed", "Failed")

    database.drop().toFuture().futureValue
  }

  it should "query by sealed trait subtype" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerSealed[PaymentStatus]
      .register[Payment]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Payment] = database.getCollection("query_test")

    val payments = List(
      Payment(new ObjectId(), "ORD-Q1", "a@test.com", Completed(100.0, 1000L)),
      Payment(new ObjectId(), "ORD-Q2", "b@test.com", Processing("TXN-A")),
      Payment(new ObjectId(), "ORD-Q3", "c@test.com", Completed(200.0, 2000L)),
      Payment(new ObjectId(), "ORD-Q4", "d@test.com", Failed("Error")),
      Payment(new ObjectId(), "ORD-Q5", "e@test.com", Completed(300.0, 3000L))
    )

    collection.insertMany(payments).toFuture().futureValue
    
    val completedPayments = collection
      .find(Filters.equal("status._type", "Completed"))
      .toFuture()
      .futureValue

    completedPayments should have size 3
    completedPayments.foreach(_.status shouldBe a[Completed])

    database.drop().toFuture().futureValue
  }

  it should "update sealed trait fields" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerSealed[PaymentStatus]
      .register[Payment]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Payment] = database.getCollection("updates")

    val payment = Payment(
      _id = new ObjectId(),
      orderId = "ORD-UPD",
      customerEmail = "update@test.com",
      status = Pending()
    )

    collection.insertOne(payment).toFuture().futureValue

    // Update to Processing
    val updatedPayment = payment.copy(status = Processing("TXN-UPD-001"))
    collection
      .replaceOne(
        Filters.equal(MongoPath.of[Payment](_._id), payment._id),
        updatedPayment
      )
      .toFuture()
      .futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Payment](_._id), payment._id))
      .first()
      .toFuture()
      .futureValue

    retrieved.status shouldBe a[Processing]
    retrieved.status.asInstanceOf[Processing].transactionId shouldBe "TXN-UPD-001"

    database.drop().toFuture().futureValue
  }

  // ========== Nested Sealed Traits ==========

  it should "handle nested sealed trait structures" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerSealed[ShippingAddress]
      .register[Shipment]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Shipment] = database.getCollection("shipments")

    val shipment = Shipment(
      _id = new ObjectId(),
      trackingNumber = "TRACK-123",
      destination = DomesticAddress("123 Main St", "NYC", "NY", 10001),
      alternateAddress = Some(PickupLocation("Store #5", "STORE-005"))
    )

    collection.insertOne(shipment).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Shipment](_._id), shipment._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe shipment
    retrieved.destination shouldBe a[DomesticAddress]
    retrieved.alternateAddress.get shouldBe a[PickupLocation]

    database.drop().toFuture().futureValue
  }

  it should "handle optional sealed trait fields" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .ignoreNone
      .registerSealed[ShippingAddress]
      .register[Shipment]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Shipment] = database.getCollection("optional_sealed")

    // Test with Some
    val shipment1 = Shipment(
      _id = new ObjectId(),
      trackingNumber = "OPT-001",
      destination = InternationalAddress("10 Rue de Paris", "Paris", "France", "75001"),
      alternateAddress = Some(DomesticAddress("456 Backup St", "LA", "CA", 90001))
    )

    collection.insertOne(shipment1).toFuture().futureValue

    val retrieved1 = collection
      .find(Filters.equal(MongoPath.of[Shipment](_._id), shipment1._id))
      .first()
      .toFuture()
      .futureValue

    retrieved1 shouldBe shipment1
    
    val shipment2 = Shipment(
      _id = new ObjectId(),
      trackingNumber = "OPT-002",
      destination = PickupLocation("Main Store", "MAIN-01"),
      alternateAddress = None
    )

    collection.insertOne(shipment2).toFuture().futureValue

    val retrieved2 = collection
      .find(Filters.equal(MongoPath.of[Shipment](_._id), shipment2._id))
      .first()
      .toFuture()
      .futureValue

    retrieved2 shouldBe shipment2
    retrieved2.alternateAddress shouldBe None

    database.drop().toFuture().futureValue
  }

  
  it should "handle collections of sealed traits" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerSealed[OrderEvent]
      .register[OrderHistory]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[OrderHistory] = database.getCollection("order_histories")

    val history = OrderHistory(
      _id = new ObjectId(),
      orderId = "ORD-HIST-001",
      events = List(
        OrderCreated(1000L, "user123"),
        OrderPaid(2000L, 299.99),
        OrderShipped(3000L, "FedEx"),
        OrderDelivered(4000L, "John Doe")
      )
    )

    collection.insertOne(history).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[OrderHistory](_._id), history._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe history
    retrieved.events should have size 4
    retrieved.events(0) shouldBe an[OrderCreated]
    retrieved.events(1) shouldBe an[OrderPaid]
    retrieved.events(2) shouldBe an[OrderShipped]
    retrieved.events(3) shouldBe an[OrderDelivered]
  }

  it should "handle mixed event types in collections" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerSealed[OrderEvent]
      .register[OrderHistory]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[OrderHistory] = database.getCollection("mixed_events")

    val history = OrderHistory(
      _id = new ObjectId(),
      orderId = "ORD-MIX",
      events = List(
        OrderCreated(1000L, "userX"),
        OrderPaid(2000L, 99.99),
        OrderCancelled(2500L, "Customer requested"),
        OrderCreated(3000L, "userX"), // Re-ordered
        OrderPaid(4000L, 99.99),
        OrderShipped(5000L, "UPS"),
        OrderDelivered(6000L, "Jane Smith")
      )
    )

    collection.insertOne(history).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[OrderHistory](_._id), history._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe history
    retrieved.events should have size 7
    
    retrieved.events.count(_.isInstanceOf[OrderCreated]) shouldBe 2
    retrieved.events.count(_.isInstanceOf[OrderPaid]) shouldBe 2
    retrieved.events.count(_.isInstanceOf[OrderCancelled]) shouldBe 1
    retrieved.events.count(_.isInstanceOf[OrderShipped]) shouldBe 1
    retrieved.events.count(_.isInstanceOf[OrderDelivered]) shouldBe 1

    database.drop().toFuture().futureValue
  }

  
  it should "handle bulk inserts with sealed traits" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerSealed[PaymentStatus]
      .register[Payment]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Payment] = database.getCollection("bulk_sealed")

    val payments = (1 to 100).map { i =>
      val status = i % 4 match
        case 0 => Pending()
        case 1 => Processing(s"TXN-$i")
        case 2 => Completed(i * 10.0, i * 1000L)
        case 3 => Failed(s"Error $i")

      Payment(new ObjectId(), s"ORD-BULK-$i", s"user$i@test.com", status)
    }

    collection.insertMany(payments).toFuture().futureValue

    val count = collection.countDocuments().toFuture().futureValue
    count shouldBe 100

    val completedCount = collection
      .countDocuments(Filters.equal("status._type", "Completed"))
      .toFuture()
      .futureValue

    completedCount shouldBe 25

    database.drop().toFuture().futureValue
  }

  
  it should "work with custom discriminator field" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .configure(_.withDiscriminatorField("_class"))
      .registerSealed[PaymentStatus]
      .register[Payment]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Payment] = database.getCollection("custom_discriminator")

    val payment = Payment(
      _id = new ObjectId(),
      orderId = "CUSTOM-DISC",
      customerEmail = "custom@test.com",
      status = Processing("TXN-CUSTOM")
    )

    collection.insertOne(payment).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Payment](_._id), payment._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe payment


    val docCollection = database.getCollection[Document]("custom_discriminator")
    val doc = docCollection
      .find(Filters.equal("_id", payment._id))
      .first()
      .toFuture()
      .futureValue

    val statusDoc = doc.get("status", classOf[org.bson.Document])
    statusDoc.getString("_class") shouldBe "Processing"

    database.drop().toFuture().futureValue
  }

  
  it should "register multiple sealed traits with registerSealedAll" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .registerSealedAll[(PaymentStatus, ShippingAddress, OrderEvent)]
      .registerAll[(Payment, Shipment, OrderHistory)]
      .build

    val database = createDatabaseWithRegistry(registry)
    
    val paymentCollection: MongoCollection[Payment] = database.getCollection("batch_payments")
    val payment = Payment(
      _id = new ObjectId(),
      orderId = "BATCH-001",
      customerEmail = "batch@test.com",
      status = Completed(199.99, System.currentTimeMillis())
    )
    paymentCollection.insertOne(payment).toFuture().futureValue
    val retrievedPayment = paymentCollection
      .find(Filters.equal(MongoPath.of[Payment](_._id), payment._id))
      .first()
      .toFuture()
      .futureValue
    retrievedPayment shouldBe payment
    
    val shipmentCollection: MongoCollection[Shipment] = database.getCollection("batch_shipments")
    val shipment = Shipment(
      _id = new ObjectId(),
      trackingNumber = "BATCH-TRACK-001",
      destination = InternationalAddress("123 Test St", "Paris", "France", "75001"),
      alternateAddress = Some(PickupLocation("Test Store", "STORE-001"))
    )
    shipmentCollection.insertOne(shipment).toFuture().futureValue
    val retrievedShipment = shipmentCollection
      .find(Filters.equal(MongoPath.of[Shipment](_._id), shipment._id))
      .first()
      .toFuture()
      .futureValue
    retrievedShipment shouldBe shipment
    
    val historyCollection: MongoCollection[OrderHistory] = database.getCollection("batch_histories")
    val history = OrderHistory(
      _id = new ObjectId(),
      orderId = "BATCH-ORDER-001",
      events = List(
        OrderCreated(1000L, "batch-user"),
        OrderPaid(2000L, 199.99),
        OrderShipped(3000L, "DHL")
      )
    )
    historyCollection.insertOne(history).toFuture().futureValue
    val retrievedHistory = historyCollection
      .find(Filters.equal(MongoPath.of[OrderHistory](_._id), history._id))
      .first()
      .toFuture()
      .futureValue
    retrievedHistory shouldBe history

    database.drop().toFuture().futureValue
  }

  it should "work with registerSealedAll and custom configuration" in {
    val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      .newBuilder
      .ignoreNone
      .configure(_.withDiscriminatorField("_class"))
      .registerSealedAll[(PaymentStatus, ShippingAddress)]
      .register[Payment]
      .register[Shipment]
      .build

    val database = createDatabaseWithRegistry(registry)
    val collection: MongoCollection[Shipment] = database.getCollection("batch_config_test")

    val shipment = Shipment(
      _id = new ObjectId(),
      trackingNumber = "CONFIG-BATCH-001",
      destination = DomesticAddress("456 Test Ave", "NYC", "NY", 10001),
      alternateAddress = None  // Should be omitted due to ignoreNone
    )

    collection.insertOne(shipment).toFuture().futureValue

    val retrieved = collection
      .find(Filters.equal(MongoPath.of[Shipment](_._id), shipment._id))
      .first()
      .toFuture()
      .futureValue

    retrieved shouldBe shipment

    val docCollection = database.getCollection[Document]("batch_config_test")
    val doc = docCollection
      .find(Filters.equal("_id", shipment._id))
      .first()
      .toFuture()
      .futureValue

    val destDoc = doc.get("destination", classOf[org.bson.Document])
    destDoc.getString("_class") shouldBe "DomesticAddress"

    database.drop().toFuture().futureValue
  }

  override def afterAll(): Unit =
    container.stop()

end SealedTraitIntegrationSpec
