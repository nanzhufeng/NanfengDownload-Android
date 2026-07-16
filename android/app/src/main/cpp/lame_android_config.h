#pragma once

// Autoconf supplies these portable aliases in upstream desktop builds. Android's
// Bionic headers don't expose the glibc-specific ieee754_* typedef names.
typedef float ieee754_float32_t;
typedef double ieee754_float64_t;
