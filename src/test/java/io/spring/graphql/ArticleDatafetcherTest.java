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
import io.spring.application.CursorPager;
import io.spring.application.CursorPager.Direction;
import io.spring.application.data.ArticleData;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(
    classes = {DgsAutoConfiguration.class, ArticleDatafetcher.class, ProfileDatafetcher.class})
public class ArticleDatafetcherTest {

  @Autowired private DgsQueryExecutor dgsQueryExecutor;

  @MockBean private ArticleQueryService articleQueryService;
  @MockBean private UserRepository userRepository;
  @MockBean private io.spring.application.ProfileQueryService profileQueryService;

  private User user;

  @BeforeEach
  public void setUp() {
    user = new User("john@jacob.com", "johnjacob", "123", "", "");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, java.util.Collections.emptyList()));
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void should_find_article_by_slug() {
    ArticleData articleData = TestHelper.articleDataFixture("1", user);
    when(articleQueryService.findBySlug(eq(articleData.getSlug()), eq(user)))
        .thenReturn(Optional.of(articleData));

    String slug =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "{ article(slug: \"%s\") { slug title body description favorited favoritesCount } }",
                articleData.getSlug()),
            "data.article.slug");

    assertThat(slug).isEqualTo(articleData.getSlug());
  }

  @Test
  public void should_query_article_author_profile() {
    ArticleData articleData = TestHelper.articleDataFixture("2", user);
    when(articleQueryService.findBySlug(eq(articleData.getSlug()), eq(user)))
        .thenReturn(Optional.of(articleData));
    when(profileQueryService.findByUsername(eq(user.getUsername()), eq(user)))
        .thenReturn(Optional.of(articleData.getProfileData()));

    String username =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "{ article(slug: \"%s\") { slug author { username following } } }",
                articleData.getSlug()),
            "data.article.author.username");

    assertThat(username).isEqualTo(user.getUsername());
  }

  @Test
  public void should_return_error_if_article_not_found() {
    when(articleQueryService.findBySlug(eq("not-exist"), any())).thenReturn(Optional.empty());

    ExecutionResult result = dgsQueryExecutor.execute("{ article(slug: \"not-exist\") { slug } }");

    assertThat(result.getErrors()).isNotEmpty();
  }

  @Test
  public void should_get_articles_with_cursor() {
    ArticleData first = TestHelper.articleDataFixture("1", user);
    ArticleData second = TestHelper.articleDataFixture("2", user);
    when(articleQueryService.findRecentArticlesWithCursor(
            eq("java"), eq(null), eq(null), any(), eq(user)))
        .thenReturn(new CursorPager<>(Arrays.asList(first, second), Direction.NEXT, false));

    List<String> slugs =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ articles(first: 10, withTag: \"java\") { edges { cursor node { slug } } pageInfo { hasNextPage hasPreviousPage } } }",
            "data.articles.edges[*].node.slug");

    assertThat(slugs).containsExactly(first.getSlug(), second.getSlug());
    Boolean hasNextPage =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ articles(first: 10, withTag: \"java\") { pageInfo { hasNextPage } } }",
            "data.articles.pageInfo.hasNextPage");
    assertThat(hasNextPage).isFalse();
  }

  @Test
  public void should_get_articles_backward_with_cursor() {
    ArticleData articleData = TestHelper.articleDataFixture("3", user);
    when(articleQueryService.findRecentArticlesWithCursor(
            eq(null), eq(null), eq(null), any(), eq(user)))
        .thenReturn(new CursorPager<>(Arrays.asList(articleData), Direction.PREV, true));

    Boolean hasPreviousPage =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ articles(last: 10) { edges { node { slug } } pageInfo { hasPreviousPage } } }",
            "data.articles.pageInfo.hasPreviousPage");

    assertThat(hasPreviousPage).isTrue();
  }

  @Test
  public void should_get_feed_with_cursor() {
    ArticleData articleData = TestHelper.articleDataFixture("4", user);
    when(articleQueryService.findUserFeedWithCursor(eq(user), any()))
        .thenReturn(new CursorPager<>(Arrays.asList(articleData), Direction.NEXT, true));

    List<String> slugs =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "{ feed(first: 10) { edges { cursor node { slug title } } pageInfo { hasNextPage } } }",
            "data.feed.edges[*].node.slug");

    assertThat(slugs).containsExactly(articleData.getSlug());
  }

  @Test
  public void should_return_error_if_both_first_and_last_are_null() {
    ExecutionResult articlesResult = dgsQueryExecutor.execute("{ articles { edges { cursor } } }");
    assertThat(articlesResult.getErrors()).hasSize(1);
    assertThat(articlesResult.getErrors().get(0).getMessage()).contains("IllegalArgumentException");

    ExecutionResult feedResult = dgsQueryExecutor.execute("{ feed { edges { cursor } } }");
    assertThat(feedResult.getErrors()).hasSize(1);
    assertThat(feedResult.getErrors().get(0).getMessage()).contains("IllegalArgumentException");
  }
}
