# Agent instructions

- Use Kotlin and keep the Android Studio project buildable. Keep the GitHub repository public when publishing is authorized. Keep secrets out of Git.
- The course calls the architecture MVVC. Preserve the existing MVVM-style separation of Activity/View, ViewModel and Repository until the course defines a different MVVC contract. Keep authorization and business rules on the Ktor REST API.
- Apply SOLID, DRY and KISS. Remove dead code. Reuse existing Singleton objects and demonstrate OOP through useful interfaces, abstract classes, polymorphism and multiple interface implementation.
- Preserve PostgreSQL relations including students and student_items. Use backward-compatible migrations and transactions for related writes.
- Maintain end-to-end CRUD where appropriate. Keep soft delete and hard delete distinct and enforce ownership and membership on the server.
- For changes in behavior, write regression tests first where practical. Cover domain unit tests, API and PostgreSQL integration tests, and mutation tests for important rules when mutation tooling is available. Report which checks actually ran.
- Use separate commits for independently reviewable changes. Run :domain:test :server:test and :app:assembleDebug before delivery.
