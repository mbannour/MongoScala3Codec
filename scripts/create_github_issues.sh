#!/bin/bash
# GitHub Issues and Milestones Creation Script for MongoScala3Codec
# This script creates all milestones, labels, issues, and project board for the roadmap

set -e

REPO="mbannour/MongoScala3Codec"

echo "🚀 Creating GitHub structure for MongoScala3Codec..."
echo "=================================================="
echo ""

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) is not installed. Please install it first:"
    echo "   https://cli.github.com/"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "❌ Not authenticated with GitHub. Please run: gh auth login"
    exit 1
fi

echo "✅ GitHub CLI authenticated"
echo ""

# ============================================
# STEP 1: Create Milestones
# ============================================
echo "📋 Creating Milestones..."
echo "------------------------"

gh api repos/$REPO/milestones -X POST -f title="M1 - Foundation" -f description="Docs, Tests, and Developer Experience (Weeks 1-3)" -f state="closed" -f due_on="2025-10-15T23:59:59Z" || echo "Milestone M1 may already exist"

gh api repos/$REPO/milestones -X POST -f title="M2 - Feature Completeness" -f description="BSON Types and ADT Support (Weeks 4-6)" -f state="closed" -f due_on="2025-10-15T23:59:59Z" || echo "Milestone M2 may already exist"

gh api repos/$REPO/milestones -X POST -f title="M3 - Performance" -f description="Performance and Benchmarking (Weeks 7-9)" -f state="open" -f due_on="2025-11-30T23:59:59Z" || echo "Milestone M3 may already exist"

gh api repos/$REPO/milestones -X POST -f title="M4 - Advanced Features" -f description="Advanced Features and Patterns (Weeks 10-12)" -f state="open" -f due_on="2025-12-31T23:59:59Z" || echo "Milestone M4 may already exist"

gh api repos/$REPO/milestones -X POST -f title="M5 - Ecosystem" -f description="Ecosystem and Integration (Weeks 13-15)" -f state="open" -f due_on="2026-01-31T23:59:59Z" || echo "Milestone M5 may already exist"

gh api repos/$REPO/milestones -X POST -f title="M6 - Production Ready" -f description="Production Readiness and Growth (Weeks 16-18)" -f state="open" -f due_on="2026-02-28T23:59:59Z" || echo "Milestone M6 may already exist"

echo "✅ Milestones created"
echo ""

# ============================================
# STEP 2: Create Labels
# ============================================
echo "🏷️  Creating Labels..."
echo "--------------------"

# Area labels
gh label create "area/docs" --description "Documentation improvements" --color "0075ca" --force
gh label create "area/codecs" --description "Core codec functionality" --color "d73a4a" --force
gh label create "area/testing" --description "Test infrastructure" --color "0e8a16" --force
gh label create "area/dx" --description "Developer experience" --color "fbca04" --force
gh label create "area/perf" --description "Performance improvements" --color "d876e3" --force
gh label create "area/interop" --description "Framework/library integration" --color "c2e0c6" --force
gh label create "area/ci" --description "CI/CD and tooling" --color "1d76db" --force

# Type labels
gh label create "feature" --description "New feature or request" --color "a2eeef" --force
gh label create "enhancement" --description "Improvement to existing feature" --color "84b6eb" --force
gh label create "breaking" --description "Breaking change" --color "b60205" --force
gh label create "good-first-issue" --description "Good for newcomers" --color "7057ff" --force
gh label create "help-wanted" --description "Extra attention needed" --color "008672" --force

# Priority labels
gh label create "priority/critical" --description "Blocking issue" --color "b60205" --force
gh label create "priority/high" --description "High priority" --color "d93f0b" --force
gh label create "priority/medium" --description "Medium priority" --color "fbca04" --force
gh label create "priority/low" --description "Low priority" --color "0e8a16" --force

