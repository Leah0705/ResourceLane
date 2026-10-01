package io.resourcelane.wallet_service.security;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

    private StringWriter responseWriter;

    private static final String JWT_SECRET = "2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c";

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();

        java.lang.reflect.Field field = JwtAuthenticationFilter.class.getDeclaredField("jwtSecret");
        field.setAccessible(true);
        field.set(filter, JWT_SECRET);

        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String createTestJwt(Long userId, String deviceFingerprint) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("deviceFingerprint", deviceFingerprint)
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // shouldNotFilter
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_whenPathStartsWithInternal() {
        when(request.getServletPath()).thenReturn("/api/wallet/internal/updateWallets");

        boolean result = filter.shouldNotFilter(request);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotFilter_whenPathDoesNotStartWithInternal() {
        when(request.getServletPath()).thenReturn("/api/wallet/createWallet");

        boolean result = filter.shouldNotFilter(request);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotFilter_healthPath() {
        when(request.getServletPath()).thenReturn("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // doFilterInternal – valid JWT
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void validJwtWithMatchingFingerprint_setsAuthenticationAndProceeds() throws Exception {
        String jwtToken = createTestJwt(1L, "deviceABC");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("jwt", jwtToken)});
        when(request.getHeader("X-Device-Fingerprint")).thenReturn("deviceABC");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // doFilterInternal – invalid JWT
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void invalidJwt_returnsUnauthorizedAndStops() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("jwt", "invalid.jwt.token")});

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Unauthorized: Invalid or expired token");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // doFilterInternal – no cookie
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void noCookie_returnsUnauthorizedAndStops() throws Exception {
        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Unauthorized: Token not found");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void noJwtCookie_returnsUnauthorizedAndStops() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "value")});

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Unauthorized: Token not found");
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // doFilterInternal – device fingerprint mismatch
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deviceFingerprintMismatch_returnsUnauthorizedAndStops() throws Exception {
        String jwtToken = createTestJwt(1L, "expectedFingerprint");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("jwt", jwtToken)});
        when(request.getHeader("X-Device-Fingerprint")).thenReturn("differentFingerprint");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Unauthorized: Device fingerprint mismatch");
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nullDeviceFingerprintHeader_returnsUnauthorizedAndStops() throws Exception {
        String jwtToken = createTestJwt(1L, "expectedFingerprint");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("jwt", jwtToken)});
        when(request.getHeader("X-Device-Fingerprint")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Unauthorized: Device fingerprint mismatch");
        verify(filterChain, never()).doFilter(any(), any());
    }
}
