//! Frame encode/decode for Keep private protocol
//!
//! Frame format:
//!   [A5 A5 A0] [seq:1B] [payload_len:2B LE] [payload] [crc16:2B LE]
//!
//! Payload format (for heartbeat/data):
//!   [src_id:4B] [dst_sub:2B] [msg_type:1B] [app_counter:2B LE]
//!   [session:2B] [field_cnt:1B] [fixed:01] [cmd] [extra]
//!
//! Control payload format:
//!   [src_id:4B] [dst_sub:2B] [msg_type:1B] [counter:2B] [cmd_body]

/// Frame magic header
pub const FRAME_MAGIC: [u8; 3] = [0xA5, 0xA5, 0xA0];

/// Phone source ID
pub const SRC_ID_PHONE: [u8; 4] = [0x32, 0x16, 0xEF, 0x23];

/// Phone destination sub
pub const DST_SUB_PHONE: [u8; 2] = [0x55, 0x01];

/// Control destination sub  
pub const DST_SUB_CTRL: [u8; 2] = [0x55, 0x03];

/// Message type
pub const MSG_TYPE: u8 = 0x93;

/// Session constants
pub const SESSION_IDLE: [u8; 2] = [0x00, 0x00];
pub const SESSION_ACTIVE: [u8; 2] = [0x04, 0x00];

/// Fixed byte in payload
pub const FIXED_BYTE: u8 = 0x01;

/// Command identifiers
pub const CMD_2F37: &[u8] = &[0xB5, 0x31, 0x30, 0x36, 0x2F, 0x37];
pub const CMD_2F34: &[u8] = &[0xB5, 0x31, 0x30, 0x36, 0x2F, 0x34];

/// CRC16-CCITT calculation
pub fn crc16(data: &[u8]) -> u16 {
    let mut crc: u16 = 0x0000;
    for &byte in data {
        crc ^= (byte as u16) << 8;
        for _ in 0..8 {
            if crc & 0x8000 != 0 {
                crc = (crc << 1) ^ 0x1021;
            } else {
                crc <<= 1;
            }
            crc &= 0xFFFF;
        }
    }
    crc
}

/// Build a standard command frame (heartbeat / ACK style)
pub fn build_command_frame(
    seq: u8,
    app_counter: u16,
    cmd: &[u8],
    session: &[u8],
    field_cnt: u8,
    extra: Option<&[u8]>,
) -> Vec<u8> {
    let cnt_bytes = app_counter.to_le_bytes();

    let mut payload = vec![];
    payload.extend_from_slice(&SRC_ID_PHONE);
    payload.extend_from_slice(&DST_SUB_PHONE);
    payload.push(MSG_TYPE);
    payload.extend_from_slice(&cnt_bytes);
    payload.extend_from_slice(session);
    payload.push(field_cnt);
    payload.push(FIXED_BYTE);
    payload.extend_from_slice(cmd);

    if let Some(ext) = extra {
        payload.push(0xFF);
        payload.extend_from_slice(ext);
    }

    build_frame(seq, &payload)
}

/// Build a raw frame with magic header + seq + payload + CRC
pub fn build_frame(seq: u8, payload: &[u8]) -> Vec<u8> {
    let mut body = vec![];
    body.extend_from_slice(&FRAME_MAGIC);
    body.push(seq);
    body.extend_from_slice(&(payload.len() as u16).to_le_bytes());
    body.extend_from_slice(payload);

    let crc = crc16(&body);
    body.extend_from_slice(&crc.to_le_bytes());
    body
}

/// Validate frame CRC
///
/// Frame: [A5 A5 A0] [seq:1B] [payload_len:2B LE] [payload] [crc16:2B LE]
/// Offsets: 0..3= magic, 3=seq, 4..6=len, 6..6+len=payload, then CRC
pub fn validate_frame(data: &[u8]) -> bool {
    // Minimum frame: magic(3) + seq(1) + len(2) + crc(2) = 8
    if data.len() < 8 {
        return false;
    }

    // Check magic
    if data[0] != 0xA5 || data[1] != 0xA5 || data[2] != 0xA0 {
        return false;
    }

    let payload_len = u16::from_le_bytes([data[4], data[5]]) as usize;
    let frame_end = 6 + payload_len; // end of payload (before CRC)

    if data.len() < frame_end + 2 {
        return false;
    }

    let crc_stored = u16::from_le_bytes([data[frame_end], data[frame_end + 1]]);
    let crc_calc = crc16(&data[..frame_end]);

    crc_stored == crc_calc
}

/// Parse frame to extract payload (returns seq and payload bytes)
pub fn parse_frame(data: &[u8]) -> Option<(u8, Vec<u8>)> {
    if !validate_frame(data) {
        return None;
    }

    let seq = data[3];
    let payload_len = u16::from_le_bytes([data[4], data[5]]) as usize;
    let payload = data[6..6 + payload_len].to_vec();

    Some((seq, payload))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_crc16() {
        // Test known CRC value
        let data = vec![0xA5, 0xA5, 0xA0, 0x00];
        let crc = crc16(&data);
        assert_ne!(crc, 0);
    }

    #[test]
    fn test_build_and_validate_frame() {
        let payload = vec![0x01, 0x02, 0x03];
        let frame = build_frame(0, &payload);
        assert!(validate_frame(&frame));
    }

    #[test]
    fn test_parse_roundtrip() {
        let payload = vec![0xDE, 0xAD, 0xBE, 0xEF];
        let frame = build_frame(42, &payload);
        let (seq, parsed) = parse_frame(&frame).unwrap();
        assert_eq!(seq, 42);
        assert_eq!(parsed, payload);
    }

    #[test]
    fn test_validate_invalid_frame() {
        assert!(!validate_frame(&[]));
        assert!(!validate_frame(&[0xA5, 0xA5]));
        assert!(!validate_frame(&[0x00, 0x00, 0x00]));
    }
}
