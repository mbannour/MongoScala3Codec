package io.github.mbannour.mongo.codecs.models

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import org.bson.{BsonInvalidOperationException, BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.CodecConfigurationException
import org.mongodb.scala.bson.{BsonDateTime, BsonTransformer, BsonValue}

class ZonedDateTimeAsDateTimeCodec extends Codec[ZonedDateTime]:
  override def decode(reader: BsonReader, decoderContext: DecoderContext): ZonedDateTime =
    try
      Instant
        .ofEpochMilli(reader.readDateTime())
        .atZone(ZoneOffset.UTC)
    catch
      case _: BsonInvalidOperationException =>
        ZonedDateTime.parse(reader.readString())

  override def encode(writer: BsonWriter, value: ZonedDateTime, encoderContext: EncoderContext): Unit =
    val valueAtUTC = ZonedDateTimeAsDateTimeCodec.zonedDateTimeAtUTC(value)
    try writer.writeDateTime(valueAtUTC.toInstant.toEpochMilli)
    catch
      case e: ArithmeticException =>
        throw new CodecConfigurationException(
          s"Unsupported ZonedDateTime value '$value' could not be converted to milliseconds: $e",
          e
        )

  override def getEncoderClass: Class[ZonedDateTime] = classOf[ZonedDateTime]

object ZonedDateTimeAsDateTimeCodec:
  def zonedDateTimeAtUTC(value: ZonedDateTime): ZonedDateTime =
    value.withZoneSameInstant(ZoneOffset.UTC)
  
  given transformZonedDateTime: BsonTransformer[ZonedDateTime] with
    def apply(value: ZonedDateTime): BsonValue =
      BsonDateTime(zonedDateTimeAtUTC(value).toInstant.toEpochMilli)
