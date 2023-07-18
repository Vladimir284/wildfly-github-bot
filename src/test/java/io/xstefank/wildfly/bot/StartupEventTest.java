package io.xstefank.wildfly.bot;

import io.quarkiverse.githubapp.GitHubClientProvider;
import io.quarkiverse.githubapp.testing.GitHubAppMockito;
import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkiverse.githubapp.testing.dsl.GitHubMockSetup;
import io.quarkiverse.githubapp.testing.dsl.GitHubMockSetupContext;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.InMemoryLogHandler;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestExtension;
import io.quarkus.test.junit.mockito.InjectMock;
import io.xstefank.wildfly.bot.model.RuntimeConstants;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logmanager.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAuthenticatedAppInstallation;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;

import java.io.IOException;
import java.util.List;
import java.util.logging.LogManager;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@GitHubAppTest
public class StartupEventTest {

    @Inject
    Event<StartupEvent> startupEvent;

    @InjectMock
    GitHubClientProvider clientProvider;

    @Inject
    MockMailbox mailbox;

    private static final java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("io.xstefank.wildfly.bot");
    private static final InMemoryLogHandler inMemoryLogHandler = new InMemoryLogHandler(
            record -> record.getLevel().intValue() >= Level.ALL.intValue());

    static {
        rootLogger.addHandler(inMemoryLogHandler);
    }

    @RegisterExtension
    static QuarkusTestExtension TEST = new QuarkusTestExtension();


    private class CustomGithubMockSetup implements GitHubMockSetup {

        private final String configFile;

        private CustomGithubMockSetup(String configFile) {
            this.configFile = configFile;
        }

        @Override
        public void setup(GitHubMockSetupContext mocks) throws Throwable {
            GitHub mockGitHub = mock(GitHub.class);
            GHApp mockGHApp = mock(GHApp.class);
            PagedIterable<GHAppInstallation> mockGHAppInstallations = GitHubAppMockito.mockPagedIterable(mocks.ghObject(GHAppInstallation.class, 0L));
            GHAppInstallation mockGHAppInstallation = mock(GHAppInstallation.class);
            GHAuthenticatedAppInstallation mockGHAuthenticatedAppInstallation = mock(GHAuthenticatedAppInstallation.class);
            PagedSearchIterable<GHRepository> mockGHRepositories = mock(PagedSearchIterable.class);
            PagedIterator<GHRepository> mockIterator = mock(PagedIterator.class);
            mocks.configFile(RuntimeConstants.CONFIG_FILE_NAME).fromString(configFile);

            GHRepository repo = mocks.repository("xstefank/wildfly");

            when(clientProvider.getApplicationClient()).thenReturn(mockGitHub);
            when(mockGitHub.getApp()).thenReturn(mockGHApp);
            when(mockGHApp.listInstallations()).thenReturn(mockGHAppInstallations);
            when(mockGHAppInstallation.getId()).thenReturn(0L);
            when(clientProvider.getInstallationClient(anyLong())).thenReturn(mockGitHub);
            when(mockGitHub.getInstallation()).thenReturn(mockGHAuthenticatedAppInstallation);
            when(mockGHAuthenticatedAppInstallation.listRepositories()).thenReturn(mockGHRepositories);

            when(mockGHRepositories._iterator(anyInt())).thenReturn(mockIterator);
            when(mockIterator.next()).thenReturn(repo);
            when(mockIterator.hasNext()).thenReturn(true).thenReturn(false);

            startupEvent.fire(new StartupEvent());
        }
    }

    @BeforeEach
    void init() {
        mailbox.clear();
    }

    @Test
    public void testMissingRuleId() throws IOException {
        given().github(new CustomGithubMockSetup("""
                        wildfly:
                          rules:
                            - title: "Test"
                              notify: [7125767235,0979986727]
                          emails:
                            - foo@bar.baz
                        """))
                .when().payloadFromClasspath("/pr-opened.json")
                .event(GHEvent.STAR)
                .then().github(mocks -> Assertions.assertTrue(inMemoryLogHandler.getRecords().stream().anyMatch(logRecord -> logRecord.getMessage().equals("The configuration file from the repository %s was not parsed successfully due to following problems: %s"))));
    }

