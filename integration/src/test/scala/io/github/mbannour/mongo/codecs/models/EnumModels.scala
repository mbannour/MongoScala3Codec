package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.bson.macros.BsonEnum
import io.github.mbannour.mongo.codecs.{CodecConfig, EnumValueCodecProvider, RegistryBuilder}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient

/** Enum models for comprehensive integration testing of enum support. */

// 1. Simple string-based enum (using name/toString)
enum Color:
  case Red, Green, Blue, Yellow

// 2. Ordinal-based enum
enum Level:
  case Beginner, Intermediate, Advanced, Expert

// 3. Enum with custom field (code)
enum StatusCode(val code: Int):
  case Success extends StatusCode(200)
  case Created extends StatusCode(201)
  case BadRequest extends StatusCode(400)
  case NotFound extends StatusCode(404)
  case ServerError extends StatusCode(500)

// 4. Enum with custom field (value)
enum Priority(val value: String, val weight: Int):
  case Low extends Priority("low", 1)
  case Medium extends Priority("medium", 5)
  case High extends Priority("high", 10)
  case Critical extends Priority("critical", 20)

// 5. Enum with custom field (id)
enum Category(val id: String):
  case Electronics extends Category("ELEC")
  case Clothing extends Category("CLTH")
  case Food extends Category("FOOD")
  case Books extends Category("BOOK")

// Case classes using different enum configurations

// Test case 1: Simple string enum
case class ColoredItem(
    _id: ObjectId,
    name: String,
    color: Color
)

// Test case 2: Ordinal enum
case class UserProfile(
    _id: ObjectId,
    username: String,
    level: Level
)

// Test case 3: Enum with code field (automatic detection)
case class ApiResponse(
    _id: ObjectId,
    message: String,
    status: StatusCode
)

// Test case 4: Enum with @BsonEnum annotation specifying custom field
case class TaskItem(
    _id: ObjectId,
    title: String,
    @BsonEnum(nameField = "value") priority: Priority
)

// Test case 5: Enum with @BsonEnum annotation using id field
case class Product(
    _id: ObjectId,
    name: String,
    @BsonEnum(nameField = "id") category: Category,
    price: Double
)

// Test case 6: Optional enum field
case class Configuration(
    _id: ObjectId,
    name: String,
    color: Option[Color],
    level: Option[Level]
)

// Test case 7: Collection of enums
case class Palette(
    _id: ObjectId,
    name: String,
    colors: List[Color]
)

// Test case 8: Multiple enums in one case class
case class GameCharacter(
    _id: ObjectId,
    name: String,
    level: Level,
    favoriteColor: Color,
    @BsonEnum(nameField = "value") priority: Priority
)

// Test case 9: Nested case class with enum
case class ItemWithStatus(
    name: String,
    status: StatusCode
)

case class Order(
    _id: ObjectId,
    orderId: String,
    items: List[ItemWithStatus],
    @BsonEnum(nameField = "value") priority: Priority
)

// Test case 10: Map with enum values
case class ColorMapping(
    _id: ObjectId,
    name: String,
    colorAssignments: Map[String, Color]
)

// Registry configurations for different enum types
object EnumModels:

  // String-based codec providers
  val colorProvider: CodecProvider = EnumValueCodecProvider.forStringEnum[Color]
  val levelProvider: CodecProvider = EnumValueCodecProvider.forStringEnum[Level]
  val statusCodeProvider: CodecProvider = EnumValueCodecProvider.forStringEnum[StatusCode]

  // Custom field-based codec providers
  import org.bson.codecs.{Codec, StringCodec}
  given Codec[String] = new StringCodec()

  val priorityProvider: CodecProvider = EnumValueCodecProvider[Priority, String](
    _.value,
    str =>
      Priority.values
        .find(_.value == str)
        .getOrElse(
          throw new IllegalArgumentException(s"Invalid priority value: $str")
        )
  )

  val categoryProvider: CodecProvider = EnumValueCodecProvider[Category, String](
    _.id,
    str =>
      Category.values
        .find(_.id == str)
        .getOrElse(
          throw new IllegalArgumentException(s"Invalid category id: $str")
        )
  )

  // Ordinal-based codec providers
  val colorOrdinalProvider: CodecProvider = EnumValueCodecProvider.forOrdinalEnum[Color]
  val levelOrdinalProvider: CodecProvider = EnumValueCodecProvider.forOrdinalEnum[Level]

  // Registry for simple string enum tests
  val colorRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(colorProvider)
      .register[ColoredItem]
      .build

  // Registry for ordinal enum tests
  val levelRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(levelProvider)
      .register[UserProfile]
      .build

  // Registry for status code (automatic code detection)
  val statusCodeRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(statusCodeProvider)
      .register[ApiResponse]
      .build

  // Registry for priority with @BsonEnum annotation
  val taskRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(priorityProvider)
      .register[TaskItem]
      .build

  // Registry for category with @BsonEnum annotation
  val productRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(categoryProvider)
      .register[Product]
      .build

  // Registry for optional enum fields
  val configurationRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(colorProvider, levelProvider)
      .register[Configuration]
      .build

  // Registry for collection of enums
  val paletteRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(colorProvider)
      .register[Palette]
      .build

  // Registry for multiple enums
  val gameCharacterRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(levelProvider, colorProvider, priorityProvider)
      .register[GameCharacter]
      .build

  // Registry for nested case class with enum
  val orderRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(statusCodeProvider, priorityProvider)
      .register[ItemWithStatus]
      .register[Order]
      .build

  // Registry for map with enum values
  val colorMappingRegistry: CodecRegistry =
    RegistryBuilder
      .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      .ignoreNone
      .withProviders(colorProvider)
      .register[ColorMapping]
      .build

end EnumModels