# Status labels
gh label create "status/blocked" --description "Blocked by other work" --color "d93f0b" --force
gh label create "status/in-progress" --description "Currently being worked on" --color "0075ca" --force
gh label create "status/needs-review" --description "Awaiting code review" --color "fbca04" --force
gh label create "status/needs-docs" --description "Needs documentation" --color "c5def5" --force

echo "✅ Labels created"
echo ""

# ============================================
# STEP 3: Create Issues for M3 (Performance)
# ============================================
echo "📝 Creating M3 Issues (Performance & Benchmarking)..."
echo "----------------------------------------------------"

gh issue create --title "Perf: Create JMH benchmark suite" \
  --milestone "M3 - Performance" \
  --label "area/perf,enhancement" \
  --body "## Goal
Create a comprehensive JMH benchmark suite to measure codec performance.

## Tasks
- [ ] Setup JMH dependencies in build.sbt
- [ ] Create benchmarks for codec generation
- [ ] Create benchmarks for roundtrip serialization (encode + decode)
- [ ] Add memory allocation profiling
- [ ] Document benchmarking methodology

## Acceptance Criteria
- [ ] Benchmark suite compiles and runs
- [ ] Benchmarks cover primitives, collections, nested types
- [ ] Results can be compared across runs
- [ ] CI integration ready"

gh issue create --title "Perf: Optimize primitive type codecs" \
  --milestone "M3 - Performance" \
  --label "area/perf,area/codecs,enhancement" \
  --body "## Goal
Optimize encoding/decoding performance for primitive types.

## Tasks
- [ ] Profile current primitive codec performance
- [ ] Identify hot paths and allocation points
- [ ] Optimize primitive encoding (String, Int, Long, Double, Boolean)
- [ ] Reduce intermediate allocations
- [ ] Benchmark before/after improvements

## Acceptance Criteria
- [ ] 10% improvement in primitive type benchmarks
- [ ] No regression in correctness tests
- [ ] Memory allocations reduced"

gh issue create --title "Perf: Optimize collection codecs" \
  --milestone "M3 - Performance" \
  --label "area/perf,area/codecs,enhancement" \
  --body "## Goal
Optimize collection encoding/decoding performance.

## Tasks
- [ ] Profile List/Seq/Set encoding performance
- [ ] Identify allocation hotspots
- [ ] Optimize array writing
- [ ] Use builders where appropriate
- [ ] Benchmark improvements

## Acceptance Criteria
- [ ] 15% improvement in collection benchmarks
- [ ] No correctness regressions
- [ ] Reduced allocations"

gh issue create --title "Perf: Implement codec caching" \
  --milestone "M3 - Performance" \
  --label "area/perf,area/codecs,enhancement" \
  --body "## Goal
Cache generated codecs to improve registry initialization performance.

## Tasks
- [ ] Design codec cache strategy
- [ ] Implement thread-safe codec caching
- [ ] Add lazy initialization for large registries
- [ ] Benchmark registry initialization before/after
- [ ] Document caching behavior

## Acceptance Criteria
- [ ] Registry initialization 50% faster with caching
- [ ] Thread-safe implementation
- [ ] No memory leaks
- [ ] Documented caching strategy"

gh issue create --title "Perf: Add CI performance regression detection" \
  --milestone "M3 - Performance" \
  --label "area/perf,area/ci,enhancement" \
  --body "## Goal
Automatically detect performance regressions in CI.

## Tasks
- [ ] Run benchmarks on CI
- [ ] Store baseline results
- [ ] Compare current run against baseline
- [ ] Fail build on significant regression (>10%)
- [ ] Generate performance reports

## Acceptance Criteria
- [ ] Benchmarks run automatically on PR
- [ ] Regressions are detected and reported
- [ ] Historical performance data available"

gh issue create --title "Bench: Compare with manual codecs" \
  --milestone "M3 - Performance" \
  --label "area/perf,enhancement" \
  --body "## Goal
Benchmark generated codecs against hand-written implementations.

