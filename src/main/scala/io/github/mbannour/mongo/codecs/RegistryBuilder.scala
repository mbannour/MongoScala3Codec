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
  *   - **Efficient caching** - temporary derivation registry cached across chained operations
  *   - Choose between **encode `None` as `null`** or **ignore `None` fields**
  *   - Add individual codecs with `withCodec` or many at once with `withCodecs`
  *   - Automatically derive codecs for **case classes** with `register[T]`
  *   - Batch registration with `registerAll[(Type1, Type2, ...)]`
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
  *
  *   // Merge builders
  *   val commonTypes = baseBuilder.register[Address].register[Person]
  *   val fullBuilder = commonTypes ++ specificTypesBuilder
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
  *       noneHandling = NoneHandling.Ignore
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
  *     }
  *     .registerAll[(Address, Person, Department)]
  *     .build
  * }}}
  *
  * ===Performance Notes===
  *   - The builder maintains a cached temporary registry used for codec derivation
  *   - Chaining `register[A].register[B]...` is O(N) total, not O(N²)
  *   - The cache is preserved across register calls and only rebuilt when base/codecs change
  *   - The final registry is assembled once in `build()` with all accumulated providers
  */
opaque type RegistryBuilder = RegistryBuilder.State

object RegistryBuilder:

  /** Internal state representation.
    *
    * @param base
    *   The base codec registry (e.g., MongoClient.DEFAULT_CODEC_REGISTRY)
    * @param config
    *   Codec configuration (None handling, discriminator, etc.)
    * @param providers
    *   Accumulated codec providers from register/registerAll calls
    * @param codecs
    *   Explicitly added codecs via withCodec/withCodecs
    * @param cachedRegistry
    *   Cached temporary registry used for codec derivation during register calls. This is NOT the final assembled registry - it's a
    *   "derivation environment" containing base + codecs (but not providers being added). Preserved across register calls for O(N)
    *   performance.
    */
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

  private def getOrBuildRegistry(state: State): (CodecRegistry, State) =
    state.cachedRegistry match
      case Some(r) => (r, state)
      case None =>
        val r = buildRegistry(state)
        (r, state.copy(cachedRegistry = Some(r)))

  private def buildRegistry(state: State): CodecRegistry =
    val parts = Vector.newBuilder[CodecRegistry]
    parts += state.base

    if state.codecs.nonEmpty then parts += fromCodecs(state.codecs*)

    if state.providers.nonEmpty then parts += fromProviders(state.providers*)

    fromRegistries(parts.result()*)
  end buildRegistry

  private inline def accumulateProvidersLoop[T <: Tuple](
      acc: List[CodecProvider],
      state: State,
      tempRegistry: CodecRegistry
  ): List[CodecProvider] =
    inline erasedValue[T] match
      case _: EmptyTuple => acc
      case _: (h *: t) =>
        val prov = CodecProviderMacro.createCodecProvider[h](using
          summonInline[ClassTag[h]],
          state.config,
          tempRegistry
        )
        accumulateProvidersLoop[t](prov :: acc, state, tempRegistry)

  private inline def accumulateProviders[T <: Tuple](state: State, tempRegistry: CodecRegistry): State =

    val added = accumulateProvidersLoop[T](Nil, state, tempRegistry)
    state.copy(providers = state.providers ++ added.reverse)

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
      *   builder.configure(_.withIgnoreNone)
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

    /** Add a single explicit codec.
      *
      * Useful for value classes or third-party types where automatic derivation is not possible.
      *
      * @param codec
      *   The codec to add
      */
    def withCodec[A](codec: Codec[A]): RegistryBuilder =
      builder.copy(codecs = builder.codecs :+ codec, cachedRegistry = None)

    /** Add a single codec provider.
      *
      * @param provider
      */
    def withProvider(provider: CodecProvider): RegistryBuilder =
      builder.copy(providers = builder.providers :+ provider, cachedRegistry = None)

    /** Add multiple codec providers at once.
      *
      * @param providers
      *   Variable number of codec providers to add
      */
    def withProviders(providers: CodecProvider*): RegistryBuilder =
      builder.copy(providers = builder.providers ++ providers, cachedRegistry = None)

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
      * @tparam T
      *   The type to register (must be a case class)
      */
    inline def register[T](using ct: ClassTag[T]): RegistryBuilder =
      val (tempRegistry, b1) = getOrBuildRegistry(builder)
      val provider = CodecProviderMacro.createCodecProvider[T](using ct, b1.config, tempRegistry)
      b1.copy(providers = b1.providers :+ provider)
    end register

    /** Batch register multiple types using tuple syntax.
      *
      * This is more efficient than calling `register` multiple times separately as it builds the temporary registry only once.
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

      accumulateProviders[T](b0, temp)

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
      */
    def build: CodecRegistry =
      buildRegistry(builder)

    /** Alias for `build`. Constructs the final codec registry.
      *
      * @return
      *   The assembled CodecRegistry
      */
    def toRegistry: CodecRegistry = build

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

    /** Merge two builders, combining their providers and codecs.
      *
      * Config and base from the left builder are used; only providers and codecs are merged.
      *
      * @param other
      *   The builder to merge with
      * @return
      *   A new builder with combined providers and codecs
      * @example
      *   {{{
      *   val common = baseBuilder.register[Address].register[Person]
      *   val full = common ++ specificBuilder
      *   }}}
      */
    def ++(other: RegistryBuilder): RegistryBuilder =
      builder.copy(
        providers = builder.providers ++ other.providers,
        codecs = builder.codecs ++ other.codecs,
        cachedRegistry = None // Invalidate cache when merging
      )

    /** Clear the cached temporary registry.
      *
      * Mainly useful for testing or debugging. The cache will be rebuilt on next register call.
      *
      * @return
      *   A new builder with cache cleared
      */
    def clearCache: RegistryBuilder =
      builder.copy(cachedRegistry = None)

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
      * This builds a snapshot registry if needed and attempts to retrieve a codec. Does not cache the result.
      *
      * @tparam T
      *   The type to check
      * @return
      *   Some(codec) if available, None otherwise
      */
    def tryGetCodec[T: ClassTag]: Option[Codec[T]] =
      val registry = builder.cachedRegistry.getOrElse(buildRegistry(builder))
      val clazz = summonInline[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
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
      s"RegistryBuilder(providers=${builder.providers.size}, codecs=${builder.codecs.size}, $noneHandling, cached=${builder.cachedRegistry.isDefined})"

  end extension

  /** Extension methods for CodecRegistry to create builders */
  extension (registry: CodecRegistry)
    /** Create a new builder from this registry */
    def newBuilder: RegistryBuilder = from(registry)

    /** Create a new builder with custom configuration */
    def builderWith(config: CodecConfig): RegistryBuilder = apply(registry, config)

end RegistryBuilder
