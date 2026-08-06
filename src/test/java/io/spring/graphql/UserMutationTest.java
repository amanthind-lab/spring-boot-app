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
import io.spring.application.UserQueryService;
import io.spring.application.user.UpdateUserCommand;
import io.spring.application.user.UserService;
import io.spring.core.service.JwtService;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import io.spring.graphql.exception.GraphQLCustomizeExceptionHandler;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(
    classes = {
      DgsAutoConfiguration.class,
      UserMutation.class,
      MeDatafetcher.class,
      ProfileDatafetcher.class,
      GraphQLCustomizeExceptionHandler.class
    })
public class UserMutationTest {

  @Autowired private DgsQueryExecutor dgsQueryExecutor;

  @MockBean private UserRepository userRepository;
  @MockBean private PasswordEncoder encryptService;
  @MockBean private UserService userService;
  @MockBean private UserQueryService userQueryService;
  @MockBean private ProfileQueryService profileQueryService;
  @MockBean private JwtService jwtService;

  private User user;
  private final String token = "jwt-token";

  @BeforeEach
  public void setUp() {
    user = new User("john@jacob.com", "johnjacob", "encoded-123", "", "");
    when(jwtService.toToken(eq(user))).thenReturn(token);
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void should_create_user_success() {
    when(userService.createUser(any())).thenReturn(user);

    String email =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "mutation { createUser(input: {email: \"john@jacob.com\", username: \"johnjacob\", password: \"123\"}) { ... on UserPayload { user { email username token } } } }",
            "data.createUser.user.email");

    assertThat(email).isEqualTo(user.getEmail());
  }

  @Test
  public void should_login_success() {
    when(userRepository.findByEmail(eq(user.getEmail()))).thenReturn(Optional.of(user));
    when(encryptService.matches(eq("123"), eq(user.getPassword()))).thenReturn(true);

    String userToken =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "mutation { login(email: \"john@jacob.com\", password: \"123\") { user { email username token } } }",
            "data.login.user.token");

    assertThat(userToken).isEqualTo(token);
  }

  @Test
  public void should_get_unauthenticated_error_if_password_not_match() {
    when(userRepository.findByEmail(eq(user.getEmail()))).thenReturn(Optional.of(user));
    when(encryptService.matches(eq("wrong"), eq(user.getPassword()))).thenReturn(false);

    ExecutionResult result =
        dgsQueryExecutor.execute(
            "mutation { login(email: \"john@jacob.com\", password: \"wrong\") { user { email } } }");

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo("invalid email or password");
  }

  @Test
  public void should_get_unauthenticated_error_if_user_not_found() {
    when(userRepository.findByEmail(eq("not-exist@test.com"))).thenReturn(Optional.empty());

    ExecutionResult result =
        dgsQueryExecutor.execute(
            "mutation { login(email: \"not-exist@test.com\", password: \"123\") { user { email } } }");

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo("invalid email or password");
  }

  @Test
  public void should_update_user_success() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));

    String username =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "mutation { updateUser(changes: {email: \"new@email.com\", bio: \"new bio\"}) { user { email username token } } }",
            "data.updateUser.user.username");

    assertThat(username).isEqualTo(user.getUsername());
    verify(userService).updateUser(any(UpdateUserCommand.class));
  }

  @Test
  public void should_return_null_if_update_user_without_current_user() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(null, null, Collections.emptyList()));

    Object result =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "mutation { updateUser(changes: {bio: \"new bio\"}) { user { email } } }",
            "data.updateUser");

    assertThat(result).isNull();
  }
}
