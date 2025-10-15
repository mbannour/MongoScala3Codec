package examples

import org.bson.types.ObjectId
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig}
import org.mongodb.scala.MongoClient

/**
 * This file demonstrates the IDEAL API for custom field name transformations.
 * 
 * Status: NOT YET IMPLEMENTED
 * Priority: MEDIUM
 * 
 * This feature would eliminate boilerplate @BsonProperty annotations when
 * you need consistent naming conventions across all fields.
 */
object CustomFieldTransformationExample:

  // ====================
  // Example 1: Snake Case Transformation
  // ====================
  
  case class User(
    _id: ObjectId,
    firstName: String,      // Want: first_name
    lastName: String,       // Want: last_name
    emailAddress: String,   // Want: email_address
    phoneNumber: Option[String]  // Want: phone_number
  )
  
  def example1_snakeCase(): Unit =
    // NEW: Built-in field naming strategies
    given config: CodecConfig = CodecConfig(
      fieldNamingStrategy = FieldNamingStrategy.SnakeCase
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .register[User]
      .build
    
    // Expected BSON structure:
    // {
    //   "_id": ObjectId("..."),
    //   "first_name": "John",
    //   "last_name": "Doe",
    //   "email_address": "john@example.com",
    //   "phone_number": "+1234567890"
    // }
  
  // ====================
  // Example 2: Different Naming Strategies
  // ====================
  
  enum FieldNamingStrategy:
    case Identity       // firstName -> firstName (default)
    case SnakeCase      // firstName -> first_name
    case CamelCase      // first_name -> firstName
    case PascalCase     // firstName -> FirstName
    case KebabCase      // firstName -> first-name
    case UpperCase      // firstName -> FIRSTNAME
    case LowerCase      // firstName -> firstname
    case Custom(f: String => String)  // Custom transformation
  
  case class Product(
    _id: ObjectId,
    productName: String,
    productCode: String,
    unitPrice: Double
  )
  
  def example2_pascalCase(): Unit =
    given config: CodecConfig = CodecConfig(
      fieldNamingStrategy = FieldNamingStrategy.PascalCase
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .register[Product]
      .build
    
    // Expected BSON:
    // {
    //   "_id": ObjectId("..."),
    //   "ProductName": "Widget",
    //   "ProductCode": "WID-001",
    //   "UnitPrice": 19.99
    // }
  
  // ====================
  // Example 3: Custom Transformation Function
  // ====================
  
  case class Document(
    _id: ObjectId,
    createdAt: Long,
    updatedAt: Long,
    authorId: String
  )
  
  def example3_customFunction(): Unit =
    // Custom function: add prefix to all fields except _id
    def addPrefix(fieldName: String): String =
      if fieldName == "_id" then fieldName
      else s"doc_$fieldName"
    
    given config: CodecConfig = CodecConfig(
      fieldNamingStrategy = FieldNamingStrategy.Custom(addPrefix)
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .register[Document]
      .build
    
    // Expected BSON:
    // {
    //   "_id": ObjectId("..."),
    //   "doc_createdAt": 1234567890,
    //   "doc_updatedAt": 1234567890,
    //   "doc_authorId": "user123"
    // }
  
  // ====================
  // Example 4: Override with @BsonProperty
  // ====================
  
  import org.mongodb.scala.bson.annotations.BsonProperty
  
  case class Person(
    _id: ObjectId,
    firstName: String,                    // Uses strategy -> first_name
    lastName: String,                     // Uses strategy -> last_name
    @BsonProperty("email") emailAddress: String  // Override -> email (not email_address)
  )
  
  def example4_override(): Unit =
    given config: CodecConfig = CodecConfig(
      fieldNamingStrategy = FieldNamingStrategy.SnakeCase
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .register[Person]
      .build
    
    // Expected BSON:
    // {
    //   "_id": ObjectId("..."),
    //   "first_name": "Alice",
    //   "last_name": "Smith",
    //   "email": "alice@example.com"  // Overridden by annotation
    // }
  
  // ====================
  // Example 5: MongoDB-Friendly Snake Case
  // ====================
  
  case class BlogPost(
    _id: ObjectId,
    postTitle: String,
    authorName: String,
    publishedAt: Long,
    viewCount: Int,
    isPublished: Boolean
  )
  
  def example5_mongoFriendly(): Unit =
    // For MongoDB compatibility and readability
    given config: CodecConfig = CodecConfig(
      fieldNamingStrategy = FieldNamingStrategy.SnakeCase
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .register[BlogPost]
      .build
    
    // MongoDB query example:
    // db.blog_posts.find({ "author_name": "Alice", "is_published": true })
    
    // Easier to read in MongoDB shell than camelCase
  
  // ====================
  // Example 6: Fluent API for Naming Strategy
  // ====================
  
  case class Customer(
    _id: ObjectId,
    companyName: String,
    contactPerson: String,
    taxId: String
  )
  
  def example6_fluentApi(): Unit =
    // Alternative fluent API
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withSnakeCaseFields  // NEW: Convenience method
      .register[Customer]
      .build
    
    // Or with explicit strategy:
    val registry2 = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withFieldNaming(FieldNamingStrategy.KebabCase)  // NEW
      .register[Customer]
      .build
  
  // ====================
  // Example 7: Mixed Strategies (Per-Type)
  // ====================
  
  case class ApiRequest(
    _id: ObjectId,
    requestId: String,
    userId: String
  )
  
  case class DatabaseRecord(
    _id: ObjectId,
    record_id: String,
    user_id: String
  )
  
  def example7_perTypeStrategy(): Unit =
    // Different strategies for different types
    given apiConfig: CodecConfig = CodecConfig(
      fieldNamingStrategy = FieldNamingStrategy.CamelCase
    )
    
    given dbConfig: CodecConfig = CodecConfig(
      fieldNamingStrategy = FieldNamingStrategy.SnakeCase
    )
    
    // Would need a way to apply different configs per type
    // This is an advanced use case
  
  // ====================
  // Implementation Notes
  // ====================
  
  /**
   * To implement this feature:
   * 
   * 1. In CodecConfig.scala:
   *    - Add FieldNamingStrategy enum
   *    - Add fieldNamingStrategy: FieldNamingStrategy field
   *    - Implement built-in strategies (snake_case, camelCase, etc.)
   * 
   * 2. In CaseClassCodecGenerator.scala:
   *    - Apply fieldNamingStrategy when reading/writing field names
   *    - Check for @BsonProperty annotation first (override)
   *    - Fall back to naming strategy if no annotation
   * 
   * 3. In RegistryBuilder.scala:
   *    - Add convenience methods:
   *      - withSnakeCaseFields
   *      - withCamelCaseFields
   *      - withFieldNaming(strategy)
   * 
   * 4. Field Name Resolution Logic:
   *    ```scala
   *    def resolveFieldName(fieldName: String, annotation: Option[String], strategy: FieldNamingStrategy): String =
   *      annotation.getOrElse(strategy.transform(fieldName))
   *    ```
   * 
   * 5. Built-in Strategy Implementations:
   *    ```scala
   *    object FieldNamingStrategy:
   *      case object SnakeCase extends FieldNamingStrategy:
   *        def transform(name: String): String =
   *          name.replaceAll("([A-Z])", "_$1").toLowerCase.dropWhile(_ == '_')
   *      
   *      case object CamelCase extends FieldNamingStrategy:
   *        def transform(name: String): String =
   *          name.split('_').zipWithIndex.map:
   *            case (word, 0) => word.toLowerCase
   *            case (word, _) => word.capitalize
   *          .mkString
   *    ```
   * 
   * 6. Testing:
   *    - Test each naming strategy
   *    - Test @BsonProperty override behavior
   *    - Test with nested case classes
   *    - Test roundtrip encoding/decoding
   *    - Test MongoDB query compatibility
   */
  
  /**
   * Benefits of this feature:
   * 
   * 1. DRY Principle - No repetitive @BsonProperty annotations
   * 2. Consistency - Enforce naming convention across entire codebase
   * 3. Maintainability - Change strategy in one place
   * 4. MongoDB Integration - Snake case is common in MongoDB
   * 5. API Compatibility - Match existing database schemas
   * 6. Flexibility - Custom transformation functions for special cases
   */
