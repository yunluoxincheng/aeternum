## MODIFIED Requirements

### Requirement: Veto Signal Timeout Handling
The system SHALL return a specific error type when veto window expires.

Previous behavior: Returned generic `AuthenticationFailed` error.

#### Scenario: Veto After Window
- **WHEN** a veto signal is received after the 48-hour window
- **THEN** a `VetoExpired` error is returned

#### Scenario: Veto Within Window
- **WHEN** a veto signal is received within the 48-hour window
- **THEN** the veto is accepted and recovery is terminated
