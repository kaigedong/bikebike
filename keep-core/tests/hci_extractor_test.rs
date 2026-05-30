//! Test HCI log parsing by comparing with Python identity_gen.py semantics.
//!
//! btsnoop file format:
//!   File header: 144 bytes (magic + version + datalink type)
//!   Packet records: [header 24 bytes] [payload N bytes]
//!
//! Python identity_gen.py uses pyshark which parses the full BT protocol stack,
//! extracting only ATT values from Keep device. We scan raw bytes with CRC validation.

use keep_core::*;
use std::fmt::Write;

/// Build a btsnoop file header (144 bytes)
fn btsnoop_header() -> Vec<u8> {
    let mut header = vec![0u8; 144];
    header[0..8].copy_from_slice(b"btsnoop\0");
    header[8..12].copy_from_slice(&1u32.to_be_bytes());
    header[12..16].copy_from_slice(&1002u32.to_be_bytes());
    header
}

/// Wrap payload in a btsnoop packet record (24-byte header + payload)
fn btsnoop_packet(payload: &[u8], direction: u32) -> Vec<u8> {
    let mut pkt = vec![0u8; 24 + payload.len()];
    pkt[0..4].copy_from_slice(&(payload.len() as u32).to_be_bytes());
    pkt[4..8].copy_from_slice(&(payload.len() as u32).to_be_bytes());
    pkt[8..12].copy_from_slice(&direction.to_be_bytes());
    pkt[24..].copy_from_slice(payload);
    pkt
}

/// Build a Keep frame (A5A5A0 + seq + len + inner + CRC)
fn build_keep_frame(seq: u8, inner_payload: &[u8]) -> Vec<u8> {
    let mut body = Vec::new();
    body.extend_from_slice(&[0xA5, 0xA5, 0xA0]);
    body.push(seq);
    body.extend_from_slice(&(inner_payload.len() as u16).to_le_bytes());
    body.extend_from_slice(inner_payload);
    let crc = crc16(&body);
    body.extend_from_slice(&crc.to_le_bytes());
    body
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().fold(String::new(), |mut s, b| {
        write!(s, "{:02x}", b).unwrap(); s
    })
}

#[test]
fn test_extract_from_btsnoop_format() {
    let inner0 = vec![0x32, 0x16, 0xEF, 0x23, 0x55, 0x01, 0x93, 0x00, 0xA4, 0x00, 0x00, 0x01];
    let inner1 = vec![0x32, 0x16, 0xEF, 0x23, 0x55, 0x01, 0x93, 0x01, 0xA5, 0x01, 0x00, 0x01];
    let inner2 = vec![0x32, 0x16, 0xEF, 0x23, 0x55, 0x01, 0x93, 0x02, 0xA6, 0x01, 0x04, 0x00];
    let inner3 = vec![0x32, 0x16, 0xEF, 0x23, 0x55, 0x01, 0x93, 0x03, 0xA7, 0x01, 0x04, 0x00];

    let frame0 = build_keep_frame(0, &inner0);
    let frame1 = build_keep_frame(1, &inner1);
    let frame2 = build_keep_frame(2, &inner2);
    let frame3 = build_keep_frame(3, &inner3);

    let mut file_data = btsnoop_header();
    file_data.extend(btsnoop_packet(&vec![0xDE, 0xAD, 0xBE, 0xEF], 0));
    file_data.extend(btsnoop_packet(&frame0, 0));
    file_data.extend(btsnoop_packet(&frame1, 0));
    file_data.extend(btsnoop_packet(&frame2, 0));
    file_data.extend(btsnoop_packet(&frame3, 0));
    file_data.extend(btsnoop_packet(&vec![0xCA, 0xFE], 1));

    let packets = extract_handshake_packets(&file_data);

    println!("Extracted {} packets:", packets.len());
    for (i, p) in packets.iter().enumerate() {
        println!("  [{}] len={}", i, p.len());
    }

    assert_eq!(packets.len(), 4, "Expected 4 handshake packets, got {}", packets.len());
    assert!(packets[0].starts_with("a5a5a000"), "Packet 0 wrong prefix");
    assert!(packets[1].starts_with("a5a5a001"), "Packet 1 wrong prefix");
    assert!(packets[2].starts_with("a5a5a002"), "Packet 2 wrong prefix");
    assert!(packets[3].starts_with("a5a5a003"), "Packet 3 wrong prefix");

    // Verify CRC
    for (i, h) in packets.iter().enumerate() {
        let bytes: Vec<u8> = (0..h.len()).step_by(2)
            .map(|j| u8::from_str_radix(&h[j..j+2], 16).unwrap())
            .collect();
        assert!(validate_frame(&bytes), "Packet {} CRC invalid", i);
    }
}

