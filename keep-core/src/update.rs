//! Version and update check utilities

/// Current app version
pub const VERSION: &str = "0.1.0";

/// GitHub repo for update checks
pub const GITHUB_REPO: &str = "kaigedong/bikebike";

/// GitHub release API URL for latest
pub fn latest_release_url() -> String {
    format!("https://api.github.com/repos/{}/releases/tags/latest", GITHUB_REPO)
}

/// Direct APK download URL (always points to latest release asset)
pub fn latest_apk_url() -> String {
    format!("https://github.com/{}/releases/latest/download/bikebike-latest.apk", GITHUB_REPO)
}

/// Parse version string to comparable tuple (major, minor, patch)
pub fn parse_version(v: &str) -> Option<(u32, u32, u32)> {
    let parts: Vec<&str> = v.trim_start_matches('v').split('.').collect();
    if parts.len() != 3 { return None; }
    Some((
        parts[0].parse().ok()?,
        parts[1].parse().ok()?,
        parts[2].parse().ok()?,
    ))
}

/// Check if remote version is newer than current
pub fn is_newer(current: &str, remote: &str) -> bool {
    let c = parse_version(current);
    let r = parse_version(remote);
    match (c, r) {
        (Some(c), Some(r)) => r > c,
        _ => false,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_version() {
        assert_eq!(parse_version("1.2.3"), Some((1, 2, 3)));
        assert_eq!(parse_version("v1.2.3"), Some((1, 2, 3)));
        assert_eq!(parse_version("0.1.0"), Some((0, 1, 0)));
        assert_eq!(parse_version("invalid"), None);
    }

    #[test]
    fn test_is_newer() {
        assert!(is_newer("1.0.0", "1.0.1"));
        assert!(is_newer("1.0.0", "1.1.0"));
        assert!(is_newer("1.0.0", "2.0.0"));
        assert!(!is_newer("1.0.0", "1.0.0"));
        assert!(!is_newer("1.1.0", "1.0.9"));
    }

    #[test]
    fn test_urls() {
        assert!(latest_release_url().contains("kaigedong/bikebike"));
        assert!(latest_apk_url().contains("bikebike-latest.apk"));
    }
}
