package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.mongo.codecs.{CodecProviderMacro, RegistryBuilder, ZonedDateTimeCodec}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient

import java.time.ZonedDateTime

case class Event(_id: ObjectId, title: String, time: ZonedDateTime)

object Event:

  val eventRegistry: CodecRegistry = RegistryBuilder
    .from(MongoClient.DEFAULT_CODEC_REGISTRY)
    .ignoreNone
    .withCodec(new ZonedDateTimeCodec)
    .register[Event]
    .build

end Event
