package examples

import org.bson.types.ObjectId
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig}
import org.mongodb.scala.MongoClient

/**
 * This file demonstrates the IDEAL API for polymorphic sealed trait support.
 * 
 * Status: NOT YET IMPLEMENTED
 * Priority: HIGH
 * 
 * This is how the library SHOULD work once sealed trait fields are fully supported.
 * Use this as a reference when implementing the feature.
 */
object PolymorphicSealedTraitExample:

  // ====================
  // Example 1: Simple Sealed Trait Hierarchy
  // ====================
  
  sealed trait PaymentStatus
  case class Pending(timestamp: Long) extends PaymentStatus
  case class Completed(timestamp: Long, transactionId: String) extends PaymentStatus
  case class Failed(timestamp: Long, reason: String) extends PaymentStatus
  
  // This should work - field typed as sealed trait
  case class Payment(
    _id: ObjectId,
    amount: Double,
    currency: String,
    status: PaymentStatus  // Polymorphic field
  )
  
  def example1(): Unit =
    given config: CodecConfig = CodecConfig(
      discriminatorField = "_type"  // Already exists
    )
    
    // NEW METHOD: registerSealed should register all children automatically
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerSealed[PaymentStatus]  // Registers Pending, Completed, Failed
      .register[Payment]
      .build
    
    // Usage:
    val payment1 = Payment(new ObjectId(), 100.0, "USD", Pending(System.currentTimeMillis()))
    val payment2 = Payment(new ObjectId(), 200.0, "EUR", Completed(System.currentTimeMillis(), "TXN-123"))
    val payment3 = Payment(new ObjectId(), 50.0, "GBP", Failed(System.currentTimeMillis(), "Insufficient funds"))
    
    // Expected BSON structure:
    // {
    //   "_id": ObjectId("..."),
    //   "amount": 100.0,
    //   "currency": "USD",
    //   "status": {
    //     "_type": "Pending",
    //     "timestamp": 1234567890
    //   }
    // }
  
  // ====================
  // Example 2: Sealed Trait with Case Objects
  // ====================
  
  sealed trait Permission
  case object Read extends Permission
  case object Write extends Permission
  case object Admin extends Permission
  
  case class User(
    _id: ObjectId,
    name: String,
    email: String,
    permission: Permission  // Polymorphic field with case objects
  )
  
  def example2(): Unit =
    given config: CodecConfig = CodecConfig(
      discriminatorField = "_type"
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerSealed[Permission]  // Handles case objects
      .register[User]
      .build
    
    // Usage:
    val user1 = User(new ObjectId(), "Alice", "alice@example.com", Read)
    val user2 = User(new ObjectId(), "Bob", "bob@example.com", Admin)
    
    // Expected BSON structure (case object as simple string):
    // {
    //   "_id": ObjectId("..."),
    //   "name": "Alice",
    //   "email": "alice@example.com",
    //   "permission": "Read"  // or { "_type": "Read" } depending on strategy
    // }
  
  // ====================
  // Example 3: Collections of Sealed Traits
  // ====================
  
  sealed trait Event
  case class Login(userId: String, timestamp: Long) extends Event
  case class Logout(userId: String, timestamp: Long) extends Event
  case class Purchase(userId: String, amount: Double, timestamp: Long) extends Event
  
  case class UserActivity(
    _id: ObjectId,
    userId: String,
    events: List[Event]  // Collection of polymorphic values
  )
  
  def example3(): Unit =
    given config: CodecConfig = CodecConfig(
      discriminatorField = "_type"
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerSealed[Event]
      .register[UserActivity]
      .build
    
    // Usage:
    val activity = UserActivity(
      new ObjectId(),
      "user123",
      List(
        Login("user123", 1000),
        Purchase("user123", 99.99, 2000),
        Logout("user123", 3000)
      )
    )
    
    // Expected BSON structure:
    // {
    //   "_id": ObjectId("..."),
    //   "userId": "user123",
    //   "events": [
    //     { "_type": "Login", "userId": "user123", "timestamp": 1000 },
    //     { "_type": "Purchase", "userId": "user123", "amount": 99.99, "timestamp": 2000 },
    //     { "_type": "Logout", "userId": "user123", "timestamp": 3000 }
    //   ]
    // }
  
  // ====================
  // Example 4: Nested Sealed Traits
  // ====================
  
  sealed trait Address
  case class USAddress(street: String, city: String, state: String, zip: String) extends Address
  case class InternationalAddress(street: String, city: String, country: String, postalCode: String) extends Address
  
  sealed trait ContactMethod
  case class EmailContact(email: String) extends ContactMethod
  case class PhoneContact(phone: String, countryCode: String) extends ContactMethod
  
  case class Customer(
    _id: ObjectId,
    name: String,
    address: Address,  // Nested sealed trait
    preferredContact: ContactMethod  // Another sealed trait
  )
  
  def example4(): Unit =
    given config: CodecConfig = CodecConfig(
      discriminatorField = "_type"
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerSealed[Address]
      .registerSealed[ContactMethod]
      .register[Customer]
      .build
    
    // Usage:
    val customer = Customer(
      new ObjectId(),
      "John Doe",
      USAddress("123 Main St", "New York", "NY", "10001"),
      EmailContact("john@example.com")
    )
  
  // ====================
  // Example 5: Mixed Sealed Hierarchy (Case Classes + Case Objects)
  // ====================
  
  sealed trait Status
  case object Active extends Status
  case object Inactive extends Status
  case class Custom(reason: String, code: Int) extends Status
  
  case class Account(
    _id: ObjectId,
    username: String,
    status: Status  // Can be case object OR case class
  )
  
  def example5(): Unit =
    given config: CodecConfig = CodecConfig(
      discriminatorField = "_type"
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerSealed[Status]
      .register[Account]
      .build
    
    // Usage:
    val account1 = Account(new ObjectId(), "alice", Active)
    val account2 = Account(new ObjectId(), "bob", Inactive)
    val account3 = Account(new ObjectId(), "charlie", Custom("Suspended by admin", 403))
    
    // Expected BSON:
    // account1: { "status": "Active" }
    // account3: { "status": { "_type": "Custom", "reason": "...", "code": 403 } }
  
  // ====================
  // Example 6: Configurable Discriminator Strategy
  // ====================
  
  enum DiscriminatorStrategy:
    case ClassName        // "Pending", "Completed"
    case FullClassName    // "examples.Pending", "examples.Completed"
    case SimpleName       // "pending", "completed" (lowercase)
    case Custom(f: Class[?] => String)
  
  sealed trait OrderStatus
  case class Placed(timestamp: Long) extends OrderStatus
  case class Shipped(trackingNumber: String) extends OrderStatus
  case class Delivered(timestamp: Long, signature: String) extends OrderStatus
  
  case class Order(
    _id: ObjectId,
    orderNumber: String,
    status: OrderStatus
  )
  
  def example6(): Unit =
    // Different discriminator strategies
    given config: CodecConfig = CodecConfig(
      discriminatorField = "__t",  // Custom field name
      discriminatorStrategy = DiscriminatorStrategy.SimpleName  // NEW: lowercase names
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerSealed[OrderStatus]
      .register[Order]
      .build
    
    // Expected BSON with SimpleName strategy:
    // {
    //   "status": {
    //     "__t": "placed",  // lowercase
    //     "timestamp": 1234567890
    //   }
    // }

  // ====================
  // Implementation Notes
  // ====================
  
  /**
   * To implement this feature, the following changes are needed:
   * 
   * 1. In CodecConfig.scala:
   *    - Add DiscriminatorStrategy enum
   *    - Update CodecConfig case class to include discriminatorStrategy
   * 
   * 2. In RegistryBuilder.scala:
   *    - Add registerSealed[T] method
   *    - Detect all sealed trait children at compile time
   *    - Register codecs for all children automatically
   * 
   * 3. In CaseClassCodecGenerator.scala:
   *    - Detect when a field is a sealed trait
   *    - Generate encoding logic that writes discriminator field
   *    - Generate decoding logic that reads discriminator and dispatches
   * 
   * 4. In CaseClassMapper.scala:
   *    - Enhanced sealed trait detection
   *    - Support for case objects
   *    - Build discriminator -> Class mapping
   * 
   * 5. Testing:
   *    - Add unit tests for registerSealed
   *    - Add integration tests for polymorphic fields
   *    - Test case object encoding/decoding
   *    - Test collections of sealed traits
   *    - Test nested sealed traits
   */
