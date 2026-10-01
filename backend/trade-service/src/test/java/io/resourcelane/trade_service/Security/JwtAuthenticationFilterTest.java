package io.resourcelane.trade_service.Security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private PrintWriter printWriter;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private static final String JWT_SECRET = "testSecretKeyForJwtTokenSigningThatIsLongEnough";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
    }

    @Test
    void shouldNotFilter_shouldReturnTrueForWsPaths() {
        when(request.getServletPath()).thenReturn("/ws/something");
        boolean result = filter.shouldNotFilter(request);
        assertThat(result).isTrue();
    }

    @Test
    void shouldNotFilter_shouldReturnFalseForApiPaths() {
        when(request.getServletPath()).thenReturn("/api/trade/createOrder");
        boolean result = filter.shouldNotFilter(request);
        assertThat(result).isFalse();
    }

    @Test
    void shouldNotFilter_shouldReturnTrueForHealthPath() {
        when(request.getServletPath()).thenReturn("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void doFilterInternal_withValidJwtAndFingerprint_shouldAuthenticate() throws Exception {
        String jwt = createJwt(1L, "fp123");
        Cookie cookie = new Cookie("jwt", jwt);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.getHeader("X-Device-Fingerprint")).thenReturn("fp123");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_withMismatchedFingerprint_shouldReturnUnauthorized() throws Exception {
        String jwt = createJwt(1L, "fp123");
        Cookie cookie = new Cookie("jwt", jwt);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.getHeader("X-Device-Fingerprint")).thenReturn("wrong-fp");
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(printWriter).write("Unauthorized: Device fingerprint mismatch");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_withMissingFingerprintHeader_shouldReturnUnauthorized() throws Exception {
        String jwt = createJwt(1L, "fp123");
        Cookie cookie = new Cookie("jwt", jwt);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.getHeader("X-Device-Fingerprint")).thenReturn(null);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(printWriter).write("Unauthorized: Device fingerprint mismatch");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_withNoJwtCookie_shouldReturnUnauthorized() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "value")});
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(printWriter).write("Unauthorized: Token not found");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_withNoCookies_shouldReturnUnauthorized() throws Exception {
        when(request.getCookies()).thenReturn(null);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(printWriter).write("Unauthorized: Token not found");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_withInvalidJwt_shouldReturnUnauthorized() throws Exception {
        Cookie cookie = new Cookie("jwt", "invalid-token");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(printWriter).write("Unauthorized: Invalid or expired token");
        verify(filterChain, never()).doFilter(any(), any());
    }

    private String createJwt(Long userId, String fingerprint) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("deviceFingerprint", fingerprint)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();
    }
}
