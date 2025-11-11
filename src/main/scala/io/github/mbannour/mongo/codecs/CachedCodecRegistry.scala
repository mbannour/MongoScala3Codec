package io.github.mbannour.mongo.codecs

import scala.collection.mutable
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistry

/** A CodecRegistry wrapper that caches codec lookups for better performance.
  *
  * This registry delegates to an underlying registry but caches the results to avoid repeated lookups. This is
  * particularly useful for nested case classes and sealed traits where the same codec may be looked up many times during
  * encoding/decoding.
  *
  * @param underlying
  *   The underlying CodecRegistry to delegate to
  * @param initialCache
  *   Optional pre-populated cache of codecs
  */
private[codecs] class CachedCodecRegistry(
    underlying: CodecRegistry,
    initialCache: Map[Class[?], Codec[?]] = Map.empty
) extends CodecRegistry:

  private val cache: mutable.Map[Class[?], Codec[?]] = mutable.Map.from(initialCache)

  override def get[T](clazz: Class[T]): Codec[T] =
    cache.getOrElseUpdate(clazz, underlying.get(clazz)).asInstanceOf[Codec[T]]

  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    // For this overload, we don't cache since it uses a different registry
    underlying.get(clazz, registry)

end CachedCodecRegistry
