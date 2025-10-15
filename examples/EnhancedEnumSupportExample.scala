package examples

import org.bson.types.ObjectId
import io.github.mbannour.mongo.codecs.{RegistryBuilder, CodecConfig}
import org.mongodb.scala.MongoClient

/**
 * This file demonstrates the IDEAL API for enhanced Scala 3 enum support.
 * 
 * Status: PARTIALLY IMPLEMENTED
 * Current: Plain enums work (Low, Medium, High)
 * Missing: Parameterized enums (ADT-style)
 * Priority: MEDIUM
 * 
 * Scala 3 enums can be much more than simple enumerations - they can be
 * full algebraic data types (ADTs). This feature would add support for that.
 */
object EnhancedEnumSupportExample:

  // ====================
  // Example 1: Current Support (Plain Enums) ✅
  // ====================
  
  enum Priority:
    case Low, Medium, High
  
  case class Task(
    _id: ObjectId,
    title: String,
    priority: Priority
  )
  
  def example1_currentSupport(): Unit =
    // This ALREADY WORKS in current version
    import io.github.mbannour.mongo.codecs.EnumValueCodecProvider
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .addProvider(EnumValueCodecProvider[Priority](_.ordinal, Priority.fromOrdinal))
      .register[Task]
      .build
    
    // Expected BSON:
    // { "_id": ObjectId("..."), "title": "Fix bug", "priority": 1 }
  
  // ====================
  // Example 2: Parameterized Enum (ADT Style) ❌
  // ====================
  
  enum Result:
    case Success(value: String)
    case Failure(error: String, code: Int)
    case Pending
  
  case class Operation(
    _id: ObjectId,
    name: String,
    result: Result  // NOT SUPPORTED - parameterized enum
  )
  
  def example2_parameterizedEnum(): Unit =
    // This should work like sealed traits
    given config: CodecConfig = CodecConfig(
      discriminatorField = "_type"
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerEnum[Result]  // NEW: Handle ADT-style enums
      .register[Operation]
      .build
    
    // Usage:
    val op1 = Operation(new ObjectId(), "compute", Result.Success("42"))
    val op2 = Operation(new ObjectId(), "validate", Result.Failure("Invalid input", 400))
    val op3 = Operation(new ObjectId(), "process", Result.Pending)
    
    // Expected BSON:
    // {
    //   "result": {
    //     "_type": "Success",
    //     "value": "42"
    //   }
    // }
  
  // ====================
  // Example 3: Enum with Custom Fields ❌
  // ====================
  
  enum HttpStatus(val code: Int, val message: String):
    case OK extends HttpStatus(200, "OK")
    case NotFound extends HttpStatus(404, "Not Found")
    case InternalError extends HttpStatus(500, "Internal Server Error")
  
  case class ApiResponse(
    _id: ObjectId,
    status: HttpStatus,
    body: Option[String]
  )
  
  def example3_enumWithFields(): Unit =
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .registerEnum[HttpStatus]  // Handle enum with custom fields
      .register[ApiResponse]
      .build
    
    // Expected BSON (store code and message):
    // {
    //   "status": {
    //     "code": 404,
    //     "message": "Not Found"
    //   }
    // }
    
    // Or simpler (just the case name):
    // { "status": "NotFound" }
  
  // ====================
  // Example 4: Enum with Methods ❌
  // ====================
  
  enum Color:
    case Red, Green, Blue
    
    def toHex: String = this match
      case Red => "#FF0000"
      case Green => "#00FF00"
      case Blue => "#0000FF"
  
  case class Theme(
    _id: ObjectId,
    name: String,
    primaryColor: Color
  )
  
  def example4_enumWithMethods(): Unit =
    // Methods are just compile-time behavior, doesn't affect encoding
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .registerEnum[Color]  // Should work like plain enum
      .register[Theme]
      .build
    
    // Expected BSON:
    // { "primaryColor": "Red" }
  
  // ====================
  // Example 5: Complex ADT Enum ❌
  // ====================
  
  enum JsonValue:
    case Null
    case Bool(value: Boolean)
    case Num(value: Double)
    case Str(value: String)
    case Arr(values: List[JsonValue])
    case Obj(fields: Map[String, JsonValue])
  
  case class JsonDocument(
    _id: ObjectId,
    content: JsonValue
  )
  
  def example5_complexEnum(): Unit =
    given config: CodecConfig = CodecConfig(
      discriminatorField = "_type"
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerEnum[JsonValue]  // Recursive enum
      .register[JsonDocument]
      .build
    
    // Usage:
    val json = JsonValue.Obj(Map(
      "name" -> JsonValue.Str("Alice"),
      "age" -> JsonValue.Num(30),
      "active" -> JsonValue.Bool(true),
      "tags" -> JsonValue.Arr(List(
        JsonValue.Str("scala"),
        JsonValue.Str("mongodb")
      ))
    ))
    
    // This should serialize naturally to BSON
  
  // ====================
  // Example 6: Enum in Collections ❌
  // ====================
  
  enum Notification:
    case Email(to: String, subject: String)
    case Sms(phone: String, message: String)
    case Push(deviceId: String, title: String)
  
  case class User(
    _id: ObjectId,
    username: String,
    notifications: List[Notification]  // Collection of enum values
  )
  
  def example6_enumInCollections(): Unit =
    given config: CodecConfig = CodecConfig(
      discriminatorField = "_type"
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerEnum[Notification]
      .register[User]
      .build
    
    val user = User(
      new ObjectId(),
      "alice",
      List(
        Notification.Email("alice@example.com", "Welcome"),
        Notification.Sms("+1234567890", "Hello"),
        Notification.Push("device123", "You have a message")
      )
    )
  
  // ====================
  // Example 7: Enum Configuration ❌
  // ====================
  
  enum LogLevel:
    case Debug, Info, Warn, Error
  
  case class LogEntry(
    _id: ObjectId,
    message: String,
    level: LogLevel
  )
  
  def example7_enumConfig(): Unit =
    enum EnumStrategy:
      case Ordinal     // Store as Int (0, 1, 2, 3)
      case Name        // Store as String ("Debug", "Info")
      case LowerCase   // Store as String ("debug", "info")
      case Custom(f: LogLevel => String)
    
    given config: CodecConfig = CodecConfig(
      enumStrategy = EnumStrategy.LowerCase  // NEW
    )
    
    val registry = RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .withConfig(config)
      .registerEnum[LogLevel]
      .register[LogEntry]
      .build
    
    // Expected BSON:
    // { "level": "debug" }  // lowercase
  
  // ====================
  // Implementation Notes
  // ====================
  
  /**
   * To implement enhanced enum support:
   * 
   * 1. Detect Enum Types at Compile Time:
   *    ```scala
   *    import scala.quoted.*
   *    
   *    inline def isEnum[T]: Boolean = ${ isEnumImpl[T] }
   *    
   *    def isEnumImpl[T: Type](using Quotes): Expr[Boolean] =
   *      import quotes.reflect.*
   *      val tpe = TypeRepr.of[T]
   *      Expr(tpe.typeSymbol.flags.is(Flags.Enum))
   *    ```
   * 
   * 2. Classify Enum Types:
   *    - Plain enum (Low, Medium, High) - already supported
   *    - Parameterized enum (Success(value), Failure(error))
   *    - Enum with fields (HttpStatus(code, message))
   * 
   * 3. For Parameterized Enums:
   *    - Treat like sealed traits
   *    - Each case is a separate type
   *    - Use discriminator field
   * 
   * 4. Add registerEnum Method:
   *    ```scala
   *    extension (builder: RegistryBuilder)
   *      def registerEnum[T](using Mirror.SumOf[T]): RegistryBuilder =
   *        // Detect enum cases
   *        // Generate appropriate codec
   *        // Register all cases
   *    ```
   * 
   * 5. Configuration in CodecConfig:
   *    ```scala
   *    enum EnumStrategy:
   *      case Ordinal
   *      case Name
   *      case LowerCase
   *      case Custom(f: Any => String)
   *    
   *    case class CodecConfig(
   *      // ... existing fields
   *      enumStrategy: EnumStrategy = EnumStrategy.Name
   *    )
   *    ```
   * 
   * 6. Testing:
   *    - Test plain enums (already working)
   *    - Test parameterized enums
   *    - Test enums with fields
   *    - Test enums in collections
   *    - Test nested enums
   *    - Test enum strategy configurations
   */
  
  /**
   * Relationship to Sealed Traits:
   * 
   * In Scala 3, enums are implemented as sealed traits under the hood:
   * 
   * ```scala
   * enum Result:
   *   case Success(value: String)
   *   case Failure(error: String)
   * ```
   * 
   * Is equivalent to:
   * 
   * ```scala
   * sealed trait Result
   * object Result:
   *   case class Success(value: String) extends Result
   *   case class Failure(error: String) extends Result
   * ```
   * 
   * Therefore, enhanced enum support builds on sealed trait support.
   * Once polymorphic sealed traits are implemented, parameterized enums
   * should work with minimal additional code.
   */
  
  /**
   * Migration Path:
   * 
   * For existing code using plain enums:
   * ```scala
   * // Before:
   * val provider = EnumValueCodecProvider[Priority](_.ordinal, Priority.fromOrdinal)
   * val registry = RegistryBuilder.addProvider(provider).build
   * 
   * // After (should be compatible):
   * val registry = RegistryBuilder.registerEnum[Priority].build
   * ```
   * 
   * The new API should be simpler while maintaining backward compatibility.
   */

end EnhancedEnumSupportExample
