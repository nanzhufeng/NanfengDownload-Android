package com.nanzhufeng.videodownloader.data.network

/** Platform validation probes may fail while the requested media host remains reachable. */
internal fun downloadNetworkAvailable(
    hasDefaultNetwork: Boolean,
    hasInternetCapability: Boolean,
    hasCaptivePortal: Boolean,
): Boolean = hasDefaultNetwork && hasInternetCapability && !hasCaptivePortal
