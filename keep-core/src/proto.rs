//! Protobuf-like data parser for Keep bike metrics
//!
//! Notification data contains a marker [B5 31 30 36 2F 37 FF]
//! followed by protobuf-encoded fields:
//!   Field 2 = distance (varint)
//!   Field 3 = duration (varint)
//!   Field 4 = calories (varint)
//!   Field 5 = resistance (varint)
//!   Field 6 = rpm (varint)
//!   Field 7 = power (varint)
//!   Field 8 = status (varint)

/// Data marker for metrics in notification
const DATA_MARKER: &[u8] = &[0xB5, 0x31, 0x30, 0x36, 0x2F, 0x37, 0xFF];

/// Protobuf field IDs
pub const FIELD_DISTANCE: u8 = 2;
pub const FIELD_DURATION: u8 = 3;
pub const FIELD_CALORIES: u8 = 4;
pub const FIELD_RESISTANCE: u8 = 5;
pub const FIELD_RPM: u8 = 6;
pub const FIELD_POWER: u8 = 7;
pub const FIELD_STATUS: u8 = 8;

/// Keep bike status codes
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BikeStatus {
    Unknown = 0,
    Ready = 1,       // 待机/随时可骑
    Transition = 2,  // 倒计时或退出阶段
    Active = 3,      // 正在骑行
    Paused = 4,      // 手动暂停
}

impl From<u64> for BikeStatus {
    fn from(v: u64) -> Self {
        match v {
            1 => BikeStatus::Ready,
            2 => BikeStatus::Transition,
            3 => BikeStatus::Active,
            4 => BikeStatus::Paused,
            _ => BikeStatus::Unknown,
        }
    }
}

/// Parsed bike metrics
#[derive(Debug, Clone)]
pub struct BikeMetrics {
    pub rpm: u32,
    pub power: u32,
    pub duration: u32,
    pub distance: u32,
    pub resistance: u32,
    pub calories: f32,
    pub speed: f32,
    pub status: BikeStatus,
    pub raw_hex: String,
}

/// Read a protobuf varint from data starting at `start`, returns (value, bytes_consumed)
fn read_varint(data: &[u8], start: usize) -> (u64, usize) {
    let mut result: u64 = 0;
    let mut shift: u32 = 0;
    let mut count = 0;

    for i in start..data.len() {
        let byte = data[i];
        result |= ((byte & 0x7F) as u64) << shift;
        count += 1;
        if byte & 0x80 == 0 || count >= 10 {
            break;
        }
        shift += 7;
    }

    (result, count)
}

/// Write a protobuf varint
pub fn write_varint(value: u64) -> Vec<u8> {
    let mut result = vec![];
    let mut v = value;
    loop {
        let byte = (v & 0x7F) as u8;
        v >>= 7;
        if v == 0 {
            result.push(byte);
            break;
        }
        result.push(byte | 0x80);
    }
    result
}

/// Dynamically decode protobuf fields from raw bytes
fn decode_protobuf(pb_data: &[u8]) -> std::collections::HashMap<u8, u64> {
    let mut results = std::collections::HashMap::new();
    let mut ptr = 0;

    while ptr < pb_data.len() {
        let tag = pb_data[ptr];
        let field_num = tag >> 3;
        let wire_type = tag & 0x07;
        ptr += 1;

        match wire_type {
            0 => {
                // Varint
                let (val, consumed) = read_varint(pb_data, ptr);
                results.insert(field_num, val);
                ptr += consumed;
            }
            2 => {
                // Length-delimited
                let (length, consumed) = read_varint(pb_data, ptr);
                ptr += consumed;
                // Store length-delimited fields as a special marker (field_num | 0x80)
                // We only care about varint fields for now
                ptr += length as usize;
            }
            _ => {
                // Skip unknown wire types
                ptr += 1;
            }
        }

        if ptr >= pb_data.len() {
            break;
        }
    }

    results
}

