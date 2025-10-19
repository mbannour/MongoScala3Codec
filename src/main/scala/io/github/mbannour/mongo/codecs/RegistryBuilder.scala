package io.github.mbannour.mongo.codecs

import scala.compiletime.*
import scala.reflect.ClassTag
import scala.util.Try

import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromProviders, fromRegistries}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

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
  * ===Common Patterns===
  * {{{
  *   // Register a single type and build immediately
  *   given CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY
  *     .newBuilder
  *     .just[User]
  *
  *   // Register multiple types with configuration
  *   val registry = MongoClient.DEFAULT_CODEC_REGISTRY
  *     .newBuilder
  *     .ignoreNone
  *     .withTypes[(User, Order, Product)]
  *
  *   // Conditional registration
  *   val builder = baseRegistry.newBuilder
  *     .registerIf[AdminUser](isProduction)
  *     .registerIf[DebugInfo](!isProduction)
  * }}}
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

  // Get a cached registry if present; otherwise build it once and cache it in the returned state
  private def getOrBuildRegistry(state: State): (CodecRegistry, State) =
    state.cachedRegistry match
      case Some(r) => (r, state)
      case None =>
        val r = buildRegistry(state)
        (r, state.copy(cachedRegistry = Some(r)))

  // Build a registry snapshot from the accumulated parts
  private def buildRegistry(state: State): CodecRegistry =
    val parts = Vector.newBuilder[CodecRegistry]
    parts += state.base

    if state.codecs.nonEmpty then parts += fromCodecs(state.codecs*)

    if state.providers.nonEmpty then parts += fromProviders(state.providers*)

    fromRegistries(parts.result()*)
  end buildRegistry

  // Helper for registerAll: accumulate providers for a tuple of types without rebuilding registry
  private inline def accumulateProviders[T <: Tuple](state: State, tempRegistry: CodecRegistry): State =
    inline erasedValue[T] match
      case _: EmptyTuple => state
      case _: (h *: t) =>
        val prov = CodecProviderMacro.createCodecProvider[h](using
          summonInline[ClassTag[h]],
          state.config,
          tempRegistry
        )
        accumulateProviders[t](state.copy(providers = state.providers :+ prov, cachedRegistry = None), tempRegistry)

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
      * Performance: O(1) - Uses cached registry from previous operations, no rebuilding until `build()` is called.
      *
      * @tparam T
      *   The type to register (must be a case class or sealed trait)
      */
    inline def register[T: ClassTag]: RegistryBuilder =
      val (tempRegistry, b1) = getOrBuildRegistry(builder)
      val provider = CodecProviderMacro.createCodecProvider[T](using
        summon[ClassTag[T]],
        b1.config,
        tempRegistry
      )
      // Invalidate cache since we're adding a new provider
      b1.copy(providers = b1.providers :+ provider, cachedRegistry = None)
    end register

    /** Batch register multiple types using tuple syntax.
      *
      * This is more efficient than calling `register` multiple times separately as it builds the temporary registry only once.
      *
      * Performance: O(1) registry build for N types, compared to O(N) with chained `register` calls.
      *
      * @tparam T
      *   A tuple of types to register
      * @example
      *   {{{
      *   builder.registerAll[(Person, Address, Department)]
      *   }}}
      */
    inline def registerAll[T <: Tuple]: RegistryBuilder =
      val (temp, b0) = getOrBuildRegistry(builder)
      // Note: b0 now has a cached registry, but we're about to add providers to it
      // We must invalidate the cache since the providers will be added AFTER the cache was built
      val result = accumulateProviders[T](b0.copy(cachedRegistry = None), temp)
      result

    /** Conditionally register a type based on a runtime condition.
      *
      * Useful for environment-specific or feature-flag based codec registration.
      *
      * @param condition
      *   Whether to register the type
      * @tparam T
      *   The type to register
      * @example
      *   {{{
      *   builder
      *     .registerIf[DebugInfo](isDevelopment)
      *     .registerIf[AdminFeature](hasAdminAccess)
      *   }}}
      */
    inline def registerIf[T: ClassTag](condition: Boolean): RegistryBuilder =
      if condition then register[T] else builder

    /** Build the final [[org.bson.codecs.configuration.CodecRegistry]].
      *
      * This is when the actual registry is constructed from all registered codecs and providers. Call this method only once at the end of
      * your configuration chain.
      *
      * Performance: O(1) if nothing was added since last cache, otherwise O(N) where N = number of providers + codecs.
      */
    def build: CodecRegistry =
      builder.cachedRegistry match
        case Some(cached) if builder.providers.isEmpty && builder.codecs.isEmpty =>
          // If we have a cached registry and nothing was added after caching, reuse it
          cached
        case _ =>
          // Otherwise build fresh
          val r = buildRegistry(builder)
          r

    // ===== Convenience Methods for Common Patterns =====

    /** Register a single type and immediately build the registry.
      *
      * Convenience method for the common pattern of registering one type.
      *
      * @tparam T
      *   The type to register
      * @example
      *   {{{
      *   given CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY.newBuilder.just[User]
      *   }}}
      */
    inline def just[T: ClassTag]: CodecRegistry =
      register[T].build

    /** Register multiple types and immediately build the registry.
      *
      * Convenience method for batch registration followed by build.
      *
      * @tparam T
      *   A tuple of types to register
      * @example
      *   {{{
      *   val registry = baseRegistry.newBuilder.withTypes[(User, Order, Product)]
      *   }}}
      */
    inline def withTypes[T <: Tuple]: CodecRegistry =
      registerAll[T].build

    // ===== State Inspection Methods =====

    /** Get the current codec configuration.
      *
      * Useful for debugging or conditional logic based on current settings.
      *
      * @return
      *   The current CodecConfig
      */
    def currentConfig: CodecConfig = builder.config

    /** Get the number of explicitly registered codecs.
      *
      * @return
      *   Count of codecs added via `withCodec` or `withCodecs`
      */
    def codecCount: Int = builder.codecs.size

    /** Get the number of registered codec providers.
      *
      * Each call to `register[T]` adds one provider.
      *
      * @return
      *   Count of providers from `register` and `registerAll` calls
      */
    def providerCount: Int = builder.providers.size

    /** Check if the registry is empty (no codecs or providers registered).
      *
      * @return
      *   true if no codecs or providers have been added
      */
    def isEmpty: Boolean = builder.codecs.isEmpty && builder.providers.isEmpty

    /** Check if this builder has a cached registry.
      *
      * Useful for understanding performance characteristics during chained operations.
      *
      * @return
      *   true if a registry is cached
      */
    def isCached: Boolean = builder.cachedRegistry.isDefined

    /** Attempt to get a codec for a given type from the current registry state.
      *
      * This builds the registry if needed and attempts to retrieve a codec.
      *
      * @tparam T
      *   The type to check
      * @return
      *   Some(codec) if available, None otherwise
      */
    def tryGetCodec[T: ClassTag]: Option[Codec[T]] =
      val registry = builder.cachedRegistry.getOrElse(buildRegistry(builder))
      val clazz = summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
      Try(registry.get(clazz)).toOption

    /** Check if a codec is available for a given type.
      *
      * @tparam T
      *   The type to check
      * @return
      *   true if a codec can be obtained for this type
      */
    inline def hasCodecFor[T: ClassTag]: Boolean =
      tryGetCodec[T].isDefined

    /** Get a summary of the builder's current state.
      *
      * Useful for logging and debugging.
      *
      * @return
      *   A human-readable summary string
      */
    def summary: String =
      val noneHandling = builder.config.noneHandling match
        case NoneHandling.Ignore => "ignore None fields"
        case NoneHandling.Encode => "encode None as null"
      s"RegistryBuilder(providers=${builder.providers.size}, codecs=${builder.codecs.size}, $noneHandling, discriminator='${builder.config.discriminatorField}', cached=${builder.cachedRegistry.isDefined})"

  end extension

  /** Extension methods for CodecRegistry to create builders */
  extension (registry: CodecRegistry)
    /** Create a new builder from this registry */
    def newBuilder: RegistryBuilder = from(registry)

    /** Create a new builder with custom configuration */
    def builderWith(config: CodecConfig): RegistryBuilder = apply(registry, config)

end RegistryBuilder
