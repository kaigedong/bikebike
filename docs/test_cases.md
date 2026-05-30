# Test Cases

## Unit Tests (Rust keep-core)

### Frame Tests
- [x] CRC16 calculation matches known values
- [x] Build frame and validate CRC
- [x] Parse frame roundtrip (build → parse → verify)
- [x] Reject invalid frames (short, wrong magic, bad CRC)

### Protocol Tests
- [x] Varint encode/decode roundtrip
- [x] Protobuf field extraction
- [x] Parse notification with mock data marker
- [x] Reject notification without data marker
- [x] Speed calculation from distance/time deltas
- [x] BikeStatus enum from integer

### Control Tests
- [x] Build set resistance command (valid range 1-24)
- [x] Reject invalid resistance (0, 25)
- [x] Build start/stop/pause/wake commands
- [x] All control commands produce valid frames

### HCI Extractor Tests
- [x] Extract handshake packets from mock data
- [x] Avoid duplicate packets

## Integration Tests (Android)

### BLE Scanning
- [ ] Scan finds Keep_* devices
- [ ] Scan timeout after 10 seconds
- [ ] Duplicate devices are merged

### BLE Connection
- [ ] Connect to known Keep device
- [ ] Discover Keep service and characteristic
- [ ] Enable notifications
- [ ] Handle connection failure gracefully

### Handshake
- [ ] Send handshake packets from identity.json
- [ ] Receive data after successful handshake
- [ ] Log all handshake packets

### Data Parsing
- [ ] Parse real notification data → correct RPM
- [ ] Parse real notification data → correct resistance
- [ ] Parse real notification data → correct status
- [ ] Speed calculation updates correctly

### Control
- [ ] Set resistance changes bike resistance
- [ ] Resistance range enforced (1-24)
- [ ] Multiple rapid resistance changes queue correctly

### UI
- [ ] Display speed/cadence/power/resistance
- [ ] Resistance slider works
- [ ] Log section shows BLE packets
- [ ] Connection state indicator updates

## Manual Test Plan

### Test 1: Basic Connection
1. Open app
2. Tap "Scan"
3. Select Keep device
4. Verify connection indicator turns green
5. Verify log shows handshake packets

### Test 2: Data Display
1. Connect to bike
2. Start pedaling
3. Verify RPM updates
4. Verify speed updates
5. Verify status shows "Active"

### Test 3: Resistance Control
1. Connect to bike
2. Move resistance slider to 10
3. Verify bike resistance changes
4. Verify UI shows resistance = 10
5. Press +1 button, verify resistance = 11

### Test 4: Reconnection
1. Connect to bike
2. Walk away (out of range)
3. Come back
4. Verify automatic reconnection

## Test with Mock Data

For testing without a real bike, use these mock notification packets:

```
// RPM=80, Resistance=10, Status=Active(3)
A5A5A0 01 1000 3216EF23550193 0000 000001 B53130362F37FF 30 50 28 0A 40 03 [CRC]
```
