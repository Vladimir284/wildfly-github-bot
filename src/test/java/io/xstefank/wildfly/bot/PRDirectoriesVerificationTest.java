package io.xstefank.wildfly.bot;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import io.xstefank.wildfly.bot.model.RuntimeConstants;
import io.xstefank.wildfly.bot.utils.GitHubJson;
import io.xstefank.wildfly.bot.utils.MockedContext;
import io.xstefank.wildfly.bot.utils.TestConstants;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import org.mockito.Mockito;

import java.io.IOException;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@GitHubAppTest
public class PRDirectoriesVerificationTest {

    private static GitHubJson gitHubJson;
    private MockedContext mockedContext;

    @BeforeAll
    public static void setupTests() throws IOException {
        gitHubJson = GitHubJson.builder(TestConstants.VALID_PR_TEMPLATE_JSON)
                .build();
    }

    @Test
    public void existingDirectoryTest() throws IOException {
        mockedContext = MockedContext.builder(gitHubJson.id())
                .prFiles(".github/wildfly-bot.yml")
                .repoDirectories("src");
        given().github(mocks -> {
            mockedContext.mock(mocks);
            GHRepository repo = mocks.repository(TestConstants.TEST_REPO);
            GHContent mockGHContent = mock(GHContent.class);
            when(repo.getFileContent(".github/" + RuntimeConstants.CONFIG_FILE_NAME, gitHubJson.commitSHA()))
                    .thenReturn(mockGHContent);
            when(mockGHContent.read()).thenReturn(IOUtils.toInputStream("""
                    wildfly:
                      rules:
                        - id: "id"
                          directories: [src]""",
                    "UTF-8"));

        })
                .when().payloadFromString(gitHubJson.jsonString())
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    GHRepository repo = mocks.repository(TestConstants.TEST_REPO);
                    Mockito.verify(repo).getDirectoryContent("src");
                    Mockito.verify(repo).createCommitStatus(gitHubJson.commitSHA(),
                            GHCommitState.SUCCESS, "", "Valid", "Configuration File");
                });
    }

    @Test
    public void nonExistingDirectoryTest() throws IOException {
        mockedContext = MockedContext.builder(gitHubJson.id())
                .prFiles(".github/wildfly-bot.yml");
        given().github(mocks -> {
            mockedContext.mock(mocks);
            GHRepository repo = mocks.repository(TestConstants.TEST_REPO);
            GHContent mockGHContent = mock(GHContent.class);
            when(repo.getFileContent(".github/" + RuntimeConstants.CONFIG_FILE_NAME, gitHubJson.commitSHA()))
                    .thenReturn(mockGHContent);
            when(mockGHContent.read()).thenReturn(IOUtils.toInputStream("""
                    wildfly:
                      rules:
                        - id: "id"
                          directories: [src]""",
                    "UTF-8"));

        })
                .when().payloadFromString(gitHubJson.jsonString())
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    GHRepository repo = mocks.repository(TestConstants.TEST_REPO);
                    Mockito.verify(repo).getDirectoryContent("src");
                    Mockito.verify(repo).createCommitStatus(gitHubJson.commitSHA(),
                            GHCommitState.ERROR, "", "Rule is missing an id or multiple rules have the same id.",
                            "Configuration File");
                });
    }

    @Test
    public void existingFileTest() throws IOException {
        mockedContext = MockedContext.builder(gitHubJson.id())
                .prFiles(".github/wildfly-bot.yml")
                .repoFiles("pom.xml");
        given().github(mocks -> {
            mockedContext.mock(mocks);
            GHRepository repo = mocks.repository(TestConstants.TEST_REPO);
            GHContent mockGHContent = mock(GHContent.class);
            when(repo.getFileContent(".github/" + RuntimeConstants.CONFIG_FILE_NAME, gitHubJson.commitSHA()))
                    .thenReturn(mockGHContent);
            when(mockGHContent.read()).thenReturn(IOUtils.toInputStream("""
                    wildfly:
                      rules:
                        - id: "id"
                          directories: [pom.xml]""",
                    "UTF-8"));

        })
                .when().payloadFromString(gitHubJson.jsonString())
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    GHRepository repo = mocks.repository(TestConstants.TEST_REPO);
                    Mockito.verify(repo).getDirectoryContent("pom.xml");
                });
    }

    @Test
    public void oneExistingSubdirectoryTest() throws IOException {
        mockedContext = MockedContext.builder(gitHubJson.id())
                .prFiles(".github/wildfly-bot.yml")
                .repoDirectories("src", "src/main", "src/main/java");
        given().github(mocks -> {
            mockedContext.mock(mocks);
            GHRepository repo = mocks.repository(TestConstants.TEST_REPO);
            GHContent mockGHContent = mock(GHContent.class);
            when(repo.getFileContent(".github/" + RuntimeConstants.CONFIG_FILE_NAME, gitHubJson.commitSHA()))
                    .thenReturn(mockGHContent);
            when(mockGHContent.read()).thenReturn(IOUtils.toInputStream("""
                    wildfly:
                      rules:
                        - id: "id"
                          directories: [src/main, src/test]""",
                    "UTF-8"));

        })
                .when().payloadFromString(gitHubJson.jsonString())
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    GHRepository repo = mocks.repository(TestConstants.TEST_REPO);
                    Mockito.verify(repo).getDirectoryContent("src/main");
                    Mockito.verify(repo).getDirectoryContent("src/test");
                    Mockito.verify(repo).createCommitStatus(gitHubJson.commitSHA(),
                            GHCommitState.ERROR, "", "Rule is missing an id or multiple rules have the same id.",
                            "Configuration File");
                });
    }

    @Test
    public void oneExistingFileInSubdirectoryTest() throws IOException {
        mockedContext = MockedContext.builder(gitHubJson.id())
                .prFiles(".github/wildfly-bot.yml")
                .repoDirectories("src", "src/main", "src/main/java")
                .repoFiles("src/main/resources/application.properties");
        given().github(mocks -> {
            mockedContext.mock(mocks);
            GHRepository repo = mocks.repository(TestConstants.TEST_REPO);
            GHContent mockGHContent = mock(GHContent.class);
            when(repo.getFileContent(".github/" + RuntimeConstants.CONFIG_FILE_NAME, gitHubJson.commitSHA()))
                    .thenReturn(mockGHContent);
            when(mockGHContent.read()).thenReturn(IOUtils.toInputStream("""
                    wildfly:
                      rules:
                        - id: "id"
                          directories: [src/main]
                        - id: "id2"
                          directories: [src/main/resources/application.properties] """,
                    "UTF-8"));

        })
                .when().payloadFromString(gitHubJson.jsonString())
                .event(GHEvent.PULL_REQUEST)
                .then().github(mocks -> {
                    GHRepository repo = mocks.repository("xstefank/wildfly");
                    Mockito.verify(repo).getDirectoryContent("src/main");
                    Mockito.verify(repo).getDirectoryContent("src/main/resources/application.properties");
                    Mockito.verify(repo).createCommitStatus(gitHubJson.commitSHA(),
                            GHCommitState.SUCCESS, "", "Valid", "Configuration File");
                });
    }
}
