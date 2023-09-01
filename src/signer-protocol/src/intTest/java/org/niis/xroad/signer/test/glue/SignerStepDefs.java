/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.signer.test.glue;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import io.cucumber.java.en.Step;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.util.encoders.Base64;
import org.junit.jupiter.api.Assertions;
import org.niis.xroad.common.test.glue.BaseStepDefs;
import org.niis.xroad.signer.proto.CertificateRequestFormat;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.CryptoUtils.SHA256WITHRSA_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA256_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA512WITHRSA_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class SignerStepDefs extends BaseStepDefs {
    private String keyId;
    private String csrId;
    private String certHash;
    private CertificateInfo certInfo;
    private byte[] cert;

    private final Map<String, String> tokenLabelToIdMapping = new HashMap<>();
    private final Map<String, String> tokenFriendlyNameToIdMapping = new HashMap<>();

    @Step("tokens are listed")
    public void listTokens() throws Exception {
        var tokens = SignerProxy.getTokens();
        testReportService.attachJson("Tokens", tokens.toArray());

        tokenLabelToIdMapping.clear();
        tokenFriendlyNameToIdMapping.clear();

        tokens.forEach(token -> {
            if (StringUtils.isNotBlank(token.getLabel())) {
                tokenLabelToIdMapping.put(token.getLabel(), token.getId());
            }
            if (StringUtils.isNotBlank(token.getFriendlyName())) {
                tokenFriendlyNameToIdMapping.put(token.getFriendlyName(), token.getId());
            }
        });
    }

    @Step("signer is initialized with pin {string}")
    public void signerIsInitializedWithPin(String pin) throws Exception {
        SignerProxy.initSoftwareToken(pin.toCharArray());
    }

    @Step("token {string} is not active")
    public void tokenIsNotActive(String friendlyName) throws Exception {
        final TokenInfo tokenInfo = getTokenInfoByFriendlyName(friendlyName);

        Assertions.assertFalse(tokenInfo.isActive());
    }

    @Step("token {string} status is {string}")
    public void assertTokenStatus(String friendlyName, String status) throws Exception {
        final TokenInfo token = getTokenInfoByFriendlyName(friendlyName);
        assertThat(token.getStatus()).isEqualTo(TokenStatusInfo.valueOf(status));
    }

    @Step("tokens list contains token {string}")
    public void tokensListContainsToken(String friendlyName) throws Exception {
        var tokens = SignerProxy.getTokens();

        final TokenInfo tokenInfo = tokens.stream()
                .filter(token -> token.getFriendlyName().equals(friendlyName))
                .findFirst()
                .orElseThrow();
        assertThat(tokenInfo).isNotNull();
    }

    @Step("tokens list contains token with label {string}")
    public void tokensListContainsTokenLabel(String label) throws Exception {
        var tokens = SignerProxy.getTokens();
        testReportService.attachJson("Tokens", tokens);
        final TokenInfo tokenInfo = tokens.stream()
                .filter(token -> token.getLabel().equals(label))
                .findFirst()
                .orElseThrow();
        assertThat(tokenInfo).isNotNull();
    }


    @Step("token {string} is logged in with pin {string}")
    public void tokenIsActivatedWithPin(String friendlyName, String pin) throws Exception {
        var tokenId = tokenFriendlyNameToIdMapping.get(friendlyName);
        SignerProxy.activateToken(tokenId, pin.toCharArray());
    }

    @Step("token {string} is logged out")
    public void tokenIsLoggedOut(String friendlyName) throws Exception {
        var tokenId = tokenFriendlyNameToIdMapping.get(friendlyName);
        SignerProxy.deactivateToken(tokenId);
    }

    @Step("token {string} is active")
    public void tokenIsActive(String friendlyName) throws Exception {
        var tokenInfo = getTokenInfoByFriendlyName(friendlyName);
        assertThat(tokenInfo.isActive()).isTrue();
    }

    @Step("token {string} pin is updated from {string} to {string}")
    public void tokenPinIsUpdatedFromTo(String friendlyName, String oldPin, String newPin) throws Exception {
        var tokenId = tokenFriendlyNameToIdMapping.get(friendlyName);
        SignerProxy.updateTokenPin(tokenId, oldPin.toCharArray(), newPin.toCharArray());
    }

    @Step("token {string} pin is update from {string} to {string} fails with an error")
    public void tokenPinIsUpdatedFromToError(String friendlyName, String oldPin, String newPin) throws Exception {
        var tokenId = tokenFriendlyNameToIdMapping.get(friendlyName);
        try {
            SignerProxy.updateTokenPin(tokenId, oldPin.toCharArray(), newPin.toCharArray());
        } catch (CodedException codedException) {
            assertException("Signer.InternalError", "",
                    "Signer.InternalError: Software token not found", codedException);
        }
    }

    @Step("name {string} is set for token with id {string}")
    public void nameIsSetForToken(String name, String tokenId) throws Exception {
        SignerProxy.setTokenFriendlyName(tokenId, name);
    }

    @Step("friendly name {string} is set for token with label {string}")
    public void nameIsSetForTokenLabel(String name, String label) throws Exception {
        var tokenId = tokenLabelToIdMapping.get(label);
        SignerProxy.setTokenFriendlyName(tokenId, name);
    }

    @Step("token with id {string} name is {string}")
    public void tokenNameIs(String tokenId, String name) throws Exception {
        assertThat(SignerProxy.getToken(tokenId).getFriendlyName()).isEqualTo(name);
    }

    @Step("token with label {string} name is {string}")
    public void tokenNameByLabelIs(String label, String name) throws Exception {
        var tokenId = tokenLabelToIdMapping.get(label);
        assertThat(SignerProxy.getToken(tokenId).getFriendlyName()).isEqualTo(name);
    }

    @Step("new key {string} generated for token {string}")
    public void newKeyGeneratedForToken(String keyLabel, String friendlyName) throws Exception {
        var tokenId = tokenFriendlyNameToIdMapping.get(friendlyName);
        final KeyInfo keyInfo = SignerProxy.generateKey(tokenId, keyLabel);
        testReportService.attachJson("keyInfo", keyInfo);
        this.keyId = keyInfo.getId();
    }

    @Step("name {string} is set for generated key")
    public void nameIsSetForGeneratedKey(String keyFriendlyName) throws Exception {
        SignerProxy.setKeyFriendlyName(this.keyId, keyFriendlyName);
    }

    @Step("token {string} has exact keys {string}")
    public void tokenHasKeys(String friendlyName, String keyNames) throws Exception {
        final List<String> keys = Arrays.asList(keyNames.split(","));
        final TokenInfo token = getTokenInfoByFriendlyName(friendlyName);

        assertThat(token.getKeyInfo().size()).isEqualTo(keys.size());

        final List<String> tokenKeyNames = token.getKeyInfo().stream()
                .map(KeyInfo::getFriendlyName)
                .collect(Collectors.toList());

        assertThat(tokenKeyNames).containsExactlyInAnyOrderElementsOf(keys);
    }

    @Step("key {string} is deleted from token {string}")
    public void keyIsDeletedFromToken(String keyName, String friendlyName) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);
        SignerProxy.deleteKey(key.getId(), true);
    }

    private KeyInfo findKeyInToken(String friendlyName, String keyName) throws Exception {
        var foundKeyInfo = getTokenInfoByFriendlyName(friendlyName).getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getFriendlyName().equals(keyName))
                .findFirst()
                .orElseThrow();
        testReportService.attachJson("Key [" + keyName + "]", foundKeyInfo);
        return foundKeyInfo;
    }

    @Step("Certificate is imported for client {string}")
    public void certificateIsImported(String client) throws Exception {
        keyId = SignerProxy.importCert(cert, CertificateInfo.STATUS_REGISTERED, getClientId(client));
    }

    @Step("Wrong Certificate is not imported for client {string}")
    public void certImportFails(String client) throws Exception {
        byte[] certBytes = fileToBytes("src/intTest/resources/cert-01.pem");
        try {
            SignerProxy.importCert(certBytes, CertificateInfo.STATUS_REGISTERED, getClientId(client));
        } catch (CodedException codedException) {
            assertException("Signer.KeyNotFound", "key_not_found_for_certificate",
                    "Signer.KeyNotFound: Could not find key that has public key that matches the public key of certificate", codedException);
        }
    }

    private byte[] fileToBytes(String fileName) throws Exception {
        try (FileInputStream in = new FileInputStream(fileName)) {
            return IOUtils.toByteArray(in);
        }
    }

    @Step("self signed cert generated for token {string} key {string}, client {string}")
    public void selfSignedCertGeneratedForTokenKeyForClient(String friendlyName, String keyName, String client) throws Exception {
        final KeyInfo keyInToken = findKeyInToken(friendlyName, keyName);

        cert = SignerProxy.generateSelfSignedCert(keyInToken.getId(), getClientId(client), KeyUsageInfo.SIGNING,
                "CN=" + client, Date.from(now().minus(5, DAYS)), Date.from(now().plus(5, DAYS)));
        this.certHash = CryptoUtils.calculateCertHexHash(cert);
    }

    private ClientId.Conf getClientId(String client) {
        final String[] parts = client.split(":");
        return ClientId.Conf.create(parts[0], parts[1], parts[2]);
    }

    @Step("the {} cert request is generated for token {string} key {string} for client {string} throws exception")
    public void certRequestIsGeneratedForTokenKeyException(String keyUsage, String friendlyName, String keyName, String client) throws Exception {
        try {
            certRequestIsGeneratedForTokenKey(keyUsage, friendlyName, keyName, client);
        } catch (CodedException codedException) {
            assertException("Signer.WrongCertUsage", "auth_cert_under_softtoken",
                    "Signer.WrongCertUsage: Authentication certificate requests can only be created under software tokens", codedException);
        }
    }

    @Step("the {} cert request is generated for token {string} key {string} for client {string}")
    public void certRequestIsGeneratedForTokenKey(String keyUsage, String friendlyName, String keyName, String client) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);
        final ClientId.Conf clientId = getClientId(client);
        SignerProxy.GeneratedCertRequestInfo csrInfo = SignerProxy.generateCertRequest(key.getId(), clientId, KeyUsageInfo.valueOf(keyUsage),
                "CN=key-" + keyName, CertificateRequestFormat.DER);

        this.csrId = csrInfo.getCertReqId();

        File csrFile = File.createTempFile("tmp", keyUsage.toLowerCase() + "_csr" + System.currentTimeMillis());
        FileUtils.writeByteArrayToFile(csrFile, csrInfo.getCertRequest());
        putStepData(StepDataKey.DOWNLOADED_FILE, csrFile);
    }

    @Step("Generated certificate with initial status {string} is imported for client {string}")
    public void importCertFromFile(String initialStatus, String client) throws Exception {
        final Optional<File> cert = getStepData(StepDataKey.CERT_FILE);
        final ClientId.Conf clientId = getClientId(client);
        final byte[] certBytes = FileUtils.readFileToByteArray(cert.orElseThrow());

        keyId = SignerProxy.importCert(certBytes, initialStatus, clientId);
    }

    @Step("cert request is regenerated")
    public void certRequestIsRegenerated() throws Exception {
        SignerProxy.regenerateCertRequest(this.csrId, CertificateRequestFormat.DER);
    }

    @Step("token {string} key {string} has {int} certificates")
    public void tokenKeyHasCertificates(String friendlyName, String keyName, int certCount) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);

        assertThat(key.getCerts()).hasSize(certCount);
    }

    @Step("sign mechanism for token {string} key {string} is not null")
    public void signMechanismForTokenKeyIsNotNull(String friendlyName, String keyName) throws Exception {
        final KeyInfo keyInToken = findKeyInToken(friendlyName, keyName);
        final String signMechanism = SignerProxy.getSignMechanism(keyInToken.getId());

        assertThat(signMechanism).isNotBlank();
    }

    @Step("member {string} has {int} certificate")
    public void memberHasCertificate(String memberId, int certCount) throws Exception {
        final List<CertificateInfo> memberCerts = SignerProxy.getMemberCerts(getClientId(memberId));
        assertThat(memberCerts).hasSize(certCount);
    }

    @Step("check token {string} key {string} batch signing enabled")
    public void checkTokenBatchSigningEnabled(String friendlyName, String keyname) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyname);

        assertThat(SignerProxy.isTokenBatchSigningEnabled(key.getId())).isNotNull();
    }

    @Step("cert request can be deleted")
    public void certRequestCanBeDeleted() throws Exception {
        SignerProxy.deleteCertRequest(this.csrId);
    }

    @Step("certificate info can be retrieved by cert hash")
    public void certificateInfoCanBeRetrievedByHash() throws Exception {
        final CertificateInfo certInfoResponse = SignerProxy.getCertForHash(this.certHash);
        assertThat(certInfoResponse).isNotNull();
        this.certInfo = certInfoResponse;
    }

    @Step("keyId can be retrieved by cert hash")
    public void keyidCanBeRetrievedByCertHash() throws Exception {
        final SignerProxy.KeyIdInfo keyIdForCertHash = SignerProxy.getKeyIdForCertHash(this.certHash);
        assertThat(keyIdForCertHash).isNotNull();
    }

    @Step("token and keyId can be retrieved by cert hash")
    public void tokenAndKeyIdCanBeRetrievedByCertHash() {
        final TokenInfoAndKeyId tokenAndKeyIdForCertHash = SignerProxy.getTokenAndKeyIdForCertHash(this.certHash);
        assertThat(tokenAndKeyIdForCertHash).isNotNull();
    }

    @Step("token and key can be retrieved by cert request")
    public void tokenAndKeyCanBeRetrievedByCertRequest() throws Exception {
        final TokenInfoAndKeyId tokenAndKeyIdForCertRequestId = SignerProxy.getTokenAndKeyIdForCertRequestId(this.csrId);
        assertThat(tokenAndKeyIdForCertRequestId).isNotNull();
    }

    @Step("token info can be retrieved by key id")
    public void tokenInfoCanBeRetrievedByKeyId() throws Exception {
        final TokenInfo tokenForKeyId = SignerProxy.getTokenForKeyId(this.keyId);
        testReportService.attachJson("tokenInfo", tokenForKeyId);
        assertThat(tokenForKeyId).isNotNull();
    }

    @Step("digest can be signed using key {string} from token {string}")
    public void digestCanBeSignedUsingKeyFromToken(String keyName, String friendlyName) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);

        SignerProxy.sign(key.getId(), SHA256WITHRSA_ID, calculateDigest(SHA256_ID, "digest".getBytes(UTF_8)));
    }

    @Step("certificate can be deactivated")
    public void certificateCanBeDeactivated() throws Exception {
        SignerProxy.deactivateCert(this.certInfo.getId());
    }

    @Step("certificate can be activated")
    public void certificateCanBeActivated() throws Exception {
        SignerProxy.activateCert(this.certInfo.getId());
    }

    @Step("certificate can be deleted")
    public void certificateCanBeDeleted() throws Exception {
        SignerProxy.deleteCert(this.certInfo.getId());
    }

    @Step("certificate status can be changed to {string}")
    public void certificateStatusCanBeChangedTo(String status) throws Exception {
        SignerProxy.setCertStatus(this.certInfo.getId(), status);
    }

    @Step("certificate can be signed using key {string} from token {string}")
    public void certificateCanBeSignedUsingKeyFromToken(String keyName, String friendlyName) throws Exception {
        final KeyInfo key = findKeyInToken(friendlyName, keyName);
        byte[] keyBytes = Base64.decode(key.getPublicKey().getBytes());
        X509EncodedKeySpec x509publicKey = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey publicKey = kf.generatePublic(x509publicKey);

        final byte[] bytes = SignerProxy.signCertificate(key.getId(), SHA256WITHRSA_ID, "CN=cs", publicKey);
        assertThat(bytes).isNotEmpty();
    }


    @Step("Digest is signed using key {string} from token {string}")
    public void sign(String keyName, String friendlyName) throws Exception {

        final KeyInfo key = findKeyInToken(friendlyName, keyName);
        byte[] bytes = SignerProxy.sign(key.getId(), SHA512WITHRSA_ID, calculateDigest(SHA256_ID, "digest".getBytes(UTF_8)));
        assertThat(bytes).isNotEmpty();
    }

    @Step("Set token name fails with TokenNotFound exception when token does not exist")
    public void setTokenNameFail() throws Exception {
        String tokenId = randomUUID().toString();
        try {
            SignerProxy.setTokenFriendlyName(tokenId, randomUUID().toString());
            fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.TokenNotFound", "token_not_found",
                    "Signer.TokenNotFound: Token '" + tokenId + "' not found", codedException);
        }
    }

    @Step("Deleting not existing certificate from token fails")
    public void failOnDeleteCert() throws Exception {
        String cerId = randomUUID().toString();
        try {
            SignerProxy.deleteCert(cerId);
            fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CertNotFound", "cert_with_id_not_found",
                    "Signer.CertNotFound: Certificate with id '" + cerId + "' not found", codedException);
        }
    }

    @Step("Retrieving token info by not existing key fails")
    public void retrievingTokenInfoCanByNotExistingKeyFails() throws Exception {
        String keyId = randomUUID().toString();
        try {
            SignerProxy.getTokenForKeyId(keyId);
            fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.KeyNotFound", "key_not_found",
                    "Signer.KeyNotFound: Key '" + keyId + "' not found", codedException);
        }
    }

    @Step("Deleting not existing certRequest fails")
    public void deletingCertRequestFails() throws Exception {
        String csrId = randomUUID().toString();
        try {
            SignerProxy.deleteCertRequest(csrId);
            fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CsrNotFound", "csr_not_found",
                    "Signer.CsrNotFound: Certificate request '" + csrId + "' not found", codedException);
        }
    }

    @Step("Signing with unknown key fails")
    public void signKeyFail() throws Exception {
        String keyId = randomUUID().toString();
        try {
            SignerProxy.sign(keyId, randomUUID().toString(), new byte[0]);
            fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.KeyNotFound", "key_not_found",
                    "Signer.KeyNotFound: Key '" + keyId + "' not found", codedException);
        }
    }

    @Step("Signing with unknown algorithm fails using key {string} from token {string}")
    public void signAlgorithmFail(String keyName, String friendlyName) throws Exception {
        try {
            final KeyInfo key = findKeyInToken(friendlyName, keyName);
            SignerProxy.sign(key.getId(), "NOT-ALGORITHM-ID", calculateDigest(SHA256_ID, "digest".getBytes(UTF_8)));

            fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CannotSign.InternalError", "",
                    "Signer.CannotSign.InternalError: Unknown sign algorithm id: NOT-ALGORITHM-ID", codedException);
        }
    }

    @Step("Getting key by not existing cert hash fails")
    public void getKeyIdByHashFail() throws Exception {
        String hash = randomUUID().toString();
        try {
            SignerProxy.getKeyIdForCertHash(hash);
            fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CertNotFound", "certificate_with_hash_not_found",
                    "Signer.CertNotFound: Certificate with hash '" + hash + "' not found", codedException);
        }
    }

    @Step("Not existing certificate can not be activated")
    public void notExistingCertActivateFail() throws Exception {
        String certId = randomUUID().toString();
        try {
            SignerProxy.activateCert(certId);
            fail("Exception expected");
        } catch (CodedException codedException) {
            assertException("Signer.CertNotFound", "cert_with_id_not_found",
                    "Signer.CertNotFound: Certificate with id '" + certId + "' not found", codedException);
        }
    }

    @Step("Member signing info for client {string} is retrieved")
    public void getMemberSigningInfo(String client) throws Exception {
        var memberInfo = SignerProxy.getMemberSigningInfo(getClientId(client));
        testReportService.attachJson("MemberSigningInfo", memberInfo);
    }

    @Step("HSM is operational")
    public void hsmIsNotOperational() throws Exception {
        assertTrue(SignerProxy.isHSMOperational());
    }

    private void assertException(String faultCode, String translationCode, String message, CodedException codedException) {
        assertEquals(faultCode, codedException.getFaultCode());
        assertEquals(translationCode, codedException.getTranslationCode());
        assertEquals(message, codedException.getMessage());
    }


    @Step("ocsp responses are set")
    public void ocspResponsesAreSet() throws Exception {
        X509Certificate subject = TestCertUtil.getConsumer().certChain[0];
        final OCSPResp ocspResponse = OcspTestUtils.createOCSPResponse(subject, TestCertUtil.getCaCert(), TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key, CertificateStatus.GOOD);

        SignerProxy.setOcspResponses(new String[]{calculateCertHexHash(subject)},
                new String[]{Base64.toBase64String(ocspResponse.getEncoded())});
    }

    @Step("ocsp responses can be retrieved")
    public void ocspResponsesCanBeRetrieved() throws Exception {
        X509Certificate subject = TestCertUtil.getConsumer().certChain[0];
        final String hash = calculateCertHexHash(subject);

        final String[] ocspResponses = SignerProxy.getOcspResponses(new String[]{hash});
        assertThat(ocspResponses).isNotEmpty();
    }

    @Step("null ocsp response is returned for unknown certificate")
    public void emptyOcspResponseIsReturnedForUnknownCertificate() throws Exception {
        final String[] ocspResponses = SignerProxy
                .getOcspResponses(new String[]{calculateCertHexHash("not a cert".getBytes())});
        assertThat(ocspResponses).hasSize(1);
        assertThat(ocspResponses[0]).isNull();
    }

    private TokenInfo getTokenInfoByFriendlyName(String friendlyName) throws Exception {
        var tokenInfo = SignerProxy.getToken(tokenFriendlyNameToIdMapping.get(friendlyName));
        testReportService.attachJson("TokenInfo", tokenInfo);
        return tokenInfo;
    }
}
