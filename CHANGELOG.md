# Changelog

All notable changes to MongoScala3Codec will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.8-M1] - 2026-01-06

### Added
- Comprehensive README improvements with best practices
- Enhanced documentation structure with table of contents
- Architecture diagram in README
- Roadmap section detailing future plans
- Support & Community section with all resources
- Missing documentation files (CHANGELOG, CODE_OF_CONDUCT, SECURITY)

### Changed
- Updated all documentation to reference version 0.0.8-M1
- Improved documentation consistency across all files
- Enhanced getting started flow with clearer paths
- Updated ENUM_SUPPORT.md to reflect sealed trait support availability

### Fixed
- Version inconsistencies across documentation files
- Test count discrepancies in documentation
- License reference in CONTRIBUTING.md (now consistently MIT)
- Date references in documentation

## [0.0.8-M1] - 2025-12

### Added
- Sealed trait and sealed class support with `registerSealed[T]`
- Batch sealed trait registration with `registerSealedAll[(T1, T2, ...)]`
- Automatic discriminator field handling (default: `_type`, configurable)
- Polymorphic codec generation for entire sealed hierarchies
- Support for sealed trait, sealed class, and sealed abstract class
- Collections of sealed types (List[Animal], Vector[Shape], etc.)
- Nested sealed traits as case class fields
- Custom discriminator field configuration

### Changed
- Improved compile-time error messages for sealed traits
- Enhanced documentation with sealed trait examples
- Updated FAQ with sealed trait troubleshooting

### Fixed
- Sealed trait codec generation edge cases
- Discriminator field handling in nested structures

## [0.0.7] - 2025-11

### Added
- RegistryBuilder enhancements with convenience methods
- `just[T]` - single type registration with immediate build
- `registerAll[(T1, T2, ...)]` - efficient batch registration
- `registerIf[T](condition)` - conditional registration
- `withTypes[(T1, T2, ...)]` - batch and build in one call
- Builder composition with `++` operator
- State inspection methods: `currentConfig`, `providerCount`, `codecCount`, `isEmpty`
- `hasCodecFor[T]` and `tryGetCodec[T]` for codec availability checks
- `summary` method for debugging builder state
- `ignoreNone` and `encodeNone` fluent API methods

### Changed
- Significant performance improvements for batch registration
- Efficient caching in RegistryBuilder (O(N) instead of O(N²))
- Cleaner configuration API

### Fixed
- Registry build performance issues with multiple registrations

## [0.0.6] - 2025-10

### Added
- Default parameter value support
- Compile-time field inspection
- Annotation processing improvements
- Enhanced error messages with suggestions

### Changed
- Improved macro expansion performance
- Better type resolution for nested structures

### Fixed
- Default value resolution for optional fields
- Annotation extraction edge cases

## [0.0.5] - 2025-09

### Added
- Scala 3 enum support via `EnumValueCodecProvider`
- String-based enum encoding
- Ordinal-based enum encoding
- Custom field enum encoding with `@BsonEnum`
- Collections support (List, Set, Vector, Map)
- UUID and primitive type support (Byte, Short, Char, Float)
- Type-safe field path extraction with `MongoPath`
- Testing utilities with `CodecTestKit`

### Changed
- Improved None handling configuration
- Better BSON type mapping documentation

### Fixed
- Option[T] encoding with NoneHandling.Ignore
- Collection codec resolution

## [0.0.4] - 2025-08

### Added
- Initial public release
- Automatic BSON codec generation for case classes
- Compile-time safe codec derivation
- Support for nested case classes
- Option[T] handling with configurable strategies
- Custom field names via `@BsonProperty`
- Opaque type support
- Basic documentation and examples

### Changed
- Improved codec generation performance
- Better error messages for unsupported types

### Fixed
- Nested case class codec resolution
- Option field encoding edge cases

## [0.0.3] - 2025-07

### Added
- Enhanced RegistryBuilder API
- Support for custom codec providers
- MongoDB interoperability improvements

## [0.0.2] - 2025-06

### Added
- Initial codec generation framework
- Basic type support
- Preliminary documentation

## [0.0.1] - 2025-05

### Added
- Project initialization
- Core macro infrastructure
- Basic build configuration

---

## Links

- [GitHub Releases](https://github.com/mbannour/MongoScala3Codec/releases)
- [GitHub Repository](https://github.com/mbannour/MongoScala3Codec)
- [Documentation](docs/README.md)
- [Migration Guide](docs/MIGRATION.md)

## Legend

- **Added**: New features
- **Changed**: Changes in existing functionality
- **Deprecated**: Soon-to-be removed features
- **Removed**: Removed features
- **Fixed**: Bug fixes
- **Security**: Security vulnerability fixes
