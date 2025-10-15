package io.github.mbannour.mongo.codecs

import org.bson.codecs.Codec
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.bson.codecs.configuration.CodecRegistries.{fromRegistries, fromProviders, fromCodecs}
import scala.reflect.ClassTag
import scala.compiletime.*

/** Type-safe, immutable registry builder using Scala 3 opaque types and extension methods.
  *
  * `RegistryBuilder` provides a fluent API for constructing MongoDB [[org.bson.codecs.configuration.CodecRegistry]] instances with
  * compile-time type safety and functional programming patterns.
  *
  * ===Features===
  *   - **Opaque types** for enhanced type safety without runtime overhead
  *   - **Immutable by design** - all operations return new instances
  *   - **Lazy registry building** - registries are only built once at the end
  *   - Choose between **encode `None` as `null`** or **ignore `None` fields**
  *   - Add individual codecs with `withCodec` or many at once with `withCodecs`
  *   - Automatically derive codecs for **case classes** with `register[T]`
  *   - Batch registration with `registerAll[(Type1, Type2, ...)]`
  *   - Configure discriminator field names for sealed hierarchies
  *   - Extension methods for fluent, idiomatic Scala 3 API
  *
  * ===Example Usage===
  * {{{
  *   import org.mongodb.scala.MongoClient
  *
  *   // Simple usage
  *   val registry = MongoClient.DEFAULT_CODEC_REGISTRY
  *     .newBuilder
  *     .ignoreNone
  *     .register[Address]
  *     .register[Person]
  *     .build
  *
  *   // With custom configuration
  *   val registry = RegistryBuilder
  *     .from(MongoClient.DEFAULT_CODEC_REGISTRY)
  *     .configure(_.copy(
  *       noneHandling = NoneHandling.Ignore,
  *       discriminatorField = "_type"
  *     ))
  *     .withCodec(employeeIdCodec)
  *     .register[Person]
  *     .build
  *
  *   // Batch registration with functional configuration
  *   val registry = MongoClient.DEFAULT_CODEC_REGISTRY
  *     .newBuilder
  *     .configure { config =>
  *       config
  *         .withIgnoreNone
  *         .withDiscriminator("_type")
  *     }
  *     .registerAll[(Address, Person, Department)]
  *     .build
  * }}}
  */
opaque type RegistryBuilder = RegistryBuilder.State

