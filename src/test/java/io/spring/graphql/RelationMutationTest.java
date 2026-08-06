package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import graphql.ExecutionResult;
import io.spring.application.ProfileQueryService;
import io.spring.application.data.ProfileData;
import io.spring.core.user.FollowRelation;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
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

@SpringBootTest(classes = {DgsAutoConfiguration.class, RelationMutation.class})
public class RelationMutationTest {

  @Autowired private DgsQueryExecutor dgsQueryExecutor;

  @MockBean private UserRepository userRepository;
  @MockBean private ProfileQueryService profileQueryService;

  private User user;
  private User target;

  @BeforeEach
  public void setUp() {
    user = new User("john@jacob.com", "johnjacob", "123", "", "");
    target = new User("target@test.com", "target", "123", "target bio", "target image");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    when(userRepository.findByUsername(eq(target.getUsername()))).thenReturn(Optional.of(target));
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void should_follow_user_success() {
    when(profileQueryService.findByUsername(eq(target.getUsername()), eq(user)))
        .thenReturn(Optional.of(profileData(true)));

    Boolean following =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "mutation { followUser(username: \"target\") { profile { username bio image following } } }",
            "data.followUser.profile.following");

    assertThat(following).isTrue();
    verify(userRepository).saveRelation(any(FollowRelation.class));
  }

  @Test
  public void should_unfollow_user_success() {
    FollowRelation followRelation = new FollowRelation(user.getId(), target.getId());
    when(userRepository.findRelation(eq(user.getId()), eq(target.getId())))
        .thenReturn(Optional.of(followRelation));
    when(profileQueryService.findByUsername(eq(target.getUsername()), eq(user)))
        .thenReturn(Optional.of(profileData(false)));

    Boolean following =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "mutation { unfollowUser(username: \"target\") { profile { username following } } }",
            "data.unfollowUser.profile.following");

    assertThat(following).isFalse();
    verify(userRepository).removeRelation(eq(followRelation));
  }

  @Test
  public void should_get_error_if_follow_target_not_found() {
    when(userRepository.findByUsername(eq("not-exist"))).thenReturn(Optional.empty());

    ExecutionResult result =
        dgsQueryExecutor.execute(
            "mutation { followUser(username: \"not-exist\") { profile { username } } }");

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).contains("ResourceNotFoundException");
  }

  @Test
  public void should_get_error_if_unfollow_relation_not_found() {
    when(userRepository.findRelation(eq(user.getId()), eq(target.getId())))
        .thenReturn(Optional.empty());

    ExecutionResult result =
        dgsQueryExecutor.execute(
            "mutation { unfollowUser(username: \"target\") { profile { username } } }");

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).contains("ResourceNotFoundException");
  }

  @Test
  public void should_get_authentication_error_if_no_current_user() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(null, null, Collections.emptyList()));

    ExecutionResult followResult =
        dgsQueryExecutor.execute(
            "mutation { followUser(username: \"target\") { profile { username } } }");
    assertThat(followResult.getErrors()).hasSize(1);
    assertThat(followResult.getErrors().get(0).getMessage()).contains("AuthenticationException");

    ExecutionResult unfollowResult =
        dgsQueryExecutor.execute(
            "mutation { unfollowUser(username: \"target\") { profile { username } } }");
    assertThat(unfollowResult.getErrors()).hasSize(1);
    assertThat(unfollowResult.getErrors().get(0).getMessage()).contains("AuthenticationException");
  }

  private ProfileData profileData(boolean following) {
    return new ProfileData(
        target.getId(), target.getUsername(), target.getBio(), target.getImage(), following);
  }
}
