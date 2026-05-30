# Keep Private Protocol Notes

## Overview

Keep bikes (C1 mini, C2 Lite, etc.) use a proprietary BLE protocol over
a custom service/characteristic pair.

## BLE Information

| Item | Value |
|------|-------|
| Service UUID | `000000ff-0000-1000-8000-00805f9b34fb` |
| Characteristic UUID | `0000ff01-0000-1000-8000-00805f9b34fb` |
| Properties | Notify / Read / Write / Write Without Response |

## Frame Format

```
[A5 A5 A0] [seq:1B] [payload_len:2B LE] [payload:N] [crc16:2B LE]
```

- **Magic**: `A5 A5 A0` (3 bytes)
- **Seq**: Sequence number, increments per frame (1 byte)
- **Payload Length**: Little-endian u16
- **Payload**: Variable length
- **CRC16**: CRC16-CCITT over everything before it (polynomial 0x1021)

## Heartbeat / Data Payload Format

```
[src_id:4B] [dst_sub:2B] [msg_type:1B] [app_counter:2B LE] [session:2B] [field_cnt:1B] [fixed:01] [cmd:var] [extra:optional]
```

| Field | Value | Notes |
|-------|-------|-------|
| src_id | `32 16 EF 23` | Phone source ID |
| dst_sub | `55 01` | Phone destination |
| msg_type | `93` | Message type |
| app_counter | 2B LE | Increments by 257 per packet |
| session | `00 00` (idle) / `04 00` (active) | Session state |
| field_cnt | `00` | Field count |
| fixed | `01` | Always 01 |
| cmd | `B5 31 30 36 2F 37` (heartbeat) / `B5 31 30 36 2F 34` (ACK) | Command |
| extra | Optional, prefixed with `FF` | e.g., calories |

## Control Command Format

Control commands use a different structure:

```
[src_id:4B] [dst_sub_ctrl:2B] [msg_type:1B] [counter:2B] [cmd_body:var]
```

| Field | Value | Notes |
|-------|-------|-------|
| src_id | `32 16 EF 23` | Phone source ID |
| dst_sub_ctrl | `55 03` | Control destination |
| msg_type | `B0` | Control message type |
| counter | 2 bytes | Increments per command |
| cmd_body | variable | See below |

### Command Body

```
[04 00 00 02] [B5 31 30 36] [command_suffix] [FF 08] [param]
```

| Action | Suffix | Param |
|--------|--------|-------|
| Set resistance | `2F 36` | Resistance level (1-24) |
| Stop | `2F 34` | `01` |
| Wake | `2F 34` | `02` |
| Start/Resume | `2F 34` | `03` |
| Pause | `2F 34` | `04` |

## Notification Data Format

Notifications contain a data marker followed by protobuf-encoded metrics:

```
... [B5 31 30 36 2F 37 FF] [protobuf_data] [CRC:2B]
```

### Protobuf Fields (varint)

| Field ID | Meaning |
|----------|---------|
| 2 | Distance (meters) |
| 3 | Duration (seconds) |
| 4 | Calories |
| 5 | Resistance level |
| 6 | RPM (cadence) |
| 7 | Power (watts) |
| 8 | Status code |

### Status Codes

| Code | Status |
|------|--------|
| 1 | Ready (待机) |
| 2 | Transition (倒计时/退出) |
| 3 | Active (骑行中) |
| 4 | Paused (暂停) |

## Authentication / Handshake

Keep bikes require authentication packets to be sent after BLE connection:

1. Connect to bike via BLE
2. Discover service `00FF` and characteristic `FF01`
3. Enable notifications
4. Send handshake packets (extracted from HCI log of Keep App session)
5. Start heartbeat loop (1 per second)

Handshake packets are extracted from the Keep App's Bluetooth HCI snoop log
using the `identity_gen.py` tool from BikeCon.

## Speed Calculation

Speed is not directly reported; it must be calculated from distance and duration deltas:

```
speed_kmh = (delta_distance / delta_duration) * 3.6
```

Only report non-zero speed/RPM/power when status is Active (3).

## References

- [BikeCon](https://github.com/shinkisan/BikeCon) - Python implementation
- [QDomyos-Zwift](https://github.com/cagnulein/qdomyos-zwift) - C++ implementation