## Tasks
- [ ] Implement hand-written codecs for common types
- [ ] Benchmark both implementations
- [ ] Document comparison methodology
- [ ] Analyze performance gap
- [ ] Identify optimization opportunities

## Acceptance Criteria
- [ ] Generated codecs within 10% of manual codecs
- [ ] Comparison documented with graphs
- [ ] Gap analysis documented"

gh issue create --title "Bench: Compare with other libraries" \
  --milestone "M3 - Performance" \
  --label "area/perf,enhancement" \
  --body "## Goal
Compare performance with other Scala MongoDB libraries.

## Tasks
- [ ] Setup Circe + BSON bridge benchmark
- [ ] Setup Play JSON + MongoDB benchmark
- [ ] Run comparative benchmarks
- [ ] Document methodology
- [ ] Publish fair comparison results

## Acceptance Criteria
- [ ] Benchmarks include Circe and Play JSON
- [ ] Methodology is documented and fair
- [ ] Results published in performance guide"

gh issue create --title "Docs: Add performance guide" \
  --milestone "M3 - Performance" \
  --label "area/docs,area/perf,enhancement" \
  --body "## Goal
Create comprehensive performance guide for users.

## Tasks
- [ ] Document performance characteristics
- [ ] Add optimization tips
- [ ] Include best practices
- [ ] Add benchmark results
- [ ] Include comparison with alternatives

## Acceptance Criteria
- [ ] Performance guide published in docs/
- [ ] All tips tested and verified
- [ ] Benchmark results included"

echo "✅ M3 issues created"
echo ""

# ============================================
# STEP 4: Create Issues for M4 (Advanced Features)
# ============================================
echo "📝 Creating M4 Issues (Advanced Features)..."
echo "-------------------------------------------"

gh issue create --title "Feature: Add custom codec combinators" \
  --milestone "M4 - Advanced Features" \
  --label "area/codecs,feature" \
  --body "## Goal
Provide combinators for building custom codecs functionally.

