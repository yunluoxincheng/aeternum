## ADDED Requirements

### Requirement: DeviceHeader Serialization
The system SHALL provide serialization and deserialization methods for `DeviceHeader` to enable cross-module data transfer.

#### Scenario: Serialize DeviceHeader
- **WHEN** a DeviceHeader needs to be transferred via UniFFI
- **THEN** the header is serialized into a byte array containing all fields

#### Scenario: Deserialize DeviceHeader
- **WHEN** a byte array containing DeviceHeader data is received
- **THEN** the header is deserialized into a valid DeviceHeader struct
