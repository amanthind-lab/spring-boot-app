package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import graphql.ExecutionResult;
import io.spring.TestHelper;
import io.spring.application.ArticleQueryService;
import io.spring.application.CommentQueryService;
import io.spring.application.CursorPager;
import io.spring.application.CursorPager.Direction;
import io.spring.application.data.ArticleData;
import io.spring.application.data.CommentData;
import io.spring.application.data.ProfileData;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(
    classes = {
      DgsAutoConfiguration.class,
      CommentDatafetcher.class,
      ArticleDatafetcher.class,
      ProfileDatafetcher.class
    })
public class CommentDatafetcherTest {

  @Autowired private DgsQueryExecutor dgsQueryExecutor;

  @MockBean private CommentQueryService commentQueryService;
  @MockBean private ArticleQueryService articleQueryService;
  @MockBean private io.spring.application.ProfileQueryService profileQueryService;
  @MockBean private UserRepository userRepository;

  private User user;
  private ArticleData articleData;

  @BeforeEach
  public void setUp() {
    user = new User("john@jacob.com", "johnjacob", "123", "", "");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    articleData = TestHelper.articleDataFixture("1", user);
    when(articleQueryService.findBySlug(eq(articleData.getSlug()), eq(user)))
        .thenReturn(Optional.of(articleData));
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void should_query_article_comments() {
    CommentData comment = commentFixture("1");
    when(commentQueryService.findByArticleIdWithCursor(eq(articleData.getId()), eq(user), any()))
        .thenReturn(new CursorPager<>(Arrays.asList(comment), Direction.NEXT, false));

    List<String> bodies =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "{ article(slug: \"%s\") { comments(first: 10) { edges { cursor node { id body createdAt } } pageInfo { hasNextPage } } } }",
                articleData.getSlug()),
            "data.article.comments.edges[*].node.body");

    assertThat(bodies).containsExactly(comment.getBody());
  }

  @Test
  public void should_query_article_comments_backward() {
    CommentData comment = commentFixture("2");
    when(commentQueryService.findByArticleIdWithCursor(eq(articleData.getId()), eq(user), any()))
        .thenReturn(new CursorPager<>(Arrays.asList(comment), Direction.PREV, true));

    Boolean hasPreviousPage =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "{ article(slug: \"%s\") { comments(last: 10) { edges { node { id } } pageInfo { hasPreviousPage } } } }",
                articleData.getSlug()),
            "data.article.comments.pageInfo.hasPreviousPage");

    assertThat(hasPreviousPage).isTrue();
  }

  @Test
  public void should_query_comment_author() {
    CommentData comment = commentFixture("3");
    when(commentQueryService.findByArticleIdWithCursor(eq(articleData.getId()), eq(user), any()))
        .thenReturn(new CursorPager<>(Arrays.asList(comment), Direction.NEXT, false));
    when(profileQueryService.findByUsername(eq(user.getUsername()), eq(user)))
        .thenReturn(Optional.of(comment.getProfileData()));

    List<String> authors =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "{ article(slug: \"%s\") { comments(first: 10) { edges { node { id author { username } } } } } }",
                articleData.getSlug()),
            "data.article.comments.edges[*].node.author.username");

    assertThat(authors).containsExactly(user.getUsername());
  }

  @Test
  public void should_return_error_if_both_first_and_last_are_null() {
    ExecutionResult result =
        dgsQueryExecutor.execute(
            String.format(
                "{ article(slug: \"%s\") { comments { edges { cursor } } } }",
                articleData.getSlug()));

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).contains("IllegalArgumentException");
  }

  private CommentData commentFixture(String seed) {
    DateTime now = new DateTime();
    return new CommentData(
        seed + "id",
        "comment body " + seed,
        articleData.getId(),
        now,
        now,
        new ProfileData(user.getId(), user.getUsername(), user.getBio(), user.getImage(), false));
  }
}
