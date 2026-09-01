package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import io.spring.application.ProfileQueryService;
import io.spring.application.UserQueryService;
import io.spring.application.data.ProfileData;
import io.spring.application.data.UserData;
import io.spring.core.service.JwtService;
import io.spring.core.user.User;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(
    classes = {DgsAutoConfiguration.class, MeDatafetcher.class, ProfileDatafetcher.class})
public class MeDatafetcherTest {

  @Autowired private DgsQueryExecutor dgsQueryExecutor;

  @MockBean private UserQueryService userQueryService;
  @MockBean private JwtService jwtService;
  @MockBean private ProfileQueryService profileQueryService;

  private User user;
  private UserData userData;
  private final String token = "jwt-token";

  @BeforeEach
  public void setUp() {
    user = new User("john@jacob.com", "johnjacob", "123", "bio", "image");
    userData = new UserData(user.getId(), user.getEmail(), user.getUsername(), "bio", "image");
    when(userQueryService.findById(eq(user.getId()))).thenReturn(Optional.of(userData));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void should_get_current_user() {
    String email =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ me { email username token } }", "data.me.email", authorizationHeaders());
    String username =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ me { email username token } }", "data.me.username", authorizationHeaders());
    String userToken =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ me { email username token } }", "data.me.token", authorizationHeaders());

    assertThat(email).isEqualTo(user.getEmail());
    assertThat(username).isEqualTo(user.getUsername());
    assertThat(userToken).isEqualTo(token);
  }

  @Test
  public void should_get_current_user_profile() {
    when(profileQueryService.findByUsername(eq(user.getUsername()), eq(user)))
        .thenReturn(
            Optional.of(new ProfileData(user.getId(), user.getUsername(), "bio", "image", false)));

    String username =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ me { profile { username bio image following } } }",
            "data.me.profile.username",
            authorizationHeaders());

    assertThat(username).isEqualTo(user.getUsername());
  }

  @Test
  public void should_return_null_if_not_authenticated() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key",
                "anonymous",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    Object me =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ me { email } }", "data.me", authorizationHeaders());

    assertThat(me).isNull();
  }

  private HttpHeaders authorizationHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "Token " + token);
    return headers;
  }
}
