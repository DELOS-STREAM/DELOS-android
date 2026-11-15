# Prebuilt native libraries

`openssl/<abi>/libcrypto.a` (OpenSSL 4.0.2) and `opus/<abi>/libopus.a` (opus 1.6.1) are prebuilt static
libraries. They were not built in this repository.

- Built by GitHub Actions in [cgutman/moonlight-mobile-deps](https://github.com/cgutman/moonlight-mobile-deps)
  at commit `419349aa689e8749a9fb91a22dc45531ddd3df45` (run `33597171234`), with the recipes
  `openssl-android.sh`, `opus-android.sh` and `archive-android.sh` of that commit.
- Byte-identical to the files in `moonlight-stream/moonlight-android` `master`.
- Provenance, build flags, what is proven and what is not: [docs/THIRD_PARTY.md](../../../../../docs/THIRD_PARTY.md).
- Checksums: `third_party/CHECKSUMS.sha256`, verified by `sh tools/verify-binaries.sh`.

Replacing a library means replacing all ABIs and the matching `include/` tree together, updating the
checksum file and the row in `docs/THIRD_PARTY.md` in the same commit, and a real stream test on a device.

The files that used to sit here (`Build.txt`, `build-openssl.sh`) described AppVeyor and an OpenSSL 1.1.1
build. Neither produced the tracked libraries, so they were removed.
