    - Code of conduct
    - Communication guidelines
    - Labels: `area/dx`, `enhancement`, `good-first-issue`
    - Acceptance: Community guidelines published

60. **Community: Establish release cadence**
    - Release schedule
    - Versioning strategy
    - Changelog automation
    - Labels: `area/dx`, `enhancement`
    - Acceptance: Release process documented

### Documentation (area/docs, milestone:M6)
61. **Docs: Add troubleshooting playbook**
    - Common production issues
    - Debugging techniques
    - Performance troubleshooting
    - Labels: `area/docs`, `enhancement`
    - Acceptance: Troubleshooting playbook published

62. **Docs: Add security best practices**
    - Connection security
    - Data validation
    - Injection prevention
    - Labels: `area/docs`, `enhancement`
    - Acceptance: Security guide published

---

## Labels to Create

### Area Labels
- `area/docs` - Documentation improvements (color: #0075ca)
- `area/codecs` - Core codec functionality (color: #d73a4a)
- `area/testing` - Test infrastructure (color: #0e8a16)
- `area/dx` - Developer experience (color: #fbca04)
- `area/perf` - Performance improvements (color: #d876e3)
- `area/interop` - Framework/library integration (color: #c2e0c6)
- `area/ci` - CI/CD and tooling (color: #1d76db)

### Type Labels
- `bug` - Something isn't working (color: #d73a4a)
- `feature` - New feature or request (color: #a2eeef)
- `enhancement` - Improvement to existing feature (color: #84b6eb)
- `breaking` - Breaking change (color: #b60205)
- `good-first-issue` - Good for newcomers (color: #7057ff)
- `help-wanted` - Extra attention needed (color: #008672)

### Priority Labels
- `priority/critical` - Blocking issue (color: #b60205)
- `priority/high` - High priority (color: #d93f0b)
- `priority/medium` - Medium priority (color: #fbca04)
- `priority/low` - Low priority (color: #0e8a16)

### Status Labels
- `status/blocked` - Blocked by other work (color: #d93f0b)
- `status/in-progress` - Currently being worked on (color: #0075ca)
- `status/needs-review` - Awaiting code review (color: #fbca04)
- `status/needs-docs` - Needs documentation (color: #c5def5)

---

## Project Board Structure

**Board Name:** MongoScala3Codec Development

**Columns:**
1. **Backlog** - All planned issues not yet started
2. **Ready** - Issues ready to be worked on (all dependencies met)
3. **In Progress** - Currently being worked on
4. **Review** - In code review or testing
5. **Done** - Completed and merged

**Automation:**
- New issues → Backlog
- Issues assigned → In Progress
- PR opened → Review
- PR merged → Done
- Issues closed → Done

---

## Issue Creation Order

1. Create all 6 milestones (M1-M6)
2. Create all labels
3. Create issues 1-62 with appropriate labels and milestones
4. Create project board
5. Add all issues to Backlog column
6. Close/mark complete issues 1-17 (M1 & M2)
# GitHub Issues Generation Plan

This document contains all issues to be created for MongoScala3Codec milestones M1-M6.

---

## M1 — Foundation: Docs, Tests, DX (✅ COMPLETE)

All M1 issues are complete and can be closed or used as reference.

### Documentation (area/docs, milestone:M1)
1. **Docs: Add Quickstart (5-minute guide)** ✅
   - Create step-by-step setup guide
   - Include nested case class example
   - Include ADT (sealed trait) example
   - All code must be copy-paste ready
   - Acceptance: New users can persist/fetch data without reading source

2. **Docs: Add Feature Overview with code samples** ✅
   - Document all supported types
   - Include examples for each feature
   - Cover NoneHandling configuration
   - Acceptance: All features documented with runnable examples

3. **Docs: Add "How It Works" (Scala 3 derivation details)** ✅
   - Explain macro-based code generation
   - Detail compile-time field inspection
   - Cover performance characteristics
   - Acceptance: Advanced users understand internals

4. **Docs: Add Migration Guide** ✅
   - From manual codec implementations
   - From Scala 2 MongoDB libraries
   - From ReactiveMongo
   - Acceptance: Migration paths clear for common scenarios

5. **Docs: Add FAQ and Troubleshooting** ✅
   - Common compile errors with fixes
   - Runtime issues with solutions
   - Configuration questions
   - Acceptance: Common issues self-serviceable

### Testing (area/testing, milestone:M1)
6. **Testing: Add Property-Based Tests (ScalaCheck)** ✅
   - Round-trip tests for primitives
   - Tests for nested case classes
   - Tests for options and collections
   - Tests for both NoneHandling modes
   - Acceptance: Property tests pass for all supported types

7. **Testing: Add Golden Tests** ✅
   - Exact BSON structure verification
   - Tests for ADTs with discriminators
   - Tests for nested structures
   - Tests for collections
   - Acceptance: BSON structure matches expected format

8. **Testing: Add Integration Tests (Testcontainers)** ✅
   - Real MongoDB insert/query operations
   - Nested case class persistence
   - ADT persistence
   - Acceptance: Integration tests pass with real MongoDB

### Developer Experience (area/dx, milestone:M1)
9. **DX: Improve compile-time error messages** ✅
   - Clear errors for unsupported types
   - Hints for missing givens
   - Acceptance: Compile errors are actionable

10. **DX: Simplify import story** ✅
    - Minimal imports required
    - Extension methods available
    - Acceptance: <5 import lines needed for basic usage

### CI/Tooling (area/ci, milestone:M1)
11. **CI: Setup GitHub Actions matrix** ✅
    - Test Scala 3.3.x, 3.4.x, 3.7.x
    - Test on JDK 17
    - Run unit and integration tests
    - Acceptance: Tests pass across all versions

12. **CI: Add code coverage (scoverage)** ✅
    - Setup scoverage plugin
    - Target ≥85% coverage
    - Badge in README
    - Acceptance: Coverage tracked and visible

13. **CI: Add Scalafmt and Scalafix** ✅
    - Configure scalafmt
    - Configure scalafix rules
    - Fatal warnings on CI
    - Acceptance: Code quality enforced

---

## M2 — Feature Completeness: BSON & ADTs (✅ COMPLETE)

All M2 issues are complete.

### Types & Derivation (area/codecs, milestone:M2)
14. **Codecs: Document full BSON type mapping table** ✅
    - 35+ type mappings documented
    - Quick reference table
    - Edge cases covered
    - Acceptance: All BSON types documented

15. **Codecs: Add Either and Try support** ✅
    - Discriminated union encoding
    - Success/Failure handling
    - Acceptance: Either and Try roundtrip correctly

16. **Codecs: Document ADT patterns and strategies** ✅
    - Manual discriminator pattern
    - Custom discriminator fields
    - Validation patterns
    - Acceptance: ADT usage clear with examples

### Interop (area/interop, milestone:M2)
17. **Interop: Document MongoDB driver integration** ✅
    - CRUD operations
    - Query patterns
    - Aggregation pipelines
    - Transactions
    - Acceptance: All major operations documented

---

## M3 — Performance & Benchmarking (📋 PLANNED)

### Performance (area/perf, milestone:M3)
18. **Perf: Create JMH benchmark suite**
    - Codec generation benchmarks
    - Roundtrip serialization benchmarks
    - Memory allocation profiling
    - Labels: `area/perf`, `enhancement`
    - Acceptance: Benchmark suite runs on CI

19. **Perf: Optimize primitive type codecs**
    - Profile primitive encoding/decoding
    - Optimize hot paths
    - Reduce allocations
    - Labels: `area/perf`, `area/codecs`, `enhancement`
    - Acceptance: 10% improvement in primitive benchmarks

20. **Perf: Optimize collection codecs**
    - Profile List/Seq/Set encoding
    - Reduce intermediate allocations
    - Labels: `area/perf`, `area/codecs`, `enhancement`
    - Acceptance: 15% improvement in collection benchmarks

21. **Perf: Implement codec caching**
    - Cache generated codecs
    - Lazy initialization for large registries
    - Labels: `area/perf`, `area/codecs`, `enhancement`
    - Acceptance: Registry initialization 50% faster

22. **Perf: Add CI performance regression detection**
    - Run benchmarks on CI
    - Compare against baseline
    - Fail on significant regression
    - Labels: `area/perf`, `area/ci`, `enhancement`
    - Acceptance: Performance regressions caught automatically

### Benchmarking (area/perf, milestone:M3)
23. **Bench: Compare with manual codecs**
    - Benchmark hand-written codecs
    - Document comparison methodology
    - Labels: `area/perf`, `enhancement`
    - Acceptance: Within 10% of manual codecs

24. **Bench: Compare with other libraries**
    - Benchmark against Circe + BSON bridge
    - Benchmark against Play JSON
    - Labels: `area/perf`, `enhancement`
    - Acceptance: Competitive performance documented

### Documentation (area/docs, area/perf, milestone:M3)
25. **Docs: Add performance guide**
    - Performance characteristics
    - Optimization tips
    - Best practices
    - Labels: `area/docs`, `area/perf`, `enhancement`
    - Acceptance: Performance guide published

---

## M4 — Advanced Features & Patterns (📋 PLANNED)

### Advanced Features (area/codecs, milestone:M4)
26. **Feature: Add custom codec combinators**
    - Implement imap, emap
    - Implement contramap, flatMap
    - Labels: `area/codecs`, `feature`
    - Acceptance: Users can compose custom codecs

27. **Feature: Support codec derivation for third-party types**
    - Extension mechanism for external types
    - Example with java.time types
    - Labels: `area/codecs`, `feature`
    - Acceptance: Third-party type derivation documented

28. **Feature: Add validation DSL**
    - Decode-time validation
    - Composable validators
    - Labels: `area/codecs`, `feature`
    - Acceptance: Validation DSL usable and documented

29. **Feature: Support polymorphic sealed trait fields**
    - Full polymorphic ADT support
    - Automatic discriminator handling
    - Labels: `area/codecs`, `feature`, `breaking`
    - Acceptance: Sealed trait fields work without workarounds

30. **Feature: Support recursive types**
    - Handle self-referential types
    - Cycle detection
    - Labels: `area/codecs`, `feature`
    - Acceptance: Recursive types roundtrip correctly

### Patterns & Utilities (area/codecs, area/docs, milestone:M4)
31. **Docs: Add event sourcing patterns guide**
    - Event serialization patterns
    - Versioning strategies
    - Labels: `area/docs`, `enhancement`
    - Acceptance: Event sourcing guide published

32. **Docs: Add CQRS patterns guide**
    - Command/query separation
    - Read model codecs
    - Labels: `area/docs`, `enhancement`
    - Acceptance: CQRS guide published

33. **Feature: Add streaming codec support**
    - FS2 integration
    - Akka Streams integration
    - Labels: `area/codecs`, `area/interop`, `feature`
    - Acceptance: Streaming examples work

34. **Feature: Add bulk operation helpers**
    - Bulk insert helpers
    - Bulk update helpers
    - Labels: `area/codecs`, `area/interop`, `enhancement`
    - Acceptance: Bulk operations simplified

---

## M5 — Ecosystem & Integration (📋 PLANNED)

### Framework Integration (area/interop, milestone:M5)
35. **Interop: Add ZIO integration guide**
    - ZIO examples
    - Resource management
    - Error handling
    - Labels: `area/interop`, `area/docs`, `enhancement`
    - Acceptance: ZIO guide published with examples

36. **Interop: Add Cats Effect integration guide**
    - Cats Effect examples
    - Resource management
    - Error handling
    - Labels: `area/interop`, `area/docs`, `enhancement`
    - Acceptance: Cats Effect guide published

37. **Interop: Add Akka/Pekko integration guide**
    - Actor persistence examples
    - Stream integration
    - Labels: `area/interop`, `area/docs`, `enhancement`
    - Acceptance: Akka guide published

38. **Interop: Add Play Framework integration guide**
    - Play JSON integration
    - Controller examples
    - Labels: `area/interop`, `area/docs`, `enhancement`
    - Acceptance: Play guide published

39. **Interop: Add http4s integration examples**
    - REST API examples
    - Error handling
    - Labels: `area/interop`, `area/docs`, `enhancement`
    - Acceptance: http4s examples available

40. **Interop: Add Tapir integration examples**
    - Endpoint definitions
    - Schema derivation
    - Labels: `area/interop`, `area/docs`, `enhancement`
    - Acceptance: Tapir examples available

### Starter Templates (area/dx, milestone:M5)
41. **DX: Create Giter8 basic template**
    - Basic project structure
    - Simple example
    - Labels: `area/dx`, `enhancement`, `good-first-issue`
    - Acceptance: g8 template published

42. **DX: Create ZIO MongoDB starter**
    - Complete ZIO example
    - Best practices included
    - Labels: `area/dx`, `area/interop`, `enhancement`
    - Acceptance: ZIO starter published

43. **DX: Create Cats Effect MongoDB starter**
    - Complete Cats Effect example
    - Best practices included
    - Labels: `area/dx`, `area/interop`, `enhancement`
    - Acceptance: Cats Effect starter published

44. **DX: Create Play Framework MongoDB starter**
    - Complete Play example
    - Best practices included
    - Labels: `area/dx`, `area/interop`, `enhancement`
    - Acceptance: Play starter published

45. **DX: Create full-stack example app**
    - Backend + frontend
    - Real-world patterns
    - Labels: `area/dx`, `enhancement`
    - Acceptance: Full-stack example deployed

### Content & Outreach (area/docs, milestone:M5)
46. **Outreach: Write blog post on zero-boilerplate MongoDB**
    - Getting started content
    - Benefits explanation
    - Labels: `area/docs`, `enhancement`
    - Acceptance: Blog post published

47. **Outreach: Write blog post on type-safe queries**
    - Type-safe query patterns
    - Compile-time safety
    - Labels: `area/docs`, `enhancement`
    - Acceptance: Blog post published

48. **Outreach: Create video tutorial**
    - YouTube getting started video
    - Screen recording + narration
    - Labels: `area/docs`, `enhancement`, `help-wanted`
    - Acceptance: Video published

49. **Outreach: Submit conference talk proposal**
    - Scala conference submission
    - Slides prepared
    - Labels: `area/docs`, `enhancement`, `help-wanted`
    - Acceptance: Talk proposal submitted

---

## M6 — Production Readiness & Growth (📋 PLANNED)

### Production Readiness (area/docs, milestone:M6)
50. **Docs: Add production deployment guide**
    - Connection pooling
    - Error handling strategies
    - Health checks
    - Labels: `area/docs`, `enhancement`
    - Acceptance: Production guide published

51. **Docs: Add monitoring and observability guide**
    - Metrics integration
    - Logging patterns
    - Tracing
    - Labels: `area/docs`, `enhancement`
    - Acceptance: Observability guide published

52. **Docs: Add multi-tenancy patterns**
    - Database-per-tenant
    - Collection-per-tenant
    - Labels: `area/docs`, `enhancement`
    - Acceptance: Multi-tenancy patterns documented

### Observability (area/interop, milestone:M6)
53. **Interop: Add Micrometer metrics integration**
    - Codec operation metrics
    - Performance tracking
    - Labels: `area/interop`, `enhancement`
    - Acceptance: Metrics example available

54. **Interop: Add OpenTelemetry tracing integration**
    - Trace codec operations
    - MongoDB query tracing
    - Labels: `area/interop`, `enhancement`
    - Acceptance: Tracing example available

55. **Interop: Add structured logging examples**
    - Log4j2 examples
    - Logback examples
    - Labels: `area/interop`, `area/docs`, `enhancement`, `good-first-issue`
    - Acceptance: Logging examples available

### Community & Growth (area/dx, milestone:M6)
56. **Community: Enhance contributor guide**
    - Development setup
    - Testing guidelines
    - PR process
    - Labels: `area/dx`, `enhancement`, `good-first-issue`
    - Acceptance: Contributor guide comprehensive

57. **Community: Create issue templates**
    - Bug report template
    - Feature request template
    - Question template
    - Labels: `area/dx`, `enhancement`, `good-first-issue`
    - Acceptance: Issue templates in place

58. **Community: Create PR template**
    - Checklist for PRs
    - Description format
    - Labels: `area/dx`, `enhancement`, `good-first-issue`
    - Acceptance: PR template in place

59. **Community: Add community guidelines**

