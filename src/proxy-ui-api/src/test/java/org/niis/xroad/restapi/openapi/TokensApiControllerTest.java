/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.Key;
import org.niis.xroad.restapi.openapi.model.Token;
import org.niis.xroad.restapi.openapi.model.TokenStatus;
import org.niis.xroad.restapi.service.TokenService;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * test system api
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class TokensApiControllerTest {

    private static final String TOKEN_NOT_FOUND_TOKEN_ID = "token-404";
    private static final String KEY_NOT_FOUND_KEY_ID = "key-404";
    private static final String GOOD_TOKEN_ID = "token-which-exists";
    private static final String GOOD_KEY_ID = "key-which-exists";

    @MockBean
    private TokenService tokenService;

    @Autowired
    private TokensApiController tokensApiController;

    @Before
    public void setUp() throws Exception {
        TokenInfo tokenInfo = TokenTestUtils.createTestTokenInfo("friendly-name", GOOD_TOKEN_ID);
        KeyInfo keyInfo = TokenTestUtils.createTestKeyInfo(GOOD_KEY_ID);
        tokenInfo.getKeyInfo().add(keyInfo);
        when(tokenService.getAllTokens()).thenReturn(Collections.singletonList(tokenInfo));

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            if (GOOD_TOKEN_ID.equals(tokenId)) {
                return tokenInfo;
            } else {
                throw new TokenService.TokenNotFoundException(new RuntimeException());
            }
        }).when(tokenService).getToken(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            String keyId = (String) args[1];
            if (!GOOD_TOKEN_ID.equals(tokenId)) {
                throw new TokenService.TokenNotFoundException(new RuntimeException());
            } else if (!GOOD_KEY_ID.equals(keyId)) {
                throw new TokenService.KeyNotFoundException("foo");
            } else {
                return keyInfo;
            }
        }).when(tokenService).getKey(any(), any());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_KEYS" })
    public void getTokens() {
        ResponseEntity<List<Token>> response = tokensApiController.getTokens();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Token> tokens = response.getBody();
        assertEquals(1, tokens.size());
        Token token = tokens.iterator().next();
        assertEquals(TokenStatus.OK, token.getStatus());
        assertEquals("friendly-name", token.getName());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_KEYS" })
    public void getToken() {
        try {
            tokensApiController.getToken(TOKEN_NOT_FOUND_TOKEN_ID);
            fail("should have thrown exception");
        } catch (TokenService.TokenNotFoundException expected) {
        }

        ResponseEntity<Token> response = tokensApiController.getToken(GOOD_TOKEN_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GOOD_TOKEN_ID, response.getBody().getId());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_KEYS" })
    public void getKey() {
        try {
            tokensApiController.getKey(TOKEN_NOT_FOUND_TOKEN_ID, KEY_NOT_FOUND_KEY_ID);
            fail("should have thrown exception");
        } catch (TokenService.TokenNotFoundException expected) {
        }

        try {
            tokensApiController.getKey(TOKEN_NOT_FOUND_TOKEN_ID, GOOD_KEY_ID);
            fail("should have thrown exception");
        } catch (TokenService.TokenNotFoundException expected) {
        }

        try {
            tokensApiController.getKey(GOOD_TOKEN_ID, KEY_NOT_FOUND_KEY_ID);
            fail("should have thrown exception");
        } catch (TokenService.KeyNotFoundException expected) {
        }

        ResponseEntity<Key> response = tokensApiController.getKey(GOOD_TOKEN_ID, GOOD_KEY_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GOOD_KEY_ID, response.getBody().getId());
    }
}
