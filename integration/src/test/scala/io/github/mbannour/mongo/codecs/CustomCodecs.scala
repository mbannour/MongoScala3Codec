package io.github.mbannour.mongo.codecs

import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}

import java.time.{ZoneId, ZonedDateTime}
import java.time.temporal.ChronoUnit

class ZonedDateTimeCodec extends Codec[ZonedDateTime]:
  override def encode(writer: BsonWriter, value: ZonedDateTime, encoderContext: EncoderContext): Unit =
    val truncatedValue = value.truncatedTo(ChronoUnit.MILLIS)
    writer.writeDateTime(truncatedValue.toInstant.toEpochMilli)

  override def decode(reader: BsonReader, decoderContext: DecoderContext): ZonedDateTime =
    ZonedDateTime.ofInstant(
      java.time.Instant.ofEpochMilli(reader.readDateTime()),
      ZoneId.systemDefault()
    )
  override def getEncoderClass: Class[ZonedDateTime] = classOf[ZonedDateTime]
end ZonedDateTimeCodec
