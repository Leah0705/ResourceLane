package io.resourcelane.auth_service.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private static final String JWT_SECRET = "2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_validJwtAndMatchingFingerprint_setsAuthentication() throws Exception {
        String jwt = Jwts.builder()
                .claim("userId", 1L)
                .claim("deviceFingerprint", "test-fp")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        Cookie[] cookies = {new Cookie("jwt", jwt)};
        when(request.getCookies()).thenReturn(cookies);
        when(request.getHeader("X-Device-Fingerprint")).thenReturn("test-fp");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_invalidJwt_returnsUnauthorized() throws Exception {
        Cookie[] cookies = {new Cookie("jwt", "invalid-token")};
        when(request.getCookies()).thenReturn(cookies);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(stringWriter.toString()).contains("Invalid or expired token");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_noCookie_returnsUnauthorized() throws Exception {
        when(request.getCookies()).thenReturn(null);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(stringWriter.toString()).contains("Token not found");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_expiredJwt_returnsUnauthorized() throws Exception {
        String jwt = Jwts.builder()
                .claim("userId", 1L)
                .claim("deviceFingerprint", "test-fp")
                .setIssuedAt(new Date(System.currentTimeMillis() - 86400000))
                .setExpiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        Cookie[] cookies = {new Cookie("jwt", jwt)};
        when(request.getCookies()).thenReturn(cookies);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(stringWriter.toString()).contains("Invalid or expired token");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_fingerprintMismatch_returnsUnauthorized() throws Exception {
        String jwt = Jwts.builder()
                .claim("userId", 1L)
                .claim("deviceFingerprint", "expected-fp")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        Cookie[] cookies = {new Cookie("jwt", jwt)};
        when(request.getCookies()).thenReturn(cookies);
        when(request.getHeader("X-Device-Fingerprint")).thenReturn("wrong-fp");

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(stringWriter.toString()).contains("Device fingerprint mismatch");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_nullFingerprintHeader_returnsUnauthorized() throws Exception {
        String jwt = Jwts.builder()
                .claim("userId", 1L)
                .claim("deviceFingerprint", "expected-fp")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();

        Cookie[] cookies = {new Cookie("jwt", jwt)};
        when(request.getCookies()).thenReturn(cookies);
        when(request.getHeader("X-Device-Fingerprint")).thenReturn(null);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(stringWriter.toString()).contains("Device fingerprint mismatch");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldNotFilter_protectedPath_returnsFalse() {
        when(request.getServletPath()).thenReturn("/api/auth/getUser");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_unprotectedPath_returnsTrue() {
        when(request.getServletPath()).thenReturn("/api/auth/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_healthPath_returnsTrue() {
        when(request.getServletPath()).thenReturn("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
