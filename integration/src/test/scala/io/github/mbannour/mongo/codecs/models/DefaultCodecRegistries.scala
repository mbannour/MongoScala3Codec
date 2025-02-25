package io.github.mbannour.mongo.codecs.models

import org.mongodb.scala.MongoClient
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.Codec
import io.github.mbannour.bson.macros.*
import io.github.mbannour.mongo.codecs.CodecProviderMacro

object DefaultCodecRegistries:
  
  val defaultRegistry: CodecRegistry =
    CodecRegistries.fromRegistries(
      CodecRegistries
        .fromProviders(CodecProviderMacro.createCodecProviderEncodeNone[Address], CodecProviderMacro.createCodecProviderEncodeNone[Person]),
      CodecRegistries.fromCodecs(
        EmployeeId.dealerIdBsonCodec,
        DateField.dateFieldCodec
      ),
      MongoClient.DEFAULT_CODEC_REGISTRY
    )

  given CodecRegistry = defaultRegistry
end DefaultCodecRegistries
