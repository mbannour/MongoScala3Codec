package io.github.mbannour.mongo.codecs.models

import io.github.mbannour.mongo.codecs.{CodecProviderMacro, EnumValueCodecProvider}
import org.bson.codecs.{Codec, StringCodec}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.types.ObjectId
import org.mongodb.scala.MongoClient

enum Priority:
  case Low, Medium, High

case class Task(_id: ObjectId, title: String, priority: Priority)

object Task:

  private val taskProvider = CodecProviderMacro.createCodecProviderEncodeNone[Task]

  val priorityEnumProvider: CodecProvider = EnumValueCodecProvider.forStringEnum[Priority]

  val defaultRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries.fromProviders(taskProvider),
      CodecRegistries.fromProviders(priorityEnumProvider),
      MongoClient.DEFAULT_CODEC_REGISTRY
    )

  given CodecRegistry = defaultRegistry
end Task
