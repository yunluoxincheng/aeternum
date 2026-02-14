## MODIFIED Requirements

### Requirement: Complete Key Derivation in AUP Preparation
The system SHALL perform complete key derivation during AUP preparation phase.

Previous behavior: Placeholder implementation returned mock data.

#### Scenario: AUP Key Derivation
- **WHEN** preparing an epoch upgrade
- **THEN** the current VK is decrypted, new DEK is derived, and VK is re-encrypted with new DEK

#### Scenario: AUP Blob Serialization
- **WHEN** key derivation is complete
- **THEN** the data is serialized into a VaultBlob for shadow writing
