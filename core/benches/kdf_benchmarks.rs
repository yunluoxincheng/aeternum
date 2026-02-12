//! Argon2id KDF Performance Benchmarks
//!
//! These benchmarks measure the performance of Argon2id key derivation
//! with various parameter configurations.
//!
//! Target: Key derivation should complete in <500ms for OWASP default parameters
//! on typical Android devices.
//!
//! Run with: `cargo bench --bench kdf_benchmarks`

use aeternum_core::crypto::kdf::{Argon2idConfig, Argon2idKDF};
use criterion::{black_box, criterion_group, criterion_main, BenchmarkId, Criterion};

/// Benchmark Argon2id with minimal parameters (fast, for unit tests)
fn bench_argon2id_minimal(c: &mut Criterion) {
    let config = Argon2idConfig::new(8192, 1, 1, 32); // 8MB, 1 iteration, 1 thread
    let kdf = Argon2idKDF::with_config(config).unwrap();
    let salt = [0u8; 16];
    let password = b"benchmark-password";

    c.bench_function("argon2id_8MB_t1_p1", |b| {
        b.iter(|| kdf.derive_key(black_box(password), black_box(&salt)))
    });
}

/// Benchmark Argon2id with moderate parameters
fn bench_argon2id_moderate(c: &mut Criterion) {
    let config = Argon2idConfig::new(16384, 2, 2, 32); // 16MB, 2 iterations, 2 threads
    let kdf = Argon2idKDF::with_config(config).unwrap();
    let salt = [0u8; 16];
    let password = b"benchmark-password";

    c.bench_function("argon2id_16MB_t2_p2", |b| {
        b.iter(|| kdf.derive_key(black_box(password), black_box(&salt)))
    });
}

/// Benchmark Argon2id with OWASP 2024 default parameters
///
/// Note: This benchmark may be slow (~200-500ms per iteration)
/// depending on hardware.
fn bench_argon2id_owasp_default(c: &mut Criterion) {
    let kdf = Argon2idKDF::new(); // OWASP defaults: 64MB, t=3, p=4
    let salt = [0u8; 16];
    let password = b"benchmark-password";

    let mut group = c.benchmark_group("argon2id_owasp");
    // Use fewer samples due to longer iteration time
    group.sample_size(10);

    group.bench_function("64MB_t3_p4", |b| {
        b.iter(|| kdf.derive_key(black_box(password), black_box(&salt)))
    });

    group.finish();
}

/// Benchmark impact of different memory costs
fn bench_argon2id_memory_scaling(c: &mut Criterion) {
    let salt = [0u8; 16];
    let password = b"benchmark-password";

    let memory_costs = [8192u32, 16384, 32768]; // 8MB, 16MB, 32MB

    let mut group = c.benchmark_group("argon2id_memory_scaling");

    for m_cost in memory_costs {
        let config = Argon2idConfig::new(m_cost, 1, 1, 32);
        let kdf = Argon2idKDF::with_config(config).unwrap();

        group.bench_with_input(
            BenchmarkId::from_parameter(format!("{}KB", m_cost)),
            &m_cost,
            |b, _| b.iter(|| kdf.derive_key(black_box(password), black_box(&salt))),
        );
    }

    group.finish();
}

/// Benchmark impact of different time costs (iterations)
fn bench_argon2id_time_scaling(c: &mut Criterion) {
    let salt = [0u8; 16];
    let password = b"benchmark-password";

    let time_costs = [1u32, 2, 3];

    let mut group = c.benchmark_group("argon2id_time_scaling");

    for t_cost in time_costs {
        let config = Argon2idConfig::new(8192, t_cost, 1, 32);
        let kdf = Argon2idKDF::with_config(config).unwrap();

        group.bench_with_input(
            BenchmarkId::from_parameter(format!("t{}", t_cost)),
            &t_cost,
            |b, _| b.iter(|| kdf.derive_key(black_box(password), black_box(&salt))),
        );
    }

    group.finish();
}

/// Benchmark impact of different output lengths
fn bench_argon2id_output_length(c: &mut Criterion) {
    let salt = [0u8; 16];
    let password = b"benchmark-password";

    let output_lengths = [16usize, 32, 64, 128];

    let mut group = c.benchmark_group("argon2id_output_length");

    for output_len in output_lengths {
        let config = Argon2idConfig::new(8192, 1, 1, output_len);
        let kdf = Argon2idKDF::with_config(config).unwrap();

        group.bench_with_input(
            BenchmarkId::from_parameter(format!("{}bytes", output_len)),
            &output_len,
            |b, _| b.iter(|| kdf.derive_key(black_box(password), black_box(&salt))),
        );
    }

    group.finish();
}

/// Benchmark with different password lengths
fn bench_argon2id_password_length(c: &mut Criterion) {
    let salt = [0u8; 16];
    let config = Argon2idConfig::new(8192, 1, 1, 32);
    let kdf = Argon2idKDF::with_config(config).unwrap();

    let passwords: Vec<Vec<u8>> = vec![
        vec![0x42; 8],    // 8 bytes
        vec![0x42; 32],   // 32 bytes
        vec![0x42; 128],  // 128 bytes
        vec![0x42; 1024], // 1024 bytes
    ];

    let mut group = c.benchmark_group("argon2id_password_length");

    for password in &passwords {
        group.bench_with_input(
            BenchmarkId::from_parameter(format!("{}bytes", password.len())),
            password,
            |b, pwd| b.iter(|| kdf.derive_key(black_box(pwd), black_box(&salt))),
        );
    }

    group.finish();
}

criterion_group!(
    benches,
    bench_argon2id_minimal,
    bench_argon2id_moderate,
    bench_argon2id_memory_scaling,
    bench_argon2id_time_scaling,
    bench_argon2id_output_length,
    bench_argon2id_password_length,
    // OWASP benchmark last since it's slow
    bench_argon2id_owasp_default,
);

criterion_main!(benches);