#[test]
fn test_no_false_positives_in_noise() {
    let mut file_data = btsnoop_header();
    for _ in 0..10 {
        file_data.extend(btsnoop_packet(&[0xDE, 0xAD, 0xBE, 0xEF], 0));
    }
    let packets = extract_handshake_packets(&file_data);
    assert_eq!(packets.len(), 0, "Should find 0 in noise, got {}", packets.len());
}

#[test]
fn test_dedup_same_prefix() {
    let inner = vec![0x32, 0x16, 0xEF, 0x23, 0x55, 0x01, 0x93, 0x00, 0xA4, 0x00, 0x00, 0x01];
    let frame0 = build_keep_frame(0, &inner);

    let mut file_data = btsnoop_header();
    file_data.extend(btsnoop_packet(&frame0, 0));
    file_data.extend(btsnoop_packet(&frame0, 0));

    let packets = extract_handshake_packets(&file_data);
    assert_eq!(packets.len(), 1, "Should deduplicate same-prefix frames");
}

#[test]
fn test_frames_inside_hci_acl_att_wrapping() {
    // Real btsnoop: [HCI ACL header] [L2CAP header] [ATT Write Cmd header] [Keep Frame]
    let inner = vec![0x32, 0x16, 0xEF, 0x23, 0x55, 0x01, 0x93, 0x00, 0xA4, 0x00, 0x00, 0x01];
    let keep_frame = build_keep_frame(0, &inner);

    // ATT Write Command: opcode 0x52 + handle (FF01 LE) + value
    let mut att = vec![0x52, 0x01, 0xFF];
    att.extend_from_slice(&keep_frame);

    // L2CAP: length (2 LE) + channel ATT=4 (2 LE) + data
    let mut l2cap = vec![];
    l2cap.extend_from_slice(&(att.len() as u16).to_le_bytes());
    l2cap.extend_from_slice(&4u16.to_le_bytes());
    l2cap.extend_from_slice(&att);

    // HCI ACL: handle+flags (2 LE) + length (2 LE) + data
    let mut hci = vec![];
    hci.extend_from_slice(&0x0040u16.to_le_bytes());
    hci.extend_from_slice(&(l2cap.len() as u16).to_le_bytes());
    hci.extend_from_slice(&l2cap);

    let mut file_data = btsnoop_header();
    file_data.extend(btsnoop_packet(&hci, 0));

    let packets = extract_handshake_packets(&file_data);
    assert_eq!(packets.len(), 1, "Should find 1 packet inside HCI+L2CAP+ATT wrapping");
    assert!(packets[0].starts_with("a5a5a000"));
}

#[test]
fn test_vs_python_semantics() {
    // Python does NOT validate CRC, just checks prefix.
    // We validate CRC. This means:
    // - Our result is a strict subset of Python's result (fewer false positives)
    // - Any valid CRC frame we find, Python would also find
    // So our results are always correct if CRC matches.

    // Let's verify: build a frame with correct CRC and one with bad CRC
    let inner = vec![0x32, 0x16, 0xEF, 0x23, 0x55, 0x01, 0x93, 0x00, 0xA4, 0x00, 0x00, 0x01];
    let good_frame = build_keep_frame(0, &inner);

    // Bad CRC frame: same data but wrong CRC
    let mut bad_frame = good_frame.clone();
    let last = bad_frame.len() - 1;
    bad_frame[last] = bad_frame[last].wrapping_add(1); // corrupt CRC

    let mut file_data = btsnoop_header();
    file_data.extend(btsnoop_packet(&good_frame, 0));
    file_data.extend(btsnoop_packet(&bad_frame, 0));

    let packets = extract_handshake_packets(&file_data);

    // We should only find 1 (the good one), Python would find 2
    assert_eq!(packets.len(), 1, "CRC validation should filter out corrupt frame");
    assert!(packets[0].starts_with("a5a5a000"));

    println!("PASS: CRC validation correctly filters corrupt frames (Python wouldn't)");
}
