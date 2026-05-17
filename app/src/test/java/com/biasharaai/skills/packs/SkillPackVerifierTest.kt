package com.biasharaai.skills.packs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillPackVerifierTest {

  private val verifier = SkillPackVerifier()

  @Test
  fun verify_acceptsValidSignature() {
    val (publicKey, privateKey) = SkillPackTestSupport.generateEcKeyPair()
    val signed = SkillPackTestSupport.sign(SkillPackTestSupport.sampleManifest(), privateKey)
    val pem = SkillPackTestSupport.publicKeyPemBody(publicKey)

    assertEquals(SkillPackVerifier.VerifyResult.Valid, verifier.verify(signed, pem))
  }

  @Test
  fun verify_rejectsTamperedPayload() {
    val (publicKey, privateKey) = SkillPackTestSupport.generateEcKeyPair()
    val signed = SkillPackTestSupport.sign(SkillPackTestSupport.sampleManifest(), privateKey)
    val tampered = signed.copy(version = "9.9.9")
    val pem = SkillPackTestSupport.publicKeyPemBody(publicKey)

    val result = verifier.verify(tampered, pem)
    assertTrue(result is SkillPackVerifier.VerifyResult.Invalid)
  }

  @Test
  fun verify_rejectsMissingSignature() {
    val (publicKey, _) = SkillPackTestSupport.generateEcKeyPair()
    val unsigned = SkillPackTestSupport.sampleManifest()
    val pem = SkillPackTestSupport.publicKeyPemBody(publicKey)

    val result = verifier.verify(unsigned, pem)
    assertTrue(result is SkillPackVerifier.VerifyResult.Invalid)
  }
}