    @Test
    public void testSendEmailsOnInvalidRule() throws IOException {
        given().github(new CustomGithubMockSetup("""
                        wildfly:
                          rules:
                            - title: "Test"
                              notify: [7125767235,0979986727]
                          emails:
                            - foo@bar.baz
                        """))
                .when().payloadFromClasspath("/pr-opened.json")
                .event(GHEvent.STAR)
                .then().github(mocks -> {
                    GHRepository repository = mocks.repository("xstefank/wildfly");

                    List<Mail> sent = mailbox.getMailsSentTo("foo@bar.baz");
                    Assertions.assertEquals(sent.size(), 1);
                    Assertions.assertEquals(sent.get(0).getSubject(), "Unsuccessful installation of Wildfly Bot Application");
                    Assertions.assertEquals(sent.get(0).getText(), String.format("""
                                                        Hello,\n
                                                        The configuration file %s has some invalid rules in the following github repository: %s . The following problems were detected. [Rule [id=null title=Test body=null titleBody=null directories=[] notify=[7125767235, 0979986727]] is missing an id]\n
                                                        This is generated message, please do not respond.""", RuntimeConstants.CONFIG_FILE_NAME, repository.getHttpTransportUrl()));
                });
    }

    @Test
    public void testWithOneValidRule() throws IOException {
        given().github(new CustomGithubMockSetup("""
                        wildfly:
                          rules:
                            - id: Test
                              title: "Test"
                              notify: [7125767235,0979986727]
                          emails:
                            - foo@bar.baz
                        """))
                .when().payloadFromClasspath("/pr-opened.json")
                .event(GHEvent.STAR)
                .then().github(mocks -> Assertions.assertTrue(inMemoryLogHandler.getRecords().stream().anyMatch(logRecord -> logRecord.getMessage().equals("The configuration file from the repository %s was parsed successfully."))));
    }

    @Test
    public void testSendEmailsOnMultipleInvalidRules() throws IOException {
        given().github(new CustomGithubMockSetup("""
                        wildfly:
                          rules:
                            - id: Test
                              title: "Test"
                            - id: Test
                              body: "Test"
                          emails:
                            - foo@bar.baz
                        """))
                .when().payloadFromClasspath("/pr-opened.json")
                .event(GHEvent.STAR)
                .then().github(mocks -> {
                    GHRepository repository = mocks.repository("xstefank/wildfly");

                    Assertions.assertTrue(inMemoryLogHandler.getRecords().stream().anyMatch(logRecord -> logRecord.getMessage().equals("The configuration file from the repository %s was not parsed successfully due to following problems: %s")));

                    List<Mail> sent = mailbox.getMailsSentTo("foo@bar.baz");
                    Assertions.assertEquals(sent.size(), 1);
                    Assertions.assertEquals(sent.get(0).getSubject(), "Unsuccessful installation of Wildfly Bot Application");
                    Assertions.assertEquals(sent.get(0).getText(), String.format("""
                                                        Hello,\n
                                                        The configuration file %s has some invalid rules in the following github repository: %s . The following problems were detected. [Rule [id=Test title=Test body=null titleBody=null directories=[] notify=[]] and [id=Test title=null body=Test titleBody=null directories=[] notify=[]] have the same id]\n
                                                        This is generated message, please do not respond.""", RuntimeConstants.CONFIG_FILE_NAME, repository.getHttpTransportUrl()));
                });
    }

    @Test
    public void testWithMultipleValidRules() throws IOException {
        given().github(new CustomGithubMockSetup("""
                        wildfly:
                          rules:
                            - id: Test
                              title: "Test"
                            - id: Test-2
                              body: "Test"
                          emails:
                            - foo@bar.baz
                        """))
                .when().payloadFromClasspath("/pr-opened.json")
                .event(GHEvent.STAR)
                .then().github(mocks -> Assertions.assertTrue(inMemoryLogHandler.getRecords().stream().anyMatch(logRecord -> logRecord.getMessage().equals("The configuration file from the repository %s was parsed successfully."))));
    }
}