package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import graphql.ExecutionResult;
import io.spring.application.ProfileQueryService;
import io.spring.application.data.ProfileData;
import io.spring.core.user.User;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(classes = {DgsAutoConfiguration.class, ProfileDatafetcher.class})
public class ProfileDatafetcherTest {

  @Autowired private DgsQueryExecutor dgsQueryExecutor;

  @MockBean private ProfileQueryService profileQueryService;

  private User user;

  @BeforeEach
  public void setUp() {
    user = new User("john@jacob.com", "johnjacob", "123", "", "");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void should_query_profile_success() {
    ProfileData profileData = new ProfileData("id", "target", "bio", "image", true);
    when(profileQueryService.findByUsername(eq("target"), eq(user)))
        .thenReturn(Optional.of(profileData));

    String username =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ profile(username: \"target\") { profile { username bio image following } } }",
            "data.profile.profile.username");
    Boolean following =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ profile(username: \"target\") { profile { username following } } }",
            "data.profile.profile.following");

    assertThat(username).isEqualTo("target");
    assertThat(following).isTrue();
  }

  @Test
  public void should_return_error_if_profile_not_found() {
    when(profileQueryService.findByUsername(eq("not-exist"), eq(user)))
        .thenReturn(Optional.empty());

    ExecutionResult result =
        dgsQueryExecutor.execute("{ profile(username: \"not-exist\") { profile { username } } }");

    assertThat(result.getErrors()).isNotEmpty();
  }
}
