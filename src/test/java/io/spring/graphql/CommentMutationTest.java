package io.spring.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import graphql.ExecutionResult;
import io.spring.application.CommentQueryService;
import io.spring.application.ProfileQueryService;
import io.spring.application.data.CommentData;
import io.spring.application.data.ProfileData;
import io.spring.core.article.Article;
import io.spring.core.article.ArticleRepository;
import io.spring.core.comment.Comment;
import io.spring.core.comment.CommentRepository;
import io.spring.core.user.User;
import java.util.Arrays;
import java.util.Collections;
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
      CommentMutation.class,
      CommentDatafetcher.class,
      ProfileDatafetcher.class
    })
public class CommentMutationTest {

  @Autowired private DgsQueryExecutor dgsQueryExecutor;

  @MockBean private ArticleRepository articleRepository;
  @MockBean private CommentRepository commentRepository;
  @MockBean private CommentQueryService commentQueryService;
  @MockBean private ProfileQueryService profileQueryService;

  private User user;
  private Article article;

  @BeforeEach
  public void setUp() {
    user = new User("john@jacob.com", "johnjacob", "123", "", "");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    article = new Article("title", "desc", "body", Arrays.asList("java"), user.getId());
    when(articleRepository.findBySlug(eq(article.getSlug()))).thenReturn(Optional.of(article));
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void should_create_comment_success() {
    DateTime now = new DateTime();
    CommentData commentData =
        new CommentData(
            "commentId",
            "comment body",
            article.getId(),
            now,
            now,
            new ProfileData(
                user.getId(), user.getUsername(), user.getBio(), user.getImage(), false));
    when(commentQueryService.findById(any(), eq(user))).thenReturn(Optional.of(commentData));

    String body =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "mutation { addComment(slug: \"%s\", body: \"comment body\") { comment { id body createdAt } } }",
                article.getSlug()),
            "data.addComment.comment.body");

    assertThat(body).isEqualTo("comment body");
    verify(commentRepository).save(any(Comment.class));
  }

  @Test
  public void should_remove_comment_success() {
    Comment comment = new Comment("comment body", user.getId(), article.getId());
    when(commentRepository.findById(eq(article.getId()), eq(comment.getId())))
        .thenReturn(Optional.of(comment));

    Boolean success =
        dgsQueryExecutor.executeAndExtractJsonPath(
            String.format(
                "mutation { deleteComment(slug: \"%s\", id: \"%s\") { success } }",
                article.getSlug(), comment.getId()),
            "data.deleteComment.success");

    assertThat(success).isTrue();
    verify(commentRepository).remove(eq(comment));
  }

  @Test
  public void should_get_error_if_not_allowed_to_remove_comment() {
    User anotherUser = new User("other@test.com", "other", "123", "", "");
    Article anotherArticle =
        new Article("other title", "desc", "body", Arrays.asList("java"), anotherUser.getId());
    Comment comment = new Comment("comment body", anotherUser.getId(), anotherArticle.getId());
    when(articleRepository.findBySlug(eq(anotherArticle.getSlug())))
        .thenReturn(Optional.of(anotherArticle));
    when(commentRepository.findById(eq(anotherArticle.getId()), eq(comment.getId())))
        .thenReturn(Optional.of(comment));

    ExecutionResult result =
        dgsQueryExecutor.execute(
            String.format(
                "mutation { deleteComment(slug: \"%s\", id: \"%s\") { success } }",
                anotherArticle.getSlug(), comment.getId()));

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).contains("NoAuthorizationException");
  }

  @Test
  public void should_get_error_if_comment_not_found() {
    when(commentRepository.findById(eq(article.getId()), eq("not-exist")))
        .thenReturn(Optional.empty());

    ExecutionResult result =
        dgsQueryExecutor.execute(
            String.format(
                "mutation { deleteComment(slug: \"%s\", id: \"not-exist\") { success } }",
                article.getSlug()));

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getMessage()).contains("ResourceNotFoundException");
  }

  @Test
  public void should_get_authentication_error_if_no_current_user() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(null, null, Collections.emptyList()));

    ExecutionResult createResult =
        dgsQueryExecutor.execute(
            String.format(
                "mutation { addComment(slug: \"%s\", body: \"body\") { comment { id } } }",
                article.getSlug()));
    assertThat(createResult.getErrors()).hasSize(1);
    assertThat(createResult.getErrors().get(0).getMessage()).contains("AuthenticationException");

    ExecutionResult removeResult =
        dgsQueryExecutor.execute(
            String.format(
                "mutation { deleteComment(slug: \"%s\", id: \"id\") { success } }",
                article.getSlug()));
    assertThat(removeResult.getErrors()).hasSize(1);
    assertThat(removeResult.getErrors().get(0).getMessage()).contains("AuthenticationException");
  }
}
