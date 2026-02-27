package io.github.mbannour.mongo.codecs

import scala.compiletime.*
import scala.quoted.*
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
  *
  * ===Migration Note (type parameter)===
  * `RegistryBuilder` now carries a compile-time `[+Registered <: Tuple]` type parameter that tracks which types have been registered. Code
  * that previously annotated values as plain `RegistryBuilder` must now use one of:
  *   - `RegistryBuilder[Tuple]` — explicit wildcard form
  *   - `RegistryBuilder.AnyRegistryBuilder` — convenience type alias provided for this purpose
  */
opaque type RegistryBuilder[+Registered <: Tuple] = RegistryBuilder.State

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
      cachedRegistry: Option[CodecRegistry] = None,
      sealedSubtypeClasses: Set[Class[?]] = Set.empty
  )

  /** Type alias for `RegistryBuilder[Tuple]`.
    *
    * Use this when you need a non-specific reference to a builder without caring about which types are tracked. This is the migration alias
    * for code that previously used `RegistryBuilder` without a type argument (before the `[+Registered <: Tuple]` type parameter was
    * added).
    *
    * @example
    *   {{{
    *   // Before (no longer compiles — RegistryBuilder now requires a type argument):
    *   val b: RegistryBuilder = someMethod()
    *
    *   // After — use the alias or an explicit type argument:
    *   val b: RegistryBuilder.AnyRegistryBuilder = someMethod()
    *   val b: RegistryBuilder[Tuple]             = someMethod()
    *   }}}
    */
  type AnyRegistryBuilder = RegistryBuilder[Tuple]

  /** Create builder from base registry with default configuration */
  def from(base: CodecRegistry): RegistryBuilder[EmptyTuple] = State(base)

  /** Create builder from base registry with custom configuration */
  def apply(base: CodecRegistry, config: CodecConfig): RegistryBuilder[EmptyTuple] =
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

  private def captureVarargs[A](args: A*): Seq[A] = args

  /** Non-inline helper that checks whether the encoder class of a codec is already tracked as a sealed subtype. Kept non-inline so that
    * `codec` is always typed as `Codec[?]` (the Java interface), where `getEncoderClass` is declared with `()`. This avoids a Scala 3
    * inline-resolution error that occurs when a concrete Scala codec class overrides `getEncoderClass` without `()`.
    */
  private def checkSealedSubtypeClass(codec: Codec[?], sealedSubtypeClasses: Set[Class[?]]): Unit =
    val encoderClass = codec.getEncoderClass
    if sealedSubtypeClasses.contains(encoderClass) then
      throw new IllegalStateException(
        s"Duplicate registration: ${encoderClass.getSimpleName} is already registered " +
          s"as a sealed subtype (via registerSealed). Remove the .withCodec[A] call."
      )

  private inline def tupleContains[Ts <: Tuple, A]: Boolean =
    inline erasedValue[Ts] match
      case _: EmptyTuple  => false
      case _: (A *: _)    => true
      case _: (_ *: tail) => tupleContains[tail, A]
      case _              => false

  private inline def typeName[T]: String = ${ typeNameImpl[T] }

  private def typeNameImpl[T: Type](using Quotes): Expr[String] =
    import quotes.reflect.*
    val tpe = TypeRepr.of[T].dealias
    val symbol = tpe.typeSymbol
    val renderedName =
      if symbol != Symbol.noSymbol && symbol.name.nonEmpty && symbol.name != "<none>" then symbol.name
      else tpe.show
    Expr(renderedName)
  end typeNameImpl

  private inline def duplicateCodecError[T](inline detail: String): Nothing =
    compiletime.error("Duplicate codec detected for " + typeName[T] + ". " + detail)

  private inline def ensureExplicitNotRegistered[T, Registered <: Tuple]: Unit =
    inline if tupleContains[Registered, T] then
      duplicateCodecError[T](
        "withCodec[T] cannot add a type that is already tracked in this builder. " +
          "Remove either the earlier .register[T] / .registerAll call or this .withCodec[T] call."
      )

  private inline def ensureDerivedNotRegistered[T, Registered <: Tuple]: Unit =
    inline if tupleContains[Registered, T] then
      duplicateCodecError[T](
        "This type is already registered in this builder. " +
          "Remove the duplicate .register[T], .registerSealed[T], .registerAll, or .registerSealedAll call."
      )

  private inline def ensureDistinctTupleTypes[Ts <: Tuple]: Unit =
    inline erasedValue[Ts] match
      case _: EmptyTuple => ()
      case _: (h *: t) =>
        inline if tupleContains[t, h] then
          duplicateCodecError[h](
            "The same type appears more than once in your tuple argument to registerAll/registerSealedAll. " +
              "Each type must appear exactly once — remove the duplicate."
          )
        else ensureDistinctTupleTypes[t]
      case _ => ()

  private inline def ensureTupleNotRegistered[Ts <: Tuple, Registered <: Tuple]: Unit =
    inline erasedValue[Ts] match
      case _: EmptyTuple => ()
      case _: (h *: t) =>
        ensureDerivedNotRegistered[h, Registered]
        ensureTupleNotRegistered[t, Registered]
      case _ => ()

  private inline def ensureNoOverlap[Incoming <: Tuple, Existing <: Tuple]: Unit =
    inline erasedValue[Incoming] match
      case _: EmptyTuple => ()
      case _: (h *: t) =>
        inline if tupleContains[Existing, h] then
          duplicateCodecError[h](
            "Cannot merge builders: this type is registered in both the left and right builder. " +
              "Remove .register[T] or .registerSealed[T] from one of the builders before using ++."
          )
        else ensureNoOverlap[t, Existing]
      case _ => ()

  private def withCodecsImpl[Registered <: Tuple: Type](
      builderExpr: Expr[RegistryBuilder[Registered]],
      codecsExpr: Expr[Seq[Codec[?]]]
  )(using Quotes): Expr[RegistryBuilder[Tuple]] =
    import quotes.reflect.*

    def codecTargetType(codecExpr: Expr[Codec[?]]): TypeRepr =
      codecExpr.asTerm.tpe.widen.baseType(TypeRepr.of[Codec].typeSymbol) match
        case AppliedType(_, List(targetType)) => targetType
        case _ =>
          report.errorAndAbort(
            s"withCodecs only accepts Codec[T] values, but found: ${codecExpr.asTerm.tpe.show}"
          )

    def chain[Current <: Tuple: Type](
        currentExpr: Expr[RegistryBuilder[Current]],
        codecs: List[Expr[Codec[?]]]
    ): Expr[RegistryBuilder[Tuple]] =
      codecs match
        case Nil => currentExpr
        case codecExpr :: tail =>
          codecTargetType(codecExpr).asType match
            case '[target] =>
              val typedCodec = codecExpr.asExprOf[Codec[target]]
              type Next = Tuple.Concat[Current, target *: EmptyTuple]
              given Type[Next] = Type.of[Tuple.Concat[Current, target *: EmptyTuple]]
              val nextExpr: Expr[RegistryBuilder[Next]] =
                '{ $currentExpr.withCodec[target]($typedCodec) }
              chain[Next](nextExpr, tail)

    codecsExpr match
      case Varargs(args) =>
        chain[Registered](builderExpr, args.map(_.asExprOf[Codec[?]]).toList)
      case _ =>
        // Runtime Seq/spread (codecs*) cannot be fully inspected at compile-time.
        // Fall back to runtime append with a two-part duplicate check:
        //   (a) incoming codecs vs. already-registered codecs in the builder, and
        //   (b) duplicates within the incoming sequence itself.
        '{
          val runtimeCodecs = captureVarargs($codecsExpr*)
          val existingClasses: Set[Class[?]] = $builderExpr.codecs.map(_.getEncoderClass).toSet
          val incomingClasses: Seq[Class[?]] = runtimeCodecs.map(_.getEncoderClass)
          // (a) incoming vs. existing
          val dupVsExisting: Seq[Class[?]] = incomingClasses.filter(existingClasses.contains)
          // (b) within the incoming sequence – collect every class seen more than once
          val seen = collection.mutable.HashSet.empty[Class[?]]
          val dupWithinSelf: Seq[Class[?]] = incomingClasses.filter(cls => !seen.add(cls))
          val allDups: Seq[Class[?]] = (dupVsExisting ++ dupWithinSelf).distinct
          if allDups.nonEmpty then
            throw new IllegalArgumentException(
              "withCodecs: duplicate codec(s) detected at runtime. " +
                "Duplicate encoder class(es): " + allDups.map(_.getSimpleName).mkString(", ") +
                ". Remove the duplicate codec(s) from the sequence."
            )
          $builderExpr.copy(
            codecs = $builderExpr.codecs ++ runtimeCodecs,
            cachedRegistry = None
          )
        }
    end match
  end withCodecsImpl

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

  /** Accumulate sealed trait providers recursively through tuple.
    *
    * Returns a pair of (accumulated providers, accumulated subtype runtime classes) so that the caller can detect duplicate registrations
    * of concrete subtypes that were already covered by an earlier `registerSealed` call.
    */
  private inline def accumulateSealedProvidersLoop[T <: Tuple](
      acc: List[CodecProvider],
      accSubtypes: Set[Class[?]],
      state: State,
      tempRegistry: CodecRegistry
  ): (List[CodecProvider], Set[Class[?]]) =
    inline erasedValue[T] match
      case _: EmptyTuple => (acc, accSubtypes)
      case _: (h *: t)   =>
        // Create provider for the sealed trait itself
        val sealedProvider = SealedCodecProviderMacro.createProvider[h](using
          summonInline[ClassTag[h]],
          state.config,
          tempRegistry
        )

        // Create providers for all concrete case class subtypes
        val subclassProviders = SealedCodecProviderMacro.createSubclassProviders[h](
          state.config,
          tempRegistry
        )

        // Collect runtime classes for all concrete subtypes for duplicate detection
        val newSubtypes = SealedCodecProviderMacro.subclassRuntimeClasses[h]

        // Accumulate all providers and continue with rest of tuple
        accumulateSealedProvidersLoop[t](
          (sealedProvider :: subclassProviders.toList) ++ acc,
          accSubtypes ++ newSubtypes,
          state,
          tempRegistry
        )

  /** Accumulate all sealed trait providers from a tuple */
  private inline def accumulateSealedProviders[T <: Tuple](state: State, tempRegistry: CodecRegistry): State =
    val (added, subtypeClasses) = accumulateSealedProvidersLoop[T](Nil, Set.empty, state, tempRegistry)
    state.copy(
      providers = state.providers ++ added.reverse,
      sealedSubtypeClasses = state.sealedSubtypeClasses ++ subtypeClasses
    )

  /** Extension methods for fluent builder API */
  extension [Registered <: Tuple](builder: RegistryBuilder[Registered])

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
    def configure(f: CodecConfig => CodecConfig): RegistryBuilder[Registered] =
      builder.copy(config = f(builder.config), cachedRegistry = None)

    /** Set the codec configuration directly.
      *
      * @param newConfig
      *   The codec configuration to use
      */
    def withConfig(newConfig: CodecConfig): RegistryBuilder[Registered] =
      builder.copy(config = newConfig, cachedRegistry = None)

    /** Switch policy: omit `None` fields entirely from BSON documents. */
    def ignoreNone: RegistryBuilder[Registered] =
      configure(_.copy(noneHandling = NoneHandling.Ignore))

    /** Switch policy: encode `None` as BSON `null`. */
    def encodeNone: RegistryBuilder[Registered] =
      configure(_.copy(noneHandling = NoneHandling.Encode))

    /** Add a single explicit codec.
      *
      * Useful for value classes or third-party types where automatic derivation is not possible.
      *
      * @param codec
      *   The codec to add
      */
    inline def withCodec[A](codec: Codec[A]): RegistryBuilder[Tuple.Concat[Registered, A *: EmptyTuple]] =
      ensureExplicitNotRegistered[A, Registered]
      // Delegate the sealedSubtypeClasses check to a non-inline method that accepts Codec[?].
      // This avoids a Scala 3 inline resolution issue: when withCodec is inlined at a call site
      // where the static type is a concrete Scala codec class that overrides getEncoderClass
      // without (), calling codec.getEncoderClass in an inline body causes a compile error
      // ("does not take parameters"). The non-inline helper always sees Codec[?] (the Java
      // interface), where getEncoderClass is declared with (), so the call is always valid.
      checkSealedSubtypeClass(codec, builder.sealedSubtypeClasses)
      builder.copy(codecs = builder.codecs :+ codec, cachedRegistry = None)
    end withCodec

    /** Add a single codec provider.
      *
      * @param provider
      *   The codec provider to add
      * @note
      *   No duplicate-target detection is performed for providers added via this method. If you register a provider for a type that was
      *   already registered via `register[T]` or `registerSealed[T]`, the duplicate will be silently accepted. Prefer the typed
      *   `register`/`registerSealed` API where compile-time safety is needed.
      */
    def withProvider(provider: CodecProvider): RegistryBuilder[Registered] =
      builder.copy(providers = builder.providers :+ provider, cachedRegistry = None)

    /** Add multiple codec providers at once.
      *
      * @param providers
      *   Variable number of codec providers to add
      * @note
      *   Same caveat as `withProvider`: no duplicate-target detection is performed for providers added via this method.
      */
    def withProviders(providers: CodecProvider*): RegistryBuilder[Registered] =
      builder.copy(providers = builder.providers ++ providers, cachedRegistry = None)

    /** Add multiple codecs at once.
      *
      * @param codecs
      *   Variable number of codecs to add
      */
    transparent inline def withCodecs(inline codecs: Codec[?]*): RegistryBuilder[Tuple] =
      ${ withCodecsImpl[Registered]('builder, 'codecs) }

    /** Register a type with automatic codec derivation.
      *
      * Relies on Scala 3 inline macros to auto-generate the BSON codec. Works for nested case classes and sealed hierarchies.
      *
      * @tparam T
      *   The type to register (must be a case class)
      */
    inline def register[T](using ct: ClassTag[T]): RegistryBuilder[Tuple.Concat[Registered, T *: EmptyTuple]] =
      ensureDerivedNotRegistered[T, Registered]
      if builder.sealedSubtypeClasses.contains(ct.runtimeClass) then
        throw new IllegalStateException(
          s"Duplicate registration: ${ct.runtimeClass.getSimpleName} is already registered as a sealed subtype " +
            s"(via registerSealed). Remove the .register[T] call."
        )
      val (tempRegistry, b1) = getOrBuildRegistry(builder)
      val provider = CodecProviderMacro.createCodecProvider[T](using ct, b1.config, tempRegistry)
      b1.copy(providers = b1.providers :+ provider)
    end register

    /** Register a sealed trait or class with automatic codec derivation for all subtypes.
      *
      * This method:
      *   - Generates a discriminator-based codec for the sealed trait/class
      *   - Automatically registers codecs for all concrete case class subtypes
      *   - Enables polymorphic serialization with a discriminator field (default: "_type")
      *
      * The discriminator field stores the concrete type name during encoding and is used to determine which concrete codec to use during
      * decoding.
      *
      * @tparam T
      *   The sealed trait or class to register
      * @example
      *   {{{
      *   sealed trait Status
      *   case class Active(since: Long) extends Status
      *   case class Inactive(reason: String) extends Status
      *
      *   val registry = MongoClient.DEFAULT_CODEC_REGISTRY
      *     .newBuilder
      *     .registerSealed[Status]
      *     .build
      *
      *   // Active encoded as: {"_type": "Active", "since": 1234567890}
      *   // Inactive encoded as: {"_type": "Inactive", "reason": "Completed"}
      *   }}}
      *
      * @note
      *   Only case classes are supported as sealed subtypes. Case objects are not supported - use Scala 3 enums instead.
      */
    inline def registerSealed[T: ClassTag]: RegistryBuilder[Tuple.Concat[Registered, T *: EmptyTuple]] =
      ensureDerivedNotRegistered[T, Registered]
      val (tempRegistry, b1) = getOrBuildRegistry(builder)

      // Create provider for the sealed trait itself
      val sealedProvider = SealedCodecProviderMacro.createProvider[T](using
        summon[ClassTag[T]],
        b1.config,
        tempRegistry
      )

      // Create providers for all concrete case class subtypes
      val subclassProviders = SealedCodecProviderMacro.createSubclassProviders[T](
        b1.config,
        tempRegistry
      )

      // Collect runtime classes for all concrete subtypes for duplicate detection
      val subtypeClasses = SealedCodecProviderMacro.subclassRuntimeClasses[T]

      // Add both the sealed trait provider and all subclass providers
      b1.copy(
        providers = (b1.providers :+ sealedProvider) ++ subclassProviders,
        sealedSubtypeClasses = b1.sealedSubtypeClasses ++ subtypeClasses
      )
    end registerSealed

    /** Batch register multiple sealed traits using tuple syntax.
      *
      * This is significantly more efficient than calling `registerSealed` multiple times as it builds the temporary registry only once.
      * Each sealed trait registration includes the trait itself plus all its concrete case class subtypes.
      *
      * @tparam T
      *   A tuple of sealed trait types to register
      * @example
      *   {{{
      *   sealed trait Animal
      *   case class Dog(name: String) extends Animal
      *   case class Cat(name: String) extends Animal
      *
      *   sealed trait Vehicle
      *   case class Car(make: String) extends Vehicle
      *   case class Bike(brand: String) extends Vehicle
      *
      *   // Register multiple sealed traits at once - more efficient
      *   val registry = RegistryBuilder
      *     .from(MongoClient.DEFAULT_CODEC_REGISTRY)
      *     .registerSealedAll[(Animal, Vehicle)]
      *     .build
      *   }}}
      *
      * @note
      *   Only case classes are supported as sealed subtypes. Case objects are not supported - use Scala 3 enums instead.
      */
    inline def registerSealedAll[T <: Tuple]: RegistryBuilder[Tuple.Concat[Registered, T]] =
      ensureDistinctTupleTypes[T]
      ensureTupleNotRegistered[T, Registered]
      val (temp, b0) = getOrBuildRegistry(builder)

      accumulateSealedProviders[T](b0, temp)
    end registerSealedAll

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
    inline def registerAll[T <: Tuple]: RegistryBuilder[Tuple.Concat[Registered, T]] =
      ensureDistinctTupleTypes[T]
      ensureTupleNotRegistered[T, Registered]
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
    inline def registerIf[T: ClassTag](condition: Boolean): RegistryBuilder[Tuple] =
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
    inline def ++[Other <: Tuple](other: RegistryBuilder[Other]): RegistryBuilder[Tuple.Concat[Registered, Other]] =
      ensureNoOverlap[Other, Registered]
      builder.copy(
        providers = builder.providers ++ other.providers,
        codecs = builder.codecs ++ other.codecs,
        sealedSubtypeClasses = builder.sealedSubtypeClasses ++ other.sealedSubtypeClasses,
        cachedRegistry = None // Invalidate cache when merging
      )
    end ++

    /** Clear the cached temporary registry.
      *
      * Mainly useful for testing or debugging. The cache will be rebuilt on next register call.
      *
      * @return
      *   A new builder with cache cleared
      */
    def clearCache: RegistryBuilder[Registered] =
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
    def newBuilder: RegistryBuilder[EmptyTuple] = from(registry)

    /** Create a new builder with custom configuration */
    def builderWith(config: CodecConfig): RegistryBuilder[EmptyTuple] = apply(registry, config)

end RegistryBuilder
