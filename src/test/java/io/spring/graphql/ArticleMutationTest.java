package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import graphql.ExecutionResult;
import io.spring.TestHelper;
import io.spring.application.ArticleQueryService;
import io.spring.application.ProfileQueryService;
import io.spring.application.article.ArticleCommandService;
import io.spring.application.data.ArticleData;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.favorite.ArticleFavorite;
import io.spring.core.favorite.ArticleFavoriteRepository;
import io.spring.core.user.User;
import io.spring.core.user.UserRepository;
import java.util.Arrays;
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

@SpringBootTest(
    classes = {
      DgsAutoConfiguration.class,
      ArticleMutation.class,
      ArticleDatafetcher.class,
      ProfileDatafetcher.class
    })
public class ArticleMutationTest {

  @Autowired private DgsQueryExecutor dgsQueryExecutor;

  @MockBean private ArticleCommandService articleCommandService;
  @MockBean private ArticleRepository articleRepository;
  @MockBean private ArticleFavoriteRepository articleFavoriteRepository;
  @MockBean private ArticleQueryService articleQueryService;
  @MockBean private ProfileQueryService profileQueryService;
  @MockBean private UserRepository userRepository;

  private User user;

  @BeforeEach
  public void setUp() {
    user = new User("john@jacob.com", "johnjacob", "123", "", "");
    setCurrentUser(user);
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void should_create_article_success() {
    Article article = articleFixture("new title", user);
    ArticleData articleData = TestHelper.getArticleDataFromArticleAndUser(article, user);
    when(articleCommandService.createArticle(any(), eq(user))).thenReturn(article);
    when(articleQueryService.findById(eq(article.getId()), eq(user)))
        .thenReturn(Optional.of(articleData));

    String slug =
        dgsQueryExecutor.executeAndExtractJsonPath(
            "mutation { createArticle(input: {title: \"new title\", description: \"desc\", body: \"body\", tagList: [\"java\"]}) { article { slug title body } } }",
            "data.createArticle.article.slug");

    assertThat(slug).isEqualTo(article.getSlug());
  }

  @Test
  public void should_update_article_success() {
    Article article = articleFixture("old title", user);
    ArticleData articleData = TestHelper.getArticleDataFromArticleAndUser(article, user);
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));
    when(articleCommandService.updateArticle(eq(article), any())).thenReturn(article);
    when(articleQueryService.findById(eq(article.getId()), eq(user)))
        .thenReturn(Optional.of(articleData));

    String slug =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "mutation { updateArticle(slug: \"%s\", changes: {title: \"new title\"}) { article { slug } } }",
                article.getSlug()),
            "data.updateArticle.article.slug");

    assertThat(slug).isEqualTo(article.getSlug());
    verify(articleCommandService).updateArticle(eq(article), any());
  }

  @Test
  public void should_get_error_if_not_author_to_update_article() {
    User anotherUser = new User("other@test.com", "other", "123", "", "");
    Article article = articleFixture("other title", anotherUser);
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));

    ExecutionResult result =
        dgsQueryExecutor.execute(
            String.format(
                "mutation { updateArticle(slug: \"%s\", changes: {title: \"new title\"}) { article { slug } } }",
                article.getSlug()));

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).contains("NoAuthorizationException");
  }

  @Test
  public void should_favorite_article_success() {
    Article article = articleFixture("favorite title", user);
    ArticleData articleData = TestHelper.getArticleDataFromArticleAndUser(article, user);
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));
    when(articleQueryService.findById(eq(article.getId()), eq(user)))
        .thenReturn(Optional.of(articleData));

    String slug =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "mutation { favoriteArticle(slug: \"%s\") { article { slug favorited } } }",
                article.getSlug()),
            "data.favoriteArticle.article.slug");

    assertThat(slug).isEqualTo(article.getSlug());
    verify(articleFavoriteRepository).save(any(ArticleFavorite.class));
  }

  @Test
  public void should_unfavorite_article_success() {
    Article article = articleFixture("unfavorite title", user);
    ArticleData articleData = TestHelper.getArticleDataFromArticleAndUser(article, user);
    ArticleFavorite articleFavorite = new ArticleFavorite(article.getId(), user.getId());
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));
    when(articleFavoriteRepository.find(eq(article.getId()), eq(user.getId())))
        .thenReturn(Optional.of(articleFavorite));
    when(articleQueryService.findById(eq(article.getId()), eq(user)))
        .thenReturn(Optional.of(articleData));

    String slug =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "mutation { unfavoriteArticle(slug: \"%s\") { article { slug } } }",
                article.getSlug()),
            "data.unfavoriteArticle.article.slug");

    assertThat(slug).isEqualTo(article.getSlug());
    verify(articleFavoriteRepository).remove(eq(articleFavorite));
  }

  @Test
  public void should_delete_article_success() {
    Article article = articleFixture("delete title", user);
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));

    Boolean success =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "mutation { deleteArticle(slug: \"%s\") { success } }", article.getSlug()),
            "data.deleteArticle.success");

    assertThat(success).isTrue();
    verify(articleRepository).remove(eq(article));
  }

  @Test
  public void should_get_error_if_not_author_to_delete_article() {
    User anotherUser = new User("other@test.com", "other", "123", "", "");
    Article article = articleFixture("other title", anotherUser);
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));

    ExecutionResult result =
        dgsQueryExecutor.execute(
            String.format(
                "mutation { deleteArticle(slug: \"%s\") { success } }", article.getSlug()));

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).contains("NoAuthorizationException");
  }

  @Test
  public void should_get_error_if_article_not_found() {
    when(articleRepository.findBySlug(eq("not-exist"))).thenReturn(Optional.empty());

    ExecutionResult result =
        dgsQueryExecutor.execute("mutation { deleteArticle(slug: \"not-exist\") { success } }");

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).contains("ResourceNotFoundException");
  }

  @Test
  public void should_get_authentication_error_if_no_current_user() {
    SecurityContextHolder.clearContext();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(null, null, Collections.emptyList()));

    ExecutionResult createResult =
        dgsQueryExecutor.execute(
            "mutation { createArticle(input: {title: \"t\", description: \"d\", body: \"b\"}) { article { slug } } }");
    assertThat(createResult.getErrors()).hasSize(1);
    assertThat(createResult.getErrors().get(0).getMessage()).contains("AuthenticationException");

    ExecutionResult favoriteResult =
        dgsQueryExecutor.execute(
            "mutation { favoriteArticle(slug: \"any\") { article { slug } } }");
    assertThat(favoriteResult.getErrors()).hasSize(1);
    assertThat(favoriteResult.getErrors().get(0).getMessage()).contains("AuthenticationException");
  }

  private void setCurrentUser(User currentUser) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList()));
  }

  private Article articleFixture(String title, User author) {
    return new Article(title, "desc", "body", Arrays.asList("java", "spring"), author.getId());
  }
}
