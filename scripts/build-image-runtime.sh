#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT before running this script}"
NDK_VERSION="${ANDROID_NDK_VERSION:-27.2.12479018}"
CMAKE_BIN="${SDK_ROOT}/cmake/3.22.1/bin/cmake"
export PATH="$(dirname "${CMAKE_BIN}"):${PATH}"
TOOLCHAIN="${SDK_ROOT}/ndk/${NDK_VERSION}/build/cmake/android.toolchain.cmake"
BUILD_DIR="${ROOT_DIR}/.image-runtime-build/arm64-v8a"

if [[ "${CLEAN:-0}" == "1" ]]; then rm -rf "${BUILD_DIR}"; fi
mkdir -p "${BUILD_DIR}"

"${CMAKE_BIN}" -S "${ROOT_DIR}/third_party/stable-diffusion.cpp" -B "${BUILD_DIR}" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN}" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-26 \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_FLAGS_RELEASE="-O0 -g0" \
  -DSD_BUILD_EXAMPLES=OFF \
  -DSD_BUILD_SHARED_LIBS=ON \
  -DSD_BUILD_SHARED_GGML_LIB=ON \
  -DSD_VULKAN=OFF \
  -DSD_OPENCL=OFF \
  -DSD_CUDA=OFF \
  -DSD_METAL=OFF \
  -DSD_WEBP=OFF \
  -DSD_WEBM=OFF

CMAKE_BUILD_PARALLEL_LEVEL=1 "${CMAKE_BIN}" --build "${BUILD_DIR}" --target stable-diffusion
printf 'Image runtime built at %s\n' "${BUILD_DIR}"
find "${BUILD_DIR}" -type f \( -name 'libstable-diffusion.so' -o -name 'libggml*.so' \) -print