object RegistryBuilder:

  /** Internal state representation */
  private[codecs] final case class State(
      base: CodecRegistry,
      config: CodecConfig = CodecConfig(),
      providers: Vector[CodecProvider] = Vector.empty,
      codecs: Vector[Codec[?]] = Vector.empty,
      cachedRegistry: Option[CodecRegistry] = None
  )

  /** Create builder from base registry with default configuration */
  def from(base: CodecRegistry): RegistryBuilder = State(base)

  /** Create builder from base registry with custom configuration */
  def apply(base: CodecRegistry, config: CodecConfig): RegistryBuilder =
    State(base, config)

  /** Extension methods for fluent builder API */
  extension (builder: RegistryBuilder)

    /** Configure with a function - functional approach for flexible configuration.
      *
      * This is the most flexible configuration method, allowing you to transform the configuration using any logic you need.
      *
      * @param f
      *   Function to transform the current configuration
      * @example
      *   {{{
      *   // Simple configuration
      *   builder.configure(_.copy(noneHandling = NoneHandling.Ignore))
      *
      *   // Using helper methods on CodecConfig
      *   builder.configure(_.withIgnoreNone.withDiscriminator("_type"))
      *
      *   // Conditional configuration
      *   builder.configure { config =>
      *     if (useEncoding) config.withEncodeNone else config.withIgnoreNone
      *   }
      *   }}}
      */
    def configure(f: CodecConfig => CodecConfig): RegistryBuilder =
      builder.copy(config = f(builder.config), cachedRegistry = None)

    /** Set the codec configuration directly.
      *
      * @param newConfig
      *   The codec configuration to use
      */
    def withConfig(newConfig: CodecConfig): RegistryBuilder =
      builder.copy(config = newConfig, cachedRegistry = None)

    /** Switch policy: omit `None` fields entirely from BSON documents. */
    def ignoreNone: RegistryBuilder =
      configure(_.copy(noneHandling = NoneHandling.Ignore))

    /** Switch policy: encode `None` as BSON `null`. */
    def encodeNone: RegistryBuilder =
      configure(_.copy(noneHandling = NoneHandling.Encode))

    /** Set the discriminator field name for sealed hierarchies.
      *
      * @param field
      *   The field name to use for type discriminators (default: "_t")
      */
    def discriminator(field: String): RegistryBuilder =
      configure(_.copy(discriminatorField = field))

    /** Add a single explicit codec.
      *
      * Useful for value classes or third-party types where automatic derivation is not possible.
      *
      * @param codec
      *   The codec to add
      */
    def withCodec[A](codec: Codec[A]): RegistryBuilder =
      builder.copy(codecs = builder.codecs :+ codec, cachedRegistry = None)

    /** Add multiple codecs at once.
      *
      * @param codecs
      *   Variable number of codecs to add
      */
    def withCodecs(codecs: Codec[?]*): RegistryBuilder =
      builder.copy(codecs = builder.codecs ++ codecs, cachedRegistry = None)

    /** Register a type with automatic codec derivation.
      *
      * Relies on Scala 3 inline macros to auto-generate the BSON codec. Works for nested case classes and sealed hierarchies.
      *
      * Performance note: Intermediate registries are cached to avoid rebuilding the same registry multiple times
      * during chained register calls.
      *
      * @tparam T
      *   The type to register (must be a case class)
      */
    inline def register[T: ClassTag]: RegistryBuilder =
      val tempRegistry = getOrBuildRegistry(builder)
      val provider = CodecProviderMacro.createCodecProvider[T](using
        summon[ClassTag[T]],
        builder.config,
        tempRegistry
      )
      // Invalidate cache since we're adding a new provider
      builder.copy(providers = builder.providers :+ provider, cachedRegistry = None)
    end register

    /** Batch register multiple types using tuple syntax.
      *
      * This is more efficient than calling `register` multiple times separately as it minimizes intermediate registry builds.
      *
      * @tparam T
      *   A tuple of types to register
      * @example
      *   {{{
      *   builder.registerAll[(Person, Address, Department)]
      *   }}}
      */
    inline def registerAll[T <: Tuple]: RegistryBuilder =
      inline erasedValue[T] match
        case _: EmptyTuple => builder
        case _: (h *: t) =>
          register[h](using summonInline[ClassTag[h]]).registerAll[t]

    /** Build the final [[CodecRegistry]].
      *
      * This is when the actual registry is constructed from all registered codecs and providers. Call this method only once at the end of
      * your configuration chain.
      */
    def build: CodecRegistry =
      builder.cachedRegistry match
        case Some(cached) if builder.providers.isEmpty && builder.codecs.isEmpty =>
          // If we have a cached registry and nothing was added after caching, reuse it
          cached
        case _ =>
          // Otherwise build fresh
          buildRegistry(builder)

    /** Get cached registry or build a new one. Used internally to optimize register chains. */
    private def getOrBuildRegistry(state: State): CodecRegistry =
      state.cachedRegistry.getOrElse(buildRegistry(state))

    private def buildRegistry(state: State): CodecRegistry =
      val parts = Vector.newBuilder[CodecRegistry]
      parts += state.base

      if state.codecs.nonEmpty then parts += fromCodecs(state.codecs*)

      if state.providers.nonEmpty then parts += fromProviders(state.providers*)

      fromRegistries(parts.result()*)
    end buildRegistry
  end extension

  /** Extension methods for CodecRegistry to create builders */
  extension (registry: CodecRegistry)
    /** Create a new builder from this registry */
    def newBuilder: RegistryBuilder = from(registry)

    /** Create a new builder with custom configuration */
    def builderWith(config: CodecConfig): RegistryBuilder = apply(registry, config)

end RegistryBuilder