/// Parse notification data from Keep bike BLE characteristic
/// Returns BikeMetrics if valid data found, None otherwise
pub fn parse_notification(data: &[u8]) -> Option<BikeMetrics> {
    // Find the data marker
    let marker_idx = find_subsequence(data, DATA_MARKER)?;

    // Extract protobuf data after marker, skip last 2 bytes (CRC or footer)
    let start_ptr = marker_idx + DATA_MARKER.len();
    if start_ptr >= data.len() {
        return None;
    }

    let pb_data = &data[start_ptr..data.len().saturating_sub(2)];
    let fields = decode_protobuf(pb_data);

    let rpm = fields.get(&FIELD_RPM).copied().unwrap_or(0) as u32;
    let power = fields.get(&FIELD_POWER).copied().unwrap_or(0) as u32;
    let duration = fields.get(&FIELD_DURATION).copied().unwrap_or(0) as u32;
    let distance = fields.get(&FIELD_DISTANCE).copied().unwrap_or(0) as u32;
    let resistance = fields.get(&FIELD_RESISTANCE).copied().unwrap_or(1) as u32;
    let calories = fields.get(&FIELD_CALORIES).copied().unwrap_or(0) as f32;
    let status_code = fields.get(&FIELD_STATUS).copied().unwrap_or(0);
    let status = BikeStatus::from(status_code);

    // Only report rpm/power when active
    let (final_rpm, final_power) = if status == BikeStatus::Active {
        (rpm, power)
    } else {
        (0, 0)
    };

    Some(BikeMetrics {
        rpm: final_rpm,
        power: final_power,
        duration,
        distance,
        resistance,
        calories,
        speed: 0.0, // Speed is calculated externally from distance/time deltas
        status,
        raw_hex: hex_encode(data),
    })
}

/// Calculate speed from distance and duration deltas
/// Returns speed in km/h
pub fn calculate_speed(
    prev_distance: u32,
    curr_distance: u32,
    prev_duration: u32,
    curr_duration: u32,
) -> f32 {
    if curr_duration <= prev_duration {
        return 0.0;
    }
    let delta_d = curr_distance.saturating_sub(prev_distance) as f32;
    let delta_t = (curr_duration - prev_duration) as f32;
    if delta_t > 0.0 && delta_d >= 0.0 {
        (delta_d / delta_t) * 3.6
    } else {
        0.0
    }
}

fn find_subsequence(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    if needle.is_empty() || needle.len() > haystack.len() {
        return None;
    }
    haystack
        .windows(needle.len())
        .position(|window| window == needle)
}

fn hex_encode(data: &[u8]) -> String {
    data.iter().map(|b| format!("{:02x}", b)).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_varint_roundtrip() {
        let values = [0, 1, 127, 128, 255, 300, 65535, 100000];
        for &v in &values {
            let encoded = write_varint(v);
            let (decoded, _) = read_varint(&encoded, 0);
            assert_eq!(decoded, v, "varint roundtrip failed for {}", v);
        }
    }

    #[test]
    fn test_decode_protobuf_simple() {
        // Field 6 (rpm=100): tag = 6<<3|0 = 0x30, value = 100 = 0x64
        let data = vec![0x30, 0x64];
        let fields = decode_protobuf(&data);
        assert_eq!(fields.get(&6), Some(&100));
    }

    #[test]
    fn test_parse_notification_with_mock_data() {
        // Build a mock notification with just the marker + protobuf data
        // (parse_notification doesn't need a valid frame header, it searches for marker)
        let mut data = vec![0xFF, 0xFE, 0xFD]; // some noise
        data.extend_from_slice(DATA_MARKER);
        // Append protobuf fields
        // Field 6 (rpm=80): tag=0x30, varint=0x50
        data.push(0x30); // field 6, varint
        data.push(0x50); // 80
        // Field 5 (resistance=10): tag=0x28, varint=0x0A
        data.push(0x28); // field 5, varint
        data.push(0x0A); // 10
        // Field 8 (status=3=Active): tag=0x40, varint=0x03
        data.push(0x40); // field 8, varint
        data.push(0x03); // 3
        // Append 2 dummy footer bytes (parse skips last 2)
        data.push(0x00);
        data.push(0x00);

        let result = parse_notification(&data);
        assert!(result.is_some());
        let metrics = result.unwrap();
        assert_eq!(metrics.rpm, 80);
        assert_eq!(metrics.resistance, 10);
        assert_eq!(metrics.status, BikeStatus::Active);
    }

    #[test]
    fn test_parse_notification_no_marker() {
        let data = vec![0x00, 0x01, 0x02, 0x03];
        assert!(parse_notification(&data).is_none());
    }

    #[test]
    fn test_calculate_speed() {
        // 10 meters in 10 seconds = 3.6 km/h
        let speed = calculate_speed(0, 10, 0, 10);
        assert!((speed - 3.6).abs() < 0.01);
    }

    #[test]
    fn test_calculate_speed_zero_delta() {
        let speed = calculate_speed(10, 10, 5, 5);
        assert_eq!(speed, 0.0);
    }

    #[test]
    fn test_bike_status_from_int() {
        assert_eq!(BikeStatus::from(1), BikeStatus::Ready);
        assert_eq!(BikeStatus::from(2), BikeStatus::Transition);
        assert_eq!(BikeStatus::from(3), BikeStatus::Active);
        assert_eq!(BikeStatus::from(4), BikeStatus::Paused);
        assert_eq!(BikeStatus::from(99), BikeStatus::Unknown);
    }
}
