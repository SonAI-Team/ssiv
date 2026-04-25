# Contributing to SSIV

First off, thank you for considering contributing to Subsampling Scale Image View! It's people like you that make it a great tool for the Android community.

## How Can I Contribute?

### Reporting Bugs
*   **Check the existing issues** to see if the bug has already been reported.
*   If you can't find an open issue that describes the problem, **open a new one**.
*   Use a clear and descriptive title.
*   Provide a **minimal reproducible example** (ideally using the `:sample` app).
*   Include details about your environment (Android version, device model).

### Suggesting Enhancements
*   Open a new issue with the "enhancement" tag.
*   Explain the use case for the feature and why it would be beneficial to the library.

### Development Process

#### Setup
1.  Fork the repository and clone it locally.
2.  Open the project in Android Studio.
3.  The project contains two modules:
    *   `:library`: The core SSIV implementation.
    *   `:sample`: A comprehensive test app demonstrating various features.

#### Coding Standards
This project follows strict code quality rules. Before submitting a Pull Request, please ensure:
*   Your code follows the **Kotlin Style Guide**.
*   You have run static analysis and fixed all issues:
    ```bash
    ./gradlew detekt
    ```
*   The project builds successfully:
    ```bash
    ./gradlew assemble
    ```

#### Refactoring Guidelines
As this library is a modern refactoring of a classic implementation:
*   Keep logic decoupled. Prefer adding logic to internal helper classes (`TileManager`, `MathUtils`, etc.) rather than bloating `SubsamplingScaleImageView.kt`.
*   Maintain backwards compatibility for the public API whenever possible.
*   Leverage Coroutines and Flow for any asynchronous or reactive tasks.

### Pull Request Process
1.  Ensure your branch is up to date with `main`.
2.  Provide a clear description of the changes in your PR.
3.  Reference any related issues.
4.  Once the PR is submitted, it will be reviewed by the maintainers.

## License
By contributing, you agree that your contributions will be licensed under the **Apache License, Version 2.0**.