## Tasks
- [ ] Implement \`imap\` (isomorphism map)
- [ ] Implement \`emap\` (error-handling map)
- [ ] Implement \`contramap\`
- [ ] Implement \`flatMap\`
- [ ] Add documentation with examples
- [ ] Add tests for all combinators

## Acceptance Criteria
- [ ] All combinators work correctly
- [ ] Users can compose custom codecs
- [ ] Documentation includes examples
- [ ] Tests cover edge cases"

gh issue create --title "Feature: Support codec derivation for third-party types" \
  --milestone "M4 - Advanced Features" \
  --label "area/codecs,feature" \
  --body "## Goal
Allow users to derive codecs for types they don't own.

## Tasks
- [ ] Design extension mechanism
- [ ] Implement derivation for external types
- [ ] Add examples with java.time types
- [ ] Document the mechanism
- [ ] Add tests

## Acceptance Criteria
- [ ] Users can derive codecs for third-party types
- [ ] Mechanism is well-documented
- [ ] Examples include common use cases"

gh issue create --title "Feature: Add validation DSL" \
  --milestone "M4 - Advanced Features" \
  --label "area/codecs,feature" \
  --body "## Goal
Provide a DSL for decode-time validation.

## Tasks
- [ ] Design validation DSL
- [ ] Implement composable validators
- [ ] Add common validators (range, regex, etc.)
- [ ] Integrate with codec derivation
- [ ] Document validation patterns
- [ ] Add comprehensive tests

## Acceptance Criteria
- [ ] Validation DSL is usable and intuitive
- [ ] Validators are composable
- [ ] Error messages are clear
- [ ] Documentation includes examples"

gh issue create --title "Feature: Support polymorphic sealed trait fields" \
  --milestone "M4 - Advanced Features" \
  --label "area/codecs,feature,breaking" \
  --body "## Goal
Full support for sealed trait fields without workarounds.

## Tasks
- [ ] Design automatic discriminator handling
- [ ] Implement polymorphic field codec derivation
- [ ] Handle nested sealed traits
- [ ] Add comprehensive tests
- [ ] Document new capabilities
- [ ] Provide migration guide from workarounds

## Acceptance Criteria
- [ ] Sealed trait fields work automatically
- [ ] No manual discriminator needed
- [ ] Migration guide available
- [ ] All tests pass"

gh issue create --title "Feature: Support recursive types" \
  --milestone "M4 - Advanced Features" \
  --label "area/codecs,feature" \
  --body "## Goal
Handle self-referential and mutually recursive types.

## Tasks
- [ ] Detect recursive type patterns
- [ ] Implement lazy codec initialization
- [ ] Add cycle detection
- [ ] Test tree structures
- [ ] Test graph structures
- [ ] Document limitations

## Acceptance Criteria
- [ ] Recursive types roundtrip correctly
- [ ] No stack overflow
- [ ] Cycles detected and handled
- [ ] Documentation clear on limitations"

gh issue create --title "Docs: Add event sourcing patterns guide" \
  --milestone "M4 - Advanced Features" \
  --label "area/docs,enhancement" \
  --body "## Goal
Document event sourcing patterns with MongoScala3Codec.

## Tasks
- [ ] Document event serialization patterns
- [ ] Add event versioning strategies
- [ ] Include upcasting examples
- [ ] Add complete example project
- [ ] Document best practices

## Acceptance Criteria
- [ ] Event sourcing guide published
- [ ] Examples are runnable
- [ ] Best practices documented"

gh issue create --title "Docs: Add CQRS patterns guide" \
  --milestone "M4 - Advanced Features" \
  --label "area/docs,enhancement" \
  --body "## Goal
Document CQRS patterns with MongoDB.

## Tasks
- [ ] Document command/query separation
- [ ] Add read model codec examples
- [ ] Include projection patterns
- [ ] Add complete example
- [ ] Document best practices

## Acceptance Criteria
- [ ] CQRS guide published
- [ ] Examples demonstrate patterns
- [ ] Best practices clear"

gh issue create --title "Feature: Add streaming codec support" \
  --milestone "M4 - Advanced Features" \
  --label "area/codecs,area/interop,feature" \
  --body "## Goal
Support streaming BSON operations with FS2 and Akka Streams.

## Tasks
- [ ] Add FS2 integration
- [ ] Add Akka Streams integration
- [ ] Document streaming patterns
- [ ] Add examples for both libraries
- [ ] Add streaming tests

## Acceptance Criteria
- [ ] FS2 streaming works
- [ ] Akka Streams integration works
- [ ] Documentation includes examples"

gh issue create --title "Feature: Add bulk operation helpers" \
  --milestone "M4 - Advanced Features" \
  --label "area/codecs,area/interop,enhancement" \
  --body "## Goal
Simplify bulk insert and update operations.

## Tasks
- [ ] Add bulk insert helpers
- [ ] Add bulk update helpers
- [ ] Add ordered/unordered options
- [ ] Document bulk patterns
- [ ] Add tests and examples

## Acceptance Criteria
- [ ] Bulk operations simplified
- [ ] Documentation clear
- [ ] Examples demonstrate usage"

echo "✅ M4 issues created"
echo ""

# ============================================
# STEP 5: Create Issues for M5 (Ecosystem)
# ============================================
echo "📝 Creating M5 Issues (Ecosystem & Integration)..."
echo "-------------------------------------------------"

gh issue create --title "Interop: Add ZIO integration guide" \
  --milestone "M5 - Ecosystem" \
  --label "area/interop,area/docs,enhancement" \
  --body "## Goal
Provide comprehensive ZIO integration guide.

## Tasks
- [ ] Create ZIO examples
- [ ] Document resource management patterns
- [ ] Document error handling with ZIO
- [ ] Add ZLayer examples
- [ ] Create complete example project

## Acceptance Criteria
- [ ] ZIO guide published
- [ ] Examples are runnable
- [ ] Best practices documented"

gh issue create --title "Interop: Add Cats Effect integration guide" \
  --milestone "M5 - Ecosystem" \
  --label "area/interop,area/docs,enhancement" \
  --body "## Goal
Provide comprehensive Cats Effect integration guide.

## Tasks
- [ ] Create Cats Effect examples
- [ ] Document Resource management
- [ ] Document error handling
- [ ] Add complete example project

## Acceptance Criteria
- [ ] Cats Effect guide published
- [ ] Examples work with CE3
- [ ] Best practices documented"

gh issue create --title "Interop: Add Akka/Pekko integration guide" \
  --milestone "M5 - Ecosystem" \
  --label "area/interop,area/docs,enhancement" \
  --body "## Goal
Provide Akka and Pekko integration guide.

## Tasks
- [ ] Document actor persistence patterns
- [ ] Add Akka Streams integration
- [ ] Add Pekko examples
- [ ] Create example project

## Acceptance Criteria
- [ ] Akka/Pekko guide published
- [ ] Stream integration works
- [ ] Examples demonstrate patterns"

gh issue create --title "Interop: Add Play Framework integration guide" \
  --milestone "M5 - Ecosystem" \
  --label "area/interop,area/docs,enhancement" \
  --body "## Goal
Provide Play Framework integration guide.

## Tasks
- [ ] Document Play JSON integration
- [ ] Add controller examples
- [ ] Document dependency injection
- [ ] Create example Play app

## Acceptance Criteria
- [ ] Play guide published
- [ ] Examples work with Play 2.9+
- [ ] DI patterns documented"

gh issue create --title "Interop: Add http4s integration examples" \
  --milestone "M5 - Ecosystem" \
  --label "area/interop,area/docs,enhancement" \
  --body "## Goal
Provide http4s integration examples.

## Tasks
- [ ] Create REST API examples
- [ ] Document error handling
- [ ] Add entity encoding/decoding
- [ ] Create complete example

## Acceptance Criteria
- [ ] http4s examples available
- [ ] Examples demonstrate REST patterns
- [ ] Error handling clear"

gh issue create --title "Interop: Add Tapir integration examples" \
  --milestone "M5 - Ecosystem" \
  --label "area/interop,area/docs,enhancement" \
  --body "## Goal
Provide Tapir integration examples.

## Tasks
- [ ] Document endpoint definitions
- [ ] Add schema derivation examples
- [ ] Create API examples
- [ ] Document OpenAPI generation

## Acceptance Criteria
- [ ] Tapir examples available
- [ ] Schema derivation works
- [ ] OpenAPI generation documented"

gh issue create --title "DX: Create Giter8 basic template" \
  --milestone "M5 - Ecosystem" \
  --label "area/dx,enhancement,good-first-issue" \
  --body "## Goal
Create a basic Giter8 template for quick project setup.

## Tasks
- [ ] Create g8 template structure
- [ ] Add basic project setup
- [ ] Include simple example
- [ ] Test template generation
- [ ] Publish to GitHub

## Acceptance Criteria
- [ ] Template generates working project
- [ ] Instructions are clear
- [ ] Example compiles and runs"

gh issue create --title "DX: Create ZIO MongoDB starter" \
  --milestone "M5 - Ecosystem" \
  --label "area/dx,area/interop,enhancement" \
  --body "## Goal
Create a complete ZIO starter project.

## Tasks
- [ ] Setup ZIO project structure
- [ ] Add MongoDB integration
- [ ] Include best practices
- [ ] Add tests
- [ ] Document setup

## Acceptance Criteria
- [ ] Starter project available on GitHub
- [ ] All dependencies configured
- [ ] README with instructions"

gh issue create --title "DX: Create Cats Effect MongoDB starter" \
  --milestone "M5 - Ecosystem" \
  --label "area/dx,area/interop,enhancement" \
  --body "## Goal
Create a complete Cats Effect starter project.

## Tasks
- [ ] Setup Cats Effect project
- [ ] Add MongoDB integration
- [ ] Include best practices
- [ ] Add tests
- [ ] Document setup

## Acceptance Criteria
- [ ] Starter available on GitHub
- [ ] CE3 dependencies configured
- [ ] README with instructions"

gh issue create --title "DX: Create Play Framework MongoDB starter" \
  --milestone "M5 - Ecosystem" \
  --label "area/dx,area/interop,enhancement" \
  --body "## Goal
Create a complete Play Framework starter.

## Tasks
- [ ] Setup Play project
- [ ] Add MongoDB integration
- [ ] Include controllers and services
- [ ] Add tests
- [ ] Document setup

## Acceptance Criteria
- [ ] Play starter available
- [ ] Fully configured
- [ ] README with instructions"

gh issue create --title "DX: Create full-stack example app" \
  --milestone "M5 - Ecosystem" \
  --label "area/dx,enhancement" \
  --body "## Goal
Create a full-stack example application.

## Tasks
- [ ] Setup backend (Scala + MongoDB)
- [ ] Setup frontend (Scala.js or React)
- [ ] Implement real-world features
- [ ] Add authentication
- [ ] Document architecture
- [ ] Deploy demo

## Acceptance Criteria
- [ ] Full-stack app available
- [ ] Backend uses MongoScala3Codec
- [ ] Demo is deployed and accessible"

gh issue create --title "Outreach: Write blog post on zero-boilerplate MongoDB" \
  --milestone "M5 - Ecosystem" \
  --label "area/docs,enhancement" \
  --body "## Goal
Write and publish blog post about getting started.

## Tasks
- [ ] Write blog post content
- [ ] Include code examples
- [ ] Explain benefits
- [ ] Add visuals/diagrams
- [ ] Publish on blog/Medium

## Acceptance Criteria
- [ ] Blog post published
- [ ] Shared on social media
- [ ] Link added to docs"

gh issue create --title "Outreach: Write blog post on type-safe queries" \
  --milestone "M5 - Ecosystem" \
  --label "area/docs,enhancement" \
  --body "## Goal
Write blog post about type-safe MongoDB queries.

## Tasks
- [ ] Write blog post content
- [ ] Demonstrate type safety
- [ ] Include examples
- [ ] Publish

## Acceptance Criteria
- [ ] Blog post published
- [ ] Examples are clear
- [ ] Link added to docs"

gh issue create --title "Outreach: Create video tutorial" \
  --milestone "M5 - Ecosystem" \
  --label "area/docs,enhancement,help-wanted" \
  --body "## Goal
Create a YouTube video tutorial for getting started.

## Tasks
- [ ] Script the video
- [ ] Record screen and narration
- [ ] Edit video
- [ ] Upload to YouTube
- [ ] Link from docs

## Acceptance Criteria
- [ ] Video published on YouTube
- [ ] Clear audio and visuals
- [ ] Linked from documentation"

gh issue create --title "Outreach: Submit conference talk proposal" \
  --milestone "M5 - Ecosystem" \
  --label "area/docs,enhancement,help-wanted" \
  --body "## Goal
Submit talk proposal to Scala conference.

## Tasks
- [ ] Write talk proposal
- [ ] Create slide deck
- [ ] Submit to Scala Days / ScalaCon
- [ ] Practice presentation

## Acceptance Criteria
- [ ] Proposal submitted
- [ ] Slides prepared
- [ ] If accepted, talk delivered"

echo "✅ M5 issues created"
echo ""

# ============================================
# STEP 6: Create Issues for M6 (Production Ready)
# ============================================
echo "📝 Creating M6 Issues (Production Readiness)..."
echo "----------------------------------------------"

gh issue create --title "Docs: Add production deployment guide" \
  --milestone "M6 - Production Ready" \
  --label "area/docs,enhancement" \
  --body "## Goal
Create comprehensive production deployment guide.

## Tasks
- [ ] Document connection pooling best practices
- [ ] Add error handling strategies
- [ ] Document health check patterns
- [ ] Add graceful shutdown guide
- [ ] Include monitoring setup

## Acceptance Criteria
- [ ] Production guide published
- [ ] All major concerns covered
- [ ] Examples included"

gh issue create --title "Docs: Add monitoring and observability guide" \
  --milestone "M6 - Production Ready" \
  --label "area/docs,enhancement" \
  --body "## Goal
Document monitoring and observability patterns.

## Tasks
- [ ] Document metrics integration
- [ ] Add logging patterns
- [ ] Document tracing setup
- [ ] Include dashboard examples

## Acceptance Criteria
- [ ] Observability guide published
- [ ] Metrics examples work
- [ ] Logging patterns clear"

gh issue create --title "Docs: Add multi-tenancy patterns" \
  --milestone "M6 - Production Ready" \
  --label "area/docs,enhancement" \
  --body "## Goal
Document multi-tenancy patterns with MongoDB.

## Tasks
- [ ] Document database-per-tenant pattern
- [ ] Document collection-per-tenant pattern
- [ ] Add tenant isolation examples
- [ ] Document security considerations

## Acceptance Criteria
- [ ] Multi-tenancy patterns documented
- [ ] Security covered
- [ ] Examples included"

gh issue create --title "Interop: Add Micrometer metrics integration" \
  --milestone "M6 - Production Ready" \
  --label "area/interop,enhancement" \
  --body "## Goal
Provide Micrometer metrics integration.

## Tasks
- [ ] Add Micrometer dependencies
- [ ] Instrument codec operations
- [ ] Add performance tracking
- [ ] Create example project
- [ ] Document setup

## Acceptance Criteria
- [ ] Metrics integration works
- [ ] Example available
- [ ] Documentation clear"

gh issue create --title "Interop: Add OpenTelemetry tracing integration" \
  --milestone "M6 - Production Ready" \
  --label "area/interop,enhancement" \
  --body "## Goal
Provide OpenTelemetry tracing integration.

## Tasks
- [ ] Add OTel dependencies
- [ ] Trace codec operations
- [ ] Trace MongoDB queries
- [ ] Create example
- [ ] Document setup

## Acceptance Criteria
- [ ] Tracing integration works
- [ ] Example available
- [ ] Documentation clear"

gh issue create --title "Interop: Add structured logging examples" \
  --milestone "M6 - Production Ready" \
  --label "area/interop,area/docs,enhancement,good-first-issue" \
  --body "## Goal
Provide structured logging examples.

## Tasks
- [ ] Add Log4j2 example
- [ ] Add Logback example
- [ ] Document logging patterns
- [ ] Include error logging

## Acceptance Criteria
- [ ] Logging examples available
- [ ] Both Log4j2 and Logback covered
- [ ] Patterns documented"

gh issue create --title "Community: Enhance contributor guide" \
  --milestone "M6 - Production Ready" \
  --label "area/dx,enhancement,good-first-issue" \
  --body "## Goal
Make contributor guide comprehensive and welcoming.

## Tasks
- [ ] Document development setup
- [ ] Add testing guidelines
- [ ] Document PR process
- [ ] Add code style guide
- [ ] Include tips for first PR

## Acceptance Criteria
- [ ] Contributor guide complete
- [ ] New contributors can set up easily
- [ ] PR process is clear"

gh issue create --title "Community: Create issue templates" \
  --milestone "M6 - Production Ready" \
  --label "area/dx,enhancement,good-first-issue" \
  --body "## Goal
Create GitHub issue templates for consistency.

## Tasks
- [ ] Create bug report template
- [ ] Create feature request template
- [ ] Create question template
- [ ] Test templates

## Acceptance Criteria
- [ ] Templates in .github/ISSUE_TEMPLATE/
- [ ] All templates include necessary fields
- [ ] Templates are user-friendly"

gh issue create --title "Community: Create PR template" \
  --milestone "M6 - Production Ready" \
  --label "area/dx,enhancement,good-first-issue" \
  --body "## Goal
Create PR template with checklist.

## Tasks
- [ ] Design PR template
- [ ] Include checklist items
- [ ] Add description format
- [ ] Test template

## Acceptance Criteria
- [ ] PR template in .github/
- [ ] Checklist includes tests, docs
- [ ] Template is helpful"

gh issue create --title "Community: Add community guidelines" \
  --milestone "M6 - Production Ready" \
  --label "area/dx,enhancement,good-first-issue" \
  --body "## Goal
Establish community guidelines and code of conduct.

## Tasks
- [ ] Write code of conduct
- [ ] Add communication guidelines
- [ ] Document values
- [ ] Add to repository

## Acceptance Criteria
- [ ] Community guidelines published
- [ ] Code of conduct adopted
- [ ] Guidelines linked from README"

gh issue create --title "Community: Establish release cadence" \
  --milestone "M6 - Production Ready" \
  --label "area/dx,enhancement" \
  --body "## Goal
Establish regular release cadence and process.

## Tasks
- [ ] Define release schedule
- [ ] Document versioning strategy
- [ ] Setup changelog automation
- [ ] Document release process
- [ ] Create release checklist

## Acceptance Criteria
- [ ] Release cadence documented
- [ ] Changelog automated
- [ ] Process is repeatable"

gh issue create --title "Docs: Add troubleshooting playbook" \
  --milestone "M6 - Production Ready" \
  --label "area/docs,enhancement" \
  --body "## Goal
Create comprehensive troubleshooting playbook.

## Tasks
- [ ] Document common production issues
- [ ] Add debugging techniques
- [ ] Include performance troubleshooting
- [ ] Add decision trees
- [ ] Include runbooks

## Acceptance Criteria
- [ ] Troubleshooting playbook published
- [ ] Common issues covered
- [ ] Debugging steps clear"

gh issue create --title "Docs: Add security best practices" \
  --milestone "M6 - Production Ready" \
  --label "area/docs,enhancement" \
  --body "## Goal
Document security best practices for MongoDB.

## Tasks
- [ ] Document connection security (TLS)
- [ ] Add data validation patterns
- [ ] Document injection prevention
- [ ] Add authentication patterns
- [ ] Include authorization examples

## Acceptance Criteria
- [ ] Security guide published
- [ ] All major concerns covered
- [ ] Examples demonstrate patterns"

echo "✅ M6 issues created"
echo ""

# ============================================
# STEP 7: Create Project Board
# ============================================
echo "📊 Creating Project Board..."
echo "--------------------------"

echo "⚠️  Note: GitHub CLI doesn't fully support Projects v2 yet."
echo "   You'll need to create the project board manually at:"
echo "   https://github.com/$REPO/projects/new"
echo ""
echo "Project Board Configuration:"
echo "  Name: MongoScala3Codec Development"
echo "  Columns:"
echo "    1. Backlog (all new issues)"
echo "    2. Ready (dependencies met)"
echo "    3. In Progress (being worked on)"
echo "    4. Review (in code review)"
echo "    5. Done (completed)"
echo ""

# ============================================
# DONE
# ============================================
echo "✅ GitHub structure creation complete!"
echo ""
echo "📋 Summary:"
echo "  - 6 Milestones created (M1-M6)"
echo "  - 23 Labels created (area, type, priority, status)"
echo "  - 45+ Issues created across M3-M6"
echo "  - Project board instructions provided"
echo ""
echo "🎯 Next Steps:"
echo "  1. Create project board manually (see link above)"
echo "  2. Add all issues to Backlog column"
echo "  3. Close/mark M1 and M2 issues as complete"
echo "  4. Review and adjust issue priorities"
echo "  5. Assign issues to milestones as needed"
echo ""
echo "🚀 Ready to start working on the roadmap!"

