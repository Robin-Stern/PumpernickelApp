package com.pumpernickel.di

import com.pumpernickel.domain.progresspic.PhotoVault
import org.koin.mp.KoinPlatform

/**
 * Phase 17 — Swift-side accessor for the iOS PhotoVault singleton.
 * Mirrors `AchievementGalleryKoinHelper` so Swift never touches the Koin DSL.
 *
 * The Phase 17 SwiftUI surfaces (gallery / viewer) load image bytes by reading
 * the well-known sandbox path directly via FileManager — `PhotoVault.read` is a
 * `suspend fun` and the K/N callback bridge adds no value for a 1-shot read of
 * a known path. This helper is exposed for explicit deletes and for any future
 * Swift call site that wants the canonical singleton instead of resolving its
 * own no-arg `PhotoVault()`.
 */
class PhotoVaultKoinHelper {
    fun getPhotoVault(): PhotoVault = KoinPlatform.getKoin().get()
}
