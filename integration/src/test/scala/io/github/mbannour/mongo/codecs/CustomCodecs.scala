package io.github.mbannour.mongo.codecs

import io.github.mbannour.mongo.codecs.models.Priority
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.mongodb.scala.bson.codecs

import java.time.{ZoneId, ZonedDateTime}
import java.time.temporal.ChronoUnit

class ZonedDateTimeCodec extends Codec[ZonedDateTime]:
  override def encode(writer: BsonWriter, value: ZonedDateTime, encoderContext: EncoderContext): Unit =
    // Truncate the value to milliseconds before encoding.
    val truncatedValue = value.truncatedTo(ChronoUnit.MILLIS)
    writer.writeDateTime(truncatedValue.toInstant.toEpochMilli)

  override def decode(reader: BsonReader, decoderContext: DecoderContext): ZonedDateTime =
    ZonedDateTime.ofInstant(
      java.time.Instant.ofEpochMilli(reader.readDateTime()),
      ZoneId.systemDefault()
    )
  override def getEncoderClass: Class[ZonedDateTime] = classOf[ZonedDateTime]
end ZonedDateTimeCodec

object PriorityCodec extends Codec[Priority.Value]:
  override def encode(writer: BsonWriter, value: Priority.Value, encoderContext: EncoderContext): Unit =
    writer.writeString(value.toString)

  override def decode(reader: BsonReader, decoderContext: DecoderContext): Priority.Value =
    val str = reader.readString()
    try Priority.withName(str)
    catch
      case ex: NoSuchElementException =>
        throw new IllegalArgumentException(s"Unknown Priority value: '$str'", ex)

  override def getEncoderClass: Class[Priority.Value] = classOf[Priority.Value]
end PriorityCodec


