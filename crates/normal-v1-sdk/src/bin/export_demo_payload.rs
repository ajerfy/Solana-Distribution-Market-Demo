use normal_v1_sdk::demo_app_payload_json;
use std::env;
use std::fs;

fn main() {
    if let Err(error) = run() {
        eprintln!("failed to export demo payload: {error}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), String> {
    let output_path = env::args().nth(1).ok_or_else(|| {
        "usage: cargo run -p normal-v1-sdk --bin export_demo_payload -- <output-path>".to_string()
    })?;
    let payload = demo_app_payload_json()?;
    fs::write(output_path, payload).map_err(|error| error.to_string())?;
    Ok(())
}
