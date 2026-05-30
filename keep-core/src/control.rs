//! Control command builders for Keep bike
//!
//! Control commands use a different payload format:
//!   [src_id:4B] [dst_sub_ctrl:2B] [msg_type:1B] [counter:2B] [cmd_body]

use crate::frame::{crc16, DST_SUB_CTRL, SRC_ID_PHONE};

/// Resistance counter (increments per control command)
const CTRL_MSG_TYPE: u8 = 0xB0;

/// Control command base (the "106/" prefix encoded)
const CTRL_BASE: &[u8] = b"\x04\x00\x00\x02\xB5\x31\x30\x36";

/// Command suffixes
const CMD_SET_RESISTANCE: &[u8] = b"\x2F\x36\xFF\x08";
const CMD_GENERIC: &[u8] = b"\x2F\x34\xFF\x08";

/// Control action codes
pub const ACTION_STOP: u8 = 0x01;
pub const ACTION_WAKE: u8 = 0x02;
pub const ACTION_START: u8 = 0x03;
pub const ACTION_PAUSE: u8 = 0x04;

/// Build a control packet with proper framing
fn build_control_packet(seq: u8, ctrl_counter: u16, cmd_body: &[u8]) -> Vec<u8> {
    let mut payload = vec![];
    payload.extend_from_slice(&SRC_ID_PHONE);
    payload.extend_from_slice(&DST_SUB_CTRL);
    payload.push(CTRL_MSG_TYPE);
    payload.push((ctrl_counter & 0xFF) as u8);
    payload.push(((ctrl_counter + 9) & 0xFF) as u8);
    payload.extend_from_slice(cmd_body);

    let mut header = vec![
        0xA5, 0xA5, 0xA0, seq,
    ];
    header.extend_from_slice(&(payload.len() as u16).to_le_bytes());
    header.extend_from_slice(&payload);

    let crc = crc16(&header);
    header.extend_from_slice(&crc.to_le_bytes());
    header
}

/// Build set resistance command (level 1-24)
pub fn build_set_resistance(seq: u8, ctrl_counter: u16, level: u8) -> Result<Vec<u8>, String> {
    if level < 1 || level > 24 {
        return Err(format!("Resistance must be 1-24, got {}", level));
    }

    let mut cmd_body = vec![];
    cmd_body.extend_from_slice(CTRL_BASE);
    cmd_body.extend_from_slice(CMD_SET_RESISTANCE);
    cmd_body.push(level);

    Ok(build_control_packet(seq, ctrl_counter, &cmd_body))
}

/// Build start/resume command
pub fn build_start_command(seq: u8, ctrl_counter: u16) -> Vec<u8> {
    let mut cmd_body = vec![];
    cmd_body.extend_from_slice(CTRL_BASE);
    cmd_body.extend_from_slice(CMD_GENERIC);
    cmd_body.push(ACTION_START);

    build_control_packet(seq, ctrl_counter, &cmd_body)
}

/// Build stop command
pub fn build_stop_command(seq: u8, ctrl_counter: u16) -> Vec<u8> {
    let mut cmd_body = vec![];
    cmd_body.extend_from_slice(CTRL_BASE);
    cmd_body.extend_from_slice(CMD_GENERIC);
    cmd_body.push(ACTION_STOP);

    build_control_packet(seq, ctrl_counter, &cmd_body)
}

/// Build pause command
pub fn build_pause_command(seq: u8, ctrl_counter: u16) -> Vec<u8> {
    let mut cmd_body = vec![];
    cmd_body.extend_from_slice(CTRL_BASE);
    cmd_body.extend_from_slice(CMD_GENERIC);
    cmd_body.push(ACTION_PAUSE);

    build_control_packet(seq, ctrl_counter, &cmd_body)
}

/// Build wake command
pub fn build_wake_command(seq: u8, ctrl_counter: u16) -> Vec<u8> {
    let mut cmd_body = vec![];
    cmd_body.extend_from_slice(CTRL_BASE);
    cmd_body.extend_from_slice(CMD_GENERIC);
    cmd_body.push(ACTION_WAKE);

    build_control_packet(seq, ctrl_counter, &cmd_body)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::frame::validate_frame;

    #[test]
    fn test_build_set_resistance_valid() {
        let result = build_set_resistance(0, 1, 10);
        assert!(result.is_ok());
        let frame = result.unwrap();
        assert!(validate_frame(&frame));
        // Should contain the resistance level
        assert!(frame.contains(&10u8));
    }

    #[test]
    fn test_build_set_resistance_invalid() {
        assert!(build_set_resistance(0, 1, 0).is_err());
        assert!(build_set_resistance(0, 1, 25).is_err());
    }

    #[test]
    fn test_build_start_command() {
        let frame = build_start_command(0, 1);
        assert!(validate_frame(&frame));
        // Last byte before CRC should be ACTION_START (0x03)
        let len = frame.len();
        assert_eq!(frame[len - 3], ACTION_START);
    }

    #[test]
    fn test_build_stop_command() {
        let frame = build_stop_command(0, 1);
        assert!(validate_frame(&frame));
    }

    #[test]
    fn test_build_pause_command() {
        let frame = build_pause_command(0, 1);
        assert!(validate_frame(&frame));
    }

    #[test]
    fn test_build_wake_command() {
        let frame = build_wake_command(0, 1);
        assert!(validate_frame(&frame));
    }
}
