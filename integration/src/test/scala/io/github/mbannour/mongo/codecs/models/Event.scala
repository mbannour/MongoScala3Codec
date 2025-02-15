package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.mongo.codecs.{CodecProviderMacro, ZonedDateTimeCodec}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient

import java.time.ZonedDateTime

case class Event(_id: ObjectId, title: String, time: ZonedDateTime)

object Event:

  val eventProvider: CodecProvider =
    CodecProviderMacro.createCodecProviderEncodeNone[Event]

  val eventRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(eventProvider),
      CodecRegistries.fromCodecs(new ZonedDateTimeCodec),
      MongoClient.DEFAULT_CODEC_REGISTRY
    )

  given CodecRegistry = eventRegistry
end Event
