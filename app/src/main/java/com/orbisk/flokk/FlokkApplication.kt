package com.orbisk.flokk

import android.app.Application
import com.orbisk.flokk.data.HostKeyStore
import com.orbisk.flokk.data.ProfileStore
import com.orbisk.flokk.herdr.HerdrRepository
import com.orbisk.flokk.ssh.SshTransport
import com.orbisk.flokk.ssh.TofuHostKeyVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class FlokkApplication : Application() {

    lateinit var profileStore: ProfileStore
        private set

    lateinit var repository: HerdrRepository
        private set

    /** Shared TOFU pinning store, also used by the one-off "install key on host" connection. */
    lateinit var hostKeyVerifier: TofuHostKeyVerifier
        private set

    override fun onCreate() {
        super.onCreate()
        // Android bundles its own stripped-down provider under the name "BC" (missing
        // modern algorithms like X25519/Ed25519, used by sshj for curve25519-sha256 KEX
        // and ed25519 host/user keys). Swap it for the real org.bouncycastle:bcprov-jdk18on
        // dependency so sshj's `KeyFactory/KeyAgreement.getInstance(alg, "BC")` lookups
        // resolve against a complete implementation instead of failing with
        // "no such algorithm: X25519 for provider BC".
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        profileStore = ProfileStore(this)
        val hostKeyStore = HostKeyStore(this)
        hostKeyVerifier = TofuHostKeyVerifier(hostKeyStore)
        val transport = SshTransport(hostKeyVerifier)
        repository = HerdrRepository(transport, profileStore)
    }
}
