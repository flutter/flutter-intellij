# Integration Tests

> **Note:** The tests in this directory are **experimental**.

## Current Status

- **No CI Integration:** These integration tests do not currently run in our Continuous Integration (CI) environment.
- **Brittle & Environment-Dependent:** While these tests executed successfully on the creator's local setup when written, steps have not been taken to ensure reproducibility across different systems. Additionally, they are not routinely checked or maintained to confirm they still pass.
- **Manual Execution:** They can be run manually, but execution results may vary or fail depending on local configuration.

## Guidance & Strategy

- **Do Not Rely on These Tests:** Do not depend on these integration tests to verify plugin functionality or test for regressions.
- **Ongoing Evaluation:** We are currently evaluating:
  - The overall reliability and stability of the integration testing framework.
  - How effectively we can maintain these tests over time.
  - How best to incorporate integration testing into our overall plugin maintenance and testing strategy.
