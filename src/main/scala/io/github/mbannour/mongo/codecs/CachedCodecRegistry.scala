package io.github.mbannour.mongo.codecs

import java.util.concurrent.ConcurrentHashMap
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistry

/** A thread-safe CodecRegistry wrapper that caches codec lookups for better performance.
  *
  * This registry delegates to an underlying registry but caches the results to avoid repeated lookups. This is particularly useful for
  * nested case classes and sealed traits where the same codec may be looked up many times during encoding/decoding.
  *
  * Performance improvements:
  *   - Uses ConcurrentHashMap for thread-safe, lock-free caching
  *   - Lazy on-demand codec fetching (no eager pre-population)
  *   - Safe for concurrent access from multiple threads
  *
  * @param underlying
  *   The underlying CodecRegistry to delegate to
  * @param initialCache
  *   Optional pre-populated cache of codecs (for backward compatibility)
  */
class CachedCodecRegistry(
    underlying: CodecRegistry,
    initialCache: Map[Class[?], Codec[?]] = Map.empty
) extends CodecRegistry:

  private val cache: ConcurrentHashMap[Class[?], Codec[?]] =
    val chm = new ConcurrentHashMap[Class[?], Codec[?]]()
    initialCache.foreach { case (k, v) => chm.put(k, v) }
    chm

  override def get[T](clazz: Class[T]): Codec[T] =
    cache
      .computeIfAbsent(
        clazz,
        (k: Class[?]) => underlying.get(k)
      )
      .asInstanceOf[Codec[T]]

  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    underlying.get(clazz, registry)

end CachedCodecRegistry
