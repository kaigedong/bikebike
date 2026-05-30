//! HCI log parser for extracting Keep handshake packets
//!
//! Extracts authentication handshake packets from btsnoop HCI logs.
//! Handshake packets are identified by their A5A5A0 prefix and
//! sequence numbers 00, 01, 02, 03.

const HANDSHAKE_PREFIXES: [&[u8]; 4] = [
    &[0xA5, 0xA5, 0xA0, 0x00],
    &[0xA5, 0xA5, 0xA0, 0x01],
    &[0xA5, 0xA5, 0xA0, 0x02],
    &[0xA5, 0xA5, 0xA0, 0x03],
];

/// Extract handshake packets from raw HCI log data
/// Returns a list of hex-encoded handshake packets
pub fn extract_handshake_packets(data: &[u8]) -> Vec<String> {
    let mut packets: Vec<String> = Vec::new();

    // Scan for Keep frame patterns in the raw data
    for prefix in &HANDSHAKE_PREFIXES {
        let mut search_start = 0;
        while search_start < data.len() {
            if let Some(idx) = find_subsequence(&data[search_start..], prefix) {
                let abs_idx = search_start + idx;

                // Try to parse the frame to determine its full length
                if abs_idx + 6 <= data.len() {
                    let payload_len = u16::from_le_bytes([
                        data[abs_idx + 4],
                        data[abs_idx + 5],
                    ]) as usize;

                    let frame_len = 3 + 1 + 2 + payload_len + 2; // magic + seq + len + payload + crc
                    if abs_idx + frame_len <= data.len() {
                        let frame = &data[abs_idx..abs_idx + frame_len];

                        // Validate CRC
                        if crate::frame::validate_frame(frame) {
                            let hex: String = frame
                                .iter()
                                .map(|b| format!("{:02x}", b))
                                .collect();

                            // Avoid duplicates with same prefix
                            if !packets.iter().any(|p| p.starts_with(&hex[..8])) {
                                packets.push(hex);
                            }
                        }
                    }
                }
                search_start = abs_idx + 1;
            } else {
                break;
            }
        }
    }

    // Sort by prefix (which contains seq number)
    packets.sort_by(|a, b| a[..8].cmp(&b[..8]));
    packets
}

fn find_subsequence(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    if needle.is_empty() || needle.len() > haystack.len() {
        return None;
    }
    haystack
        .windows(needle.len())
        .position(|window| window == needle)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::frame::build_frame;

    #[test]
    fn test_extract_from_mock_data() {
        // Build two valid frames embedded in some noise
        let frame1 = build_frame(0, &[0x01, 0x02, 0x03]);
        let frame2 = build_frame(1, &[0x04, 0x05, 0x06]);

        let mut data = vec![0xFF, 0xFF, 0xFF]; // noise
        data.extend_from_slice(&frame1);
        data.extend_from_slice(&[0xAA, 0xBB]); // noise
        data.extend_from_slice(&frame2);
        data.extend_from_slice(&[0xCC]); // noise

        let packets = extract_handshake_packets(&data);
        assert_eq!(packets.len(), 2);
    }
}
